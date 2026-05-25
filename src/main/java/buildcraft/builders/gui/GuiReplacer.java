/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.gui.pos.GuiRectangle;

import buildcraft.builders.client.render.BlueprintRenderer;
import buildcraft.builders.container.ContainerReplacer;
import buildcraft.builders.item.ItemSchematicSingle;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.ClientSnapshots;
import buildcraft.builders.snapshot.Snapshot;

/**
 * Replacer GUI.
 *
 * <p>Layout (GUI-local coordinates, 176×241 background):
 * <pre>
 *   y=9   : 3D preview viewport (8, 9) 160×100
 *   y=115 : snapshot slot (8, 115) 16×16
 *   y=117 : name EditBox (30, 117) 138×12 — editable, client-side until Replace is clicked
 *   y=137 : "from" schematic slot (8, 137) 16×16
 *   y=137 : "to" schematic slot (56, 137) 16×16
 *   y=135 : Replace button (80, 135) 60×20 — to the right of the "to" slot
 *   y=155 : "N × A → B" summary label
 *   y=159 : player inventory
 * </pre>
 *
 * <p>Design notes the user cares about:
 * <ul>
 *   <li>The 3D preview renders the <b>hypothetical post-replace</b> blueprint when all three
 *       slots are filled, so the player sees what they're about to create. When any schematic
 *       slot is empty the unmodified blueprint is rendered instead.</li>
 *   <li>The EditBox is seeded from the current blueprint's name. Typed changes are
 *       <b>client-side only</b> — the server never sees them until the Replace button is
 *       clicked, at which point the current text is shipped in the NET_REPLACE payload.</li>
 *   <li>Ledgers: Help on the left, Owner on the right. Owner requires
 *       {@code BlockReplacer.setPlacedBy} to be wired up (it now is).</li>
 * </ul>
 */
