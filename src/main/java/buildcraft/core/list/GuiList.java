/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.list;

import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.gui.button.BCButton;
import net.minecraft.client.gui.components.EditBox;
//? if >=1.21.10 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.core.item.ItemList_BC8;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;

public class GuiList extends GuiBC8<ContainerList> {
    private static final Identifier TEXTURE_BASE =
        Identifier.parse("buildcraftunofficial:textures/gui/list_new.png");
    private static final int SIZE_X = 176, SIZE_Y = 191;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_ONE_STACK = new GuiIcon(TEXTURE_BASE, 0, 191, 20, 20);
    /** 16×16 darker-slot overlay used to visually mark disabled slots (1-8 in one-stack mode).
     * Same UV / size as 1.12.2's GuiList.ICON_HIGHLIGHT — texture art at (176, 0). */
    private static final GuiIcon ICON_HIGHLIGHT = new GuiIcon(TEXTURE_BASE, 176, 0, 16, 16);

    // Per-line toggle buttons for the three match modes (Precise / By-Type / By-Material): vanilla-faced
    // BCButtons with the mode's first letter (P / T / M) as the label, readable at 14px.
    private static final int BTN_W = 14, BTN_H = 14;
    private static final int BUTTON_COUNT = 3;
    /** Promoted to a field so {@link #keyPressed} / {@link #mouseClicked} can ask the field
     * whether it currently has focus. Without this guard, vanilla's screen `keyPressed` would
     * see the inventory key (default `e`) press while the player is typing a list name and
     * close the GUI mid-edit. Mirrors the pattern in
     * {@code GuiArchitectTable} for the same reason. */
    private EditBox labelField;

    /** Per-line cache of the ghost-preview examples currently shown in slots 1-8. The list is
     * reshuffled when the cached signature for the line changes — i.e. on GUI open (cache starts
     * empty), on every mode toggle (signature includes byType / byMaterial), and on every
     * exemplar change (signature includes the slot-0 item identity). For lines with more than 8
     * matches this means the player sees a different random subset each time, mirroring 1.12.2's
     * behaviour. */
    private final java.util.Map<Integer, GhostCache> ghostCache = new java.util.HashMap<>();

    private static final class GhostCache {
        final long signature;
        final java.util.List<net.minecraft.world.item.ItemStack> shuffled;

        GhostCache(long signature, java.util.List<net.minecraft.world.item.ItemStack> shuffled) {
            this.signature = signature;
            this.shuffled = shuffled;
        }
    }