public class GuiReplacer extends GuiBC8<ContainerReplacer> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/replacer.png");
    private static final int SIZE_X = 176, SIZE_Y = 241;

    // Preview viewport (matches 1.12.2 layout)
    private static final int PREVIEW_X = 8, PREVIEW_Y = 9;
    private static final int PREVIEW_W = 160, PREVIEW_H = 100;

    // EditBox
    private static final int NAME_X = 30, NAME_Y = 117;
    private static final int NAME_W = 138, NAME_H = 12;

    // Replace button — to the right of the "to" schematic slot (which ends at x=72).
    private static final int REPLACE_X = 80, REPLACE_Y = 135;
    private static final int REPLACE_W = 60, REPLACE_H = 20;

    // Summary label y-coord (drawn just below the schematic slots)
    private static final int SUMMARY_X = 8, SUMMARY_Y = 156;

    private EditBox nameField;
    private Button replaceButton;

    /**
     * Last blueprint key we seeded the name field from. When the player swaps in a different
     * blueprint we reset the field — unless they're currently typing in it, in which case we
     * respect their in-progress edit.
     */
    private Snapshot.Key lastSeededKey;

    public GuiReplacer(ContainerReplacer container, Inventory playerInv, Component title) {
        super(container, playerInv, title, SIZE_X, SIZE_Y);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                () -> menu.tile != null ? menu.tile.getOwner() : null,
                true // right side
            ));
        }
        // Help-ledger entries — non-drawing IGuiElements that register an ElementHelpInfo at a
        // screen rect. The auto-attached LedgerHelp discovers them by iterating gui.shownElements
        // and calling addHelpElements. Coordinates mirror the constants at the top of this class.
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.replacer.preview.title", 0xFF_88_CC_FF,
                        "buildcraft.help.replacer.preview.desc1",
                        "buildcraft.help.replacer.preview.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(8, 115, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.replacer.snapshot.title", 0xFF_88_CC_88,
                        "buildcraft.help.replacer.snapshot.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(NAME_X, NAME_Y, NAME_W, NAME_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.replacer.name.title", 0xFF_E1_C9_2F,
                        "buildcraft.help.replacer.name.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(8, 137, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.replacer.from.title", 0xFF_FF_88_88,
                        "buildcraft.help.replacer.from.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(56, 137, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.replacer.to.title", 0xFF_88_FF_88,
                        "buildcraft.help.replacer.to.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(REPLACE_X, REPLACE_Y, REPLACE_W, REPLACE_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.replacer.replace.title", 0xFF_CC_AA_88,
                        "buildcraft.help.replacer.replace.desc1",
                        "buildcraft.help.replacer.replace.desc2")));
    }

    @Override
    protected void init() {
        super.init();

        nameField = new EditBox(this.font, leftPos + NAME_X, topPos + NAME_Y, NAME_W, NAME_H,
                Component.empty());
        nameField.setMaxLength(64);
        nameField.setValue(menu.getBlueprintName());
        nameField.setFocused(false);
        lastSeededKey = currentBlueprintKey();
        addRenderableWidget(nameField);

        replaceButton = Button.builder(
                    Component.translatable("gui.buildcraftunofficial.replacer.replace"),
                    b -> onReplacePressed())
                .bounds(leftPos + REPLACE_X, topPos + REPLACE_Y, REPLACE_W, REPLACE_H)
                .build();
        addRenderableWidget(replaceButton);
        updateReplaceButtonActive();
    }

    private void onReplacePressed() {
        final String newName = nameField.getValue().trim();
        menu.sendMessage(ContainerReplacer.NET_REPLACE, buf -> buf.writeUtf(newName));
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        // If the blueprint in slot 0 has changed since last tick, reset the name field to the
        // new blueprint's name — but only if the player isn't currently typing, so we don't
        // stomp an in-progress rename.
        Snapshot.Key currentKey = currentBlueprintKey();
        boolean keyChanged = !java.util.Objects.equals(currentKey, lastSeededKey);
        if (keyChanged && nameField != null && !nameField.isFocused()) {
            nameField.setValue(menu.getBlueprintName());
            lastSeededKey = currentKey;
        } else if (keyChanged && nameField != null) {
            // User is mid-edit; don't clobber, but remember the new key so we don't re-seed
            // on every tick once they unfocus.
            lastSeededKey = currentKey;
        }

        updateReplaceButtonActive();
    }

    /**
     * Enable Replace iff all three slots are filled AND the blueprint is resolvable on the
     * client side AND both schematic stacks have readable NBT. A 0-match case is a harmless
     * server no-op so we intentionally don't check counts here — walking the palette every
     * frame just to maybe-grey-out the button isn't worth the cost.
     */
    private void updateReplaceButtonActive() {
        if (replaceButton == null) return;
        replaceButton.active = canReplace();
    }

    private boolean canReplace() {
        ItemStack snap = menu.getSlot(0).getItem();
        ItemStack from = menu.getSlot(1).getItem();
        ItemStack to = menu.getSlot(2).getItem();
        if (snap.isEmpty() || from.isEmpty() || to.isEmpty()) {
            return false;
        }
        Snapshot.Header header = ItemSnapshot.getHeader(snap);
        if (header == null) {
            return false;
        }
        // Don't require ClientSnapshots to have resolved yet — the request fires on first
        // access and the user's click will still succeed server-side regardless. Just verify
        // the NBT of both schematics parses.
        return ItemSchematicSingle.getSchematicSafe(from) != null
            && ItemSchematicSingle.getSchematicSafe(to) != null;
    }

    private Snapshot.Key currentBlueprintKey() {
        if (menu.slots.isEmpty()) return null;
        ItemStack snap = menu.getSlot(0).getItem();
        if (snap.isEmpty() || !(snap.getItem() instanceof ItemSnapshot)) return null;
        Snapshot.Header h = ItemSnapshot.getHeader(snap);
        return h == null ? null : h.key;
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        // Base texture
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos, topPos,
                0f, 0f,
                imageWidth, imageHeight,
                256, 256);

        // Resolve the slot-0 blueprint — if ClientSnapshots doesn't have it yet, this kicks off
        // an async request and returns null; the preview stays empty until the response lands.
        Blueprint blueprint = resolveCurrentBlueprint();
        if (blueprint == null) {
            return;
        }

        // If we have both from/to schematics, render a client-side hypothetical blueprint with
        // the proposed palette swap applied. The server re-runs the authoritative match when
        // Replace is clicked; this is purely a UX affordance.
        Blueprint toRender = maybeApplyPendingReplacement(blueprint);
        BlueprintRenderer.renderSnapshot(graphics, toRender,
                leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_W, PREVIEW_H);
    }

    /** @return the Blueprint referenced by slot 0, or null if not yet client-resolved. */
    private Blueprint resolveCurrentBlueprint() {
        ItemStack snap = menu.getSlot(0).getItem();
        if (snap.isEmpty() || !(snap.getItem() instanceof ItemSnapshot)) return null;
        Snapshot.Header header = ItemSnapshot.getHeader(snap);
        if (header == null) return null;
        Snapshot s = ClientSnapshots.INSTANCE.getSnapshot(header.key);
        return s instanceof Blueprint bp ? bp : null;
    }

    /**
     * If the player has loaded both schematic slots, produce a copy of {@code blueprint} with
     * the proposed palette swap applied. Otherwise return {@code blueprint} unchanged.
     */
    private Blueprint maybeApplyPendingReplacement(Blueprint blueprint) {
        ItemStack fromStack = menu.getSlot(1).getItem();
        ItemStack toStack = menu.getSlot(2).getItem();
        if (fromStack.isEmpty() || toStack.isEmpty()) {
            return blueprint;
        }
        ISchematicBlock from = ItemSchematicSingle.getSchematicSafe(fromStack);
        ISchematicBlock to = ItemSchematicSingle.getSchematicSafe(toStack);
        if (from == null || to == null) {
            return blueprint;
        }
        Blueprint preview = blueprint.copy();
        preview.replace(from, to);
        return preview;
    }

    @Override
    protected void drawForegroundLayer() {
        // Summary label: "N × <from> → <to>" — walks the palette+data on every frame, which is
        // cheap because blueprints are typically small. Skips cleanly when any piece is missing.
        String summary = buildSummaryText();
        if (summary != null) {
            GuiGraphicsExtractor graphics = buildcraft.lib.gui.GuiIcon.getGuiGraphics();
            if (graphics != null) {
                int color = 0xFF_40_40_40;
                graphics.text(font, summary, SUMMARY_X, SUMMARY_Y, color, false);
            }
        }
    }

    /** @return the "5 × Oak Log → Stone" text, or null if any precondition is unmet. */
    private String buildSummaryText() {
        ItemStack fromStack = menu.getSlot(1).getItem();
        ItemStack toStack = menu.getSlot(2).getItem();
        if (fromStack.isEmpty() || toStack.isEmpty()) {
            return null;
        }
        ISchematicBlock from = ItemSchematicSingle.getSchematicSafe(fromStack);
        ISchematicBlock to = ItemSchematicSingle.getSchematicSafe(toStack);
        if (from == null || to == null) {
            return null;
        }
        Blueprint blueprint = resolveCurrentBlueprint();
        if (blueprint == null) {
            return null;
        }
        int count = blueprint.countMatchingCells(from);
        String fromName = schematicDisplayName(from);
        String toName = schematicDisplayName(to);
        return Component.translatable("gui.buildcraftunofficial.replacer.summary",
                count, fromName, toName).getString();
    }

    private static String schematicDisplayName(ISchematicBlock schematic) {
        if (schematic == null) {
            return "?";
        }
        // getBlockStateForRender is a default on ISchematicBlock; SchematicBlockDefault overrides
        // it to return its captured blockState. Good enough for GUI display purposes without
        // reaching into protected fields.
        var state = schematic.getBlockStateForRender();
        if (state == null) {
            return "?";
        }
        Block block = state.getBlock();
        return block == null ? "?" : block.getName().getString();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (nameField != null && nameField.isFocused()) {
            // ENTER / NUMPAD_ENTER commits focus away from the field so the Replace button can
            // be activated by subsequent key presses. Matches GuiArchitectTable's pattern.
            if (event.key() == 257 || event.key() == 335) {
                this.setFocused(null);
                return true;
            }
            // Swallow the inventory key (usually 'e') while the field is focused, or the screen
            // would close mid-rename.
            if (this.minecraft.options.keyInventory.matches(event)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (nameField != null && nameField.isFocused()
                && !nameField.isMouseOver(event.x(), event.y())) {
            this.setFocused(null);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
    }

    /**
     * Placeholder used by {@link #schematicDisplayName} when we can't coerce a schematic into
     * a SchematicBlockDefault. The grey-out is cosmetic and mirrors how the Electronic Library
     * muters its status strings.
     */
    @SuppressWarnings("unused")
    private static String grey(String s) {
        return ChatFormatting.GRAY + s + ChatFormatting.RESET;
    }

    /**
     * Not used yet — left in so a future "strict match" checkbox can surface {@code from}'s
     * schematic diff against the palette without duplicating logic in this class.
     */
    @SuppressWarnings("unused")
    private static boolean schematicReadable(ItemStack stack) {
        try {
            return ItemSchematicSingle.getSchematic(stack) != null;
        } catch (InvalidInputDataException e) {
            return false;
        }
    }
}