    public GuiList(ContainerList menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        // Match-info ledger — when expanded and the player hovers over slot 0 of a By-Type or
        // By-Material line, displays which tags / capabilities the registered handlers use to
        // match items against the exemplar.
        mainGui.shownElements.add(new LedgerListMatch(mainGui, menu));

        // Help-ledger wrappers — one DummyHelpElement per addressable region. These are non-
        // drawing IGuiElements that simply register an ElementHelpInfo at a screen rect; the
        // auto-attached LedgerHelp discovers them by iterating gui.shownElements and calling
        // addHelpElements. Highlight colours are picked to read distinctly when the help ledger
        // is expanded — yellow for the label, green for the slot rows, and three different
        // hues for the per-mode buttons.
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(10, 10, 156, 12).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.list.label.title", 0xFF_E1_C9_2F,
                        "buildcraft.help.list.label.desc")));

        for (int line = 0; line < menu.lines.length; line++) {
            int rowY = 32 + line * 34;
            mainGui.shownElements.add(new DummyHelpElement(
                    new GuiRectangle(8, rowY, 9 * 18, 16).offset(mainGui.rootElement),
                    new ElementHelpInfo("buildcraft.help.list.slots.title", 0xFF_88_CC_88,
                            "buildcraft.help.list.slots.desc1",
                            "buildcraft.help.list.slots.desc2")));

            int btnRowY = rowY + 18;
            int bOffX = 8 + 9 * 18 - BUTTON_COUNT * BTN_W - 1;
            mainGui.shownElements.add(new DummyHelpElement(
                    new GuiRectangle(bOffX, btnRowY, BTN_W, BTN_H).offset(mainGui.rootElement),
                    new ElementHelpInfo("buildcraft.help.list.button.precise.title", 0xFF_88_AA_FF,
                            "buildcraft.help.list.button.precise.desc")));
            mainGui.shownElements.add(new DummyHelpElement(
                    new GuiRectangle(bOffX + BTN_W, btnRowY, BTN_W, BTN_H).offset(mainGui.rootElement),
                    new ElementHelpInfo("buildcraft.help.list.button.by_type.title", 0xFF_FF_BB_55,
                            "buildcraft.help.list.button.by_type.desc1",
                            "buildcraft.help.list.button.by_type.desc2")));
            mainGui.shownElements.add(new DummyHelpElement(
                    new GuiRectangle(bOffX + 2 * BTN_W, btnRowY, BTN_W, BTN_H).offset(mainGui.rootElement),
                    new ElementHelpInfo("buildcraft.help.list.button.by_material.title", 0xFF_CC_88_FF,
                            "buildcraft.help.list.button.by_material.desc1",
                            "buildcraft.help.list.button.by_material.desc2")));
        }

        // Label text field — bordered, with setResponder for automatic sync
        labelField = new EditBox(this.font, leftPos + 10, topPos + 10, 156, 12, Component.empty());
        labelField.setMaxLength(32);
        labelField.setBordered(true);
        // Load existing label
        if (menu.getListItemStack().getItem() instanceof ItemList_BC8 listItem) {
            String name = listItem.getLocationName(menu.getListItemStack());
            if (name != null && !name.isEmpty()) {
                labelField.setValue(name);
            }
        }
        // Don't auto-focus on open — the player has to click the field to start editing, and
        // gets the field unfocused (re-enabling inventory shortcuts) after pressing Enter or
        // clicking outside.
        labelField.setFocused(false);
        labelField.setResponder(newText -> menu.setLabel(newText));
        addRenderableWidget(labelField);

        // Toggle buttons for each line (Precise / By-Type / By-Material). A switched-on mode shows
        // "latched" — vanilla's pressed-in face with a full-bright letter, still clickable. The state
        // is read live from the line, so the mutual exclusion switchButton applies (Precise vs
        // By-Type/By-Material) shows on every button of the row without any refresh here.
        // The 1px left shift on bOffX lands the button row's right edge flush with the right-
        // most pixel of the slot grid (slot 8 ends at x=168, 3*14=42 wide button row → -1).
        for (int line = 0; line < menu.lines.length; line++) {
            int bOffX = this.leftPos + 8 + 9 * 18 - BUTTON_COUNT * BTN_W - 1;
            int bOffY = this.topPos + 32 + line * 34 + 18;

            for (int btn = 0; btn < BUTTON_COUNT; btn++) {
                final int lineIdx = line;
                final int btnIdx = btn;
                String letter = btn == 0 ? "P" : (btn == 1 ? "T" : "M");
                String tooltipKey = btn == 0 ? "gui.list.nbt" : (btn == 1 ? "gui.list.metadata" : "gui.list.oredict");

                addRenderableWidget(BCButton.builder(bOffX + btn * BTN_W, bOffY, BTN_W, BTN_H)
                        .label(Component.literal(letter))
                        .latched(() -> menu.lines[lineIdx].getOption(btnIdx))
                        .tooltip(Component.translatable(tooltipKey))
                        .onPress(() -> menu.switchButton(lineIdx, btnIdx))
                        .build());
            }
        }
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        for (int i = 0; i < menu.lines.length; i++) {
            buildcraft.lib.list.ListHandler.Line line = menu.lines[i];
            if (!line.isOneStackMode()) continue;

            // 1.12.2-style overlay around slot 0, signalling "this is the exemplar".
            ICON_ONE_STACK.drawAt(leftPos + 6, topPos + 30 + i * 34);

            // Slots 1-8 in one-stack mode are auto-populated previews of items the engine
            // would match. Match 1.12.2's exact render order: darker-slot sprite FIRST (so the
            // slot bg is visibly darkened to signal "disabled / not editable"), then the
            // example item ON TOP at full opacity (so it reads as a real item, just sitting
            // in a darker-than-normal slot). Reversing the order — item then darken — makes
            // the item itself look ghosted/translucent rather than the slot looking disabled.
            java.util.List<net.minecraft.world.item.ItemStack> examples = ghostExamplesFor(i);
            for (int slot = 1; slot < buildcraft.lib.list.ListHandler.WIDTH; slot++) {
                int x = leftPos + 8 + slot * 18;
                int y = topPos + 32 + i * 34;
                ICON_HIGHLIGHT.drawAt(x, y);
                int exampleIdx = slot - 1;
                if (exampleIdx < examples.size()) {
                    net.minecraft.world.item.ItemStack ex = examples.get(exampleIdx);
                    if (!ex.isEmpty()) {
                        graphics.fakeItem(ex, x, y); // null holder → clock/compass stay static
                    }
                }
            }
        }
    }

    @Override
    protected void drawTooltipLayer(int mouseX, int mouseY, float partialTick) {
        BCGraphics graphics = GuiIcon.getGuiGraphics();

        // Ghost-slot tooltip: when the player hovers a populated slot 1-8 of a one-stack-mode
        // line, surface the example item's vanilla tooltip (name + components). The slot itself
        // is logically empty (Container holds ItemStack.EMPTY there and ListPhantomSlot.set
        // refuses to update it) so vanilla's slot-tooltip path renders nothing — we drive the
        // tooltip from the cached ghost-example list instead.
        for (int line = 0; line < menu.lines.length; line++) {
            if (!menu.lines[line].isOneStackMode()) continue;
            java.util.List<net.minecraft.world.item.ItemStack> examples = ghostExamplesFor(line);
            for (int slot = 1; slot < buildcraft.lib.list.ListHandler.WIDTH; slot++) {
                int idx = slot - 1;
                if (idx >= examples.size()) break;
                net.minecraft.world.item.ItemStack ex = examples.get(idx);
                if (ex.isEmpty()) continue;
                int x = leftPos + 8 + slot * 18;
                int y = topPos + 32 + line * 34;
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    graphics.setTooltipForNextFrame(font, ex, mouseX, mouseY);
                    return;
                }
            }
        }
    }

    /** Returns the cached, shuffled ghost-preview list for the given line. Reshuffles when the
     * line's signature (exemplar item identity + mode flags) changes. The full {@code getExamples}
     * list is shuffled; the GUI displays the first {@code WIDTH-1 = 8} entries, so for lines with
     * many matches the player sees a different random subset on each open / toggle / exemplar
     * change. */
    private java.util.List<net.minecraft.world.item.ItemStack> ghostExamplesFor(int lineIdx) {
        buildcraft.lib.list.ListHandler.Line line = menu.lines[lineIdx];
        long sig = ghostSignature(line);
        GhostCache cached = ghostCache.get(lineIdx);
        if (cached != null && cached.signature == sig) {
            return cached.shuffled;
        }
        java.util.List<net.minecraft.world.item.ItemStack> all = new java.util.ArrayList<>(line.getExamples());
        java.util.Collections.shuffle(all);
        ghostCache.put(lineIdx, new GhostCache(sig, all));
        return all;
    }

    private static long ghostSignature(buildcraft.lib.list.ListHandler.Line line) {
        net.minecraft.world.item.ItemStack source = line.getStack(0);
        int itemHash = source.isEmpty() ? 0 : System.identityHashCode(source.getItem());
        int flags = (line.byType ? 1 : 0) | (line.byMaterial ? 2 : 0) | (line.precise ? 4 : 0);
        return ((long) itemHash << 8) | flags;
    }

    //? if >=1.21.10 {
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (labelField != null && labelField.isFocused()) {
            // Enter / numpad-Enter commits the edit and releases focus, so subsequent inventory
            // shortcut presses (default `e`) close the GUI again as expected.
            if (event.key() == 257 || event.key() == 335) {
                this.setFocused(null);
                return true;
            }
            // Swallow the inventory key so vanilla's screen-level handler doesn't close the
            // GUI mid-edit when the player types `e` (or whatever they've rebound it to).
            if (this.minecraft.options.keyInventory.matches(event)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (labelField != null && labelField.isFocused()) {
            // Enter / numpad-Enter commits the edit and releases focus.
            if (key == 257 || key == 335) {
                this.setFocused(null);
                return true;
            }
            // Swallow the inventory key (1.21.1 KeyMapping.matches(int key, int scancode)).
            if (this.minecraft.options.keyInventory.matches(key, scancode)) {
                return true;
            }
        }
        return super.keyPressed(key, scancode, modifiers);
    }*/
    //?}

    //? if >=1.21.10 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean entered) {
        // Click-outside unfocuses the field — without this, focus sticks until the player
        // presses Enter, which is non-obvious. Mirrors GuiArchitectTable.
        if (labelField != null && labelField.isFocused()
                && !labelField.isMouseOver(event.x(), event.y())) {
            this.setFocused(null);
        }
        return super.mouseClicked(event, entered);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseXd, double mouseYd, int button) {
        // Click-outside unfocuses the field. Mirrors GuiArchitectTable.
        if (labelField != null && labelField.isFocused()
                && !labelField.isMouseOver(mouseXd, mouseYd)) {
            this.setFocused(null);
        }
        return super.mouseClicked(mouseXd, mouseYd, button);
    }*/
    //?}

}
