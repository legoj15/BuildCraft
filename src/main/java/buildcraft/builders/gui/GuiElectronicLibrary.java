/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.gui;

import java.util.List;

import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.gui.components.Button;
//? if >=1.21.10 {
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.elem.GuiElementScrollbar;
import buildcraft.lib.gui.elem.ScrollWindow;
import buildcraft.lib.gui.elem.SelectionFollower;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.gui.pos.GuiRectangle;

import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;

public class GuiElectronicLibrary extends GuiBC8<ContainerElectronicLibrary> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/electronic_library.png");
    private static final int SIZE_X = 244, SIZE_Y = 220;

    // Snapshot list bounds (GUI-local coordinates). These drive row text positions and the
    // selection-highlight fill, both of which are correctly aligned at x=8 / y=22.
    private static final int LIST_X = 8;
    private static final int LIST_Y = 22;
    private static final int LIST_W = 154;
    private static final int LIST_ROW_H = 8;
    private static final int LIST_MAX_ROWS = 13;

    // Visible list panel in the texture, used for the help-ledger hover-highlight and as the mouse-wheel
    // target. The texture panel extends 1 px further left than the text inset and continues below the
    // 13th row. Matching the panel rather than the text-render bounds means the help highlight covers
    // the whole dark rectangle the player sees.
    private static final int LIST_HELP_X = LIST_X - 1;
    private static final int LIST_HELP_W = LIST_W + 1;
    private static final int LIST_HELP_H = 108;

    // Scrollbar, restored from 1.7.10's ScrollbarWidget(163, 21, 244, 0, 110): the track strip and the
    // thumb sprite have always been baked into this texture sheet at u=244 / u=250. The track sits flush
    // against the panel's right border and spans the panel's full height (y 21..130).
    private static final int SCROLL_X = 163, SCROLL_Y = 21;
    private static final int SCROLL_W = 6, SCROLL_H = 110;
    private static final GuiIcon ICON_SCROLL_TRACK = new GuiIcon(TEXTURE, 244, 0, SCROLL_W, SCROLL_H);
    private static final GuiIcon ICON_SCROLL_THUMB = new GuiIcon(TEXTURE, 250, 0, SCROLL_W, 12);

    // Slot positions — mirror ContainerElectronicLibrary's addSlot calls so the help highlight
    // matches the visible slot exactly. Top row is DOWNLOAD (out←in), bottom row is UPLOAD (in→out).
    private static final int DOWN_OUT_X = 175, DOWN_OUT_Y = 57;
    private static final int DOWN_IN_X  = 219, DOWN_IN_Y  = 57;
    private static final int UP_IN_X    = 175, UP_IN_Y    = 79;
    private static final int UP_OUT_X   = 219, UP_OUT_Y   = 79;

    // Static arrow positions (GUI-local). These mark the empty arrow graphics
    // already baked into the GUI background texture.
    //   Top row (←): DOWNLOAD from library (fills right→left, matches arrow direction)
    //   Bottom row (→): UPLOAD to library (fills left→right, matches arrow direction)
    private static final int ARROW_DOWN_X = 194, ARROW_DOWN_Y = 58;
    private static final int ARROW_UP_X   = 194, ARROW_UP_Y   = 79;
    private static final int ARROW_W = 22, ARROW_H = 16;

    // Filled arrow overlay sprites baked into the same texture sheet at the bottom-right.
    //   (234, 240): filled ← arrow — overlays the download row
    //   (234, 224): filled → arrow — overlays the upload row
    private static final int FILLED_DOWN_U = 234, FILLED_DOWN_V = 240; // ← sprite
    private static final int FILLED_UP_U   = 234, FILLED_UP_V   = 224; // → sprite

    // Delete button — matches 1.12.2 placement at (174, 109) in GUI-local coords.
    // Uses Minecraft's native Button widget for textured appearance.
    private static final int DEL_X = 174, DEL_Y = 109;
    private static final int DEL_W = 60,  DEL_H = 20;

    private Button deleteButton;

    /** Scroll position of the snapshot list. Lives on the screen (not on an element) so it survives the
     *  element rebuild that {@code init()} does on every window resize. */
    private final ScrollWindow scroll = new ScrollWindow(LIST_MAX_ROWS);
    /** Scrolls the list to a selection that changed from outside (GUI open, server sync) — see {@link #refreshList()}. */
    private final SelectionFollower<Snapshot.Key> selectionFollower = new SelectionFollower<>();

    public GuiElectronicLibrary(ContainerElectronicLibrary container, Inventory playerInv, Component title) {
        super(container, playerInv, title, SIZE_X, SIZE_Y);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        // Add the native-textured delete button at the 1.12.2 GUI-local position.
        deleteButton = Button.builder(Component.translatable("gui.del"), b -> onDeletePressed())
                .bounds(leftPos + DEL_X, topPos + DEL_Y, DEL_W, DEL_H)
                .build();
        addRenderableWidget(deleteButton);
        updateDeleteButtonActive();
    }

    @Override
    protected void initGuiElements() {
        // Owner ledger on the right side (skin face + player name)
        if (menu.tile != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                () -> menu.tile != null ? menu.tile.getOwner() : null,
                true
            ));
        }
        // The auto-attached LedgerHelp on the left pulls ElementHelpInfo from every element in
        // mainGui.shownElements at expand-time, so the DummyHelpElements below light up under the
        // cursor whether the ledger is open or closed.
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(LIST_HELP_X, LIST_Y, LIST_HELP_W, LIST_HELP_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.library.list.title", 0xFF_FF_FA_A0,
                        "buildcraft.help.library.list.desc1",
                        "buildcraft.help.library.list.desc2",
                        "buildcraft.help.library.list.desc3")));

        mainGui.shownElements.add(new GuiElementScrollbar(mainGui,
                new GuiRectangle(SCROLL_X, SCROLL_Y, SCROLL_W, SCROLL_H).offset(mainGui.rootElement),
                scroll, ICON_SCROLL_TRACK, ICON_SCROLL_THUMB));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(DOWN_IN_X, DOWN_IN_Y, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.library.download_in.title", 0xFF_88_CC_88,
                        "buildcraft.help.library.download_in.desc1",
                        "buildcraft.help.library.download_in.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(ARROW_DOWN_X, ARROW_DOWN_Y, ARROW_W, ARROW_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.library.download_arrow.title", 0xFF_88_CC_FF,
                        "buildcraft.help.library.download_arrow.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(DOWN_OUT_X, DOWN_OUT_Y, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.library.download_out.title", 0xFF_88_FF_88,
                        "buildcraft.help.library.download_out.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(UP_IN_X, UP_IN_Y, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.library.upload_in.title", 0xFF_FF_CC_88,
                        "buildcraft.help.library.upload_in.desc1",
                        "buildcraft.help.library.upload_in.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(ARROW_UP_X, ARROW_UP_Y, ARROW_W, ARROW_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.library.upload_arrow.title", 0xFF_88_AA_FF,
                        "buildcraft.help.library.upload_arrow.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(UP_OUT_X, UP_OUT_Y, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.library.upload_out.title", 0xFF_CC_AA_88,
                        "buildcraft.help.library.upload_out.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(DEL_X, DEL_Y, DEL_W, DEL_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.library.delete.title", 0xFF_FF_88_88,
                        "buildcraft.help.library.delete.desc")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshList();
        updateDeleteButtonActive();
    }

    /** Current client snapshot list, with the scroll window resized to match. Every reader of the list goes
     *  through here, so a list that shrank since the last tick (a delete, a file removed on disk) can never be
     *  indexed with a stale window.
     *  <p>
     *  When the selection changes by any route other than a click in the visible rows (the GUI opening on a
     *  saved selection, the server syncing a new one) the list scrolls just far enough to show it. It does
     *  not re-follow an unchanged selection, so the player can freely scroll away from it. */
    private List<Snapshot.Key> refreshList() {
        List<Snapshot.Key> list = GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT).getList();
        scroll.setTotal(list.size());
        selectionFollower.follow(scroll, list, menu.tile != null ? menu.tile.selected : null);
        return list;
    }

    /** Enable/disable the delete button based on whether a snapshot is currently selected
     *  and present in the local client library. */
    private void updateDeleteButtonActive() {
        if (deleteButton == null) return;
        Snapshot.Key selected = menu.tile != null ? menu.tile.selected : null;
        boolean canDelete = selected != null
                && GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT)
                        .getSnapshot(selected) != null;
        deleteButton.active = canDelete;
    }

    private void onDeletePressed() {
        GlobalSavedDataSnapshots clientSnapshots =
                GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT);
        Snapshot.Key selected = menu.tile != null ? menu.tile.selected : null;
        if (selected == null || clientSnapshots.getSnapshot(selected) == null) return;

        clientSnapshots.removeSnapshot(selected);
        menu.sendSelectedToServer(null);
        if (menu.tile != null) {
            menu.tile.selected = null;
        }
        updateDeleteButtonActive();
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        //? if >=1.21.10 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos, topPos,
                0f, 0f,
                imageWidth, imageHeight,
                256, 256);
        //?} else {
        /*graphics.blit(TEXTURE,
                leftPos, topPos,
                0f, 0f,
                imageWidth, imageHeight,
                256, 256);*/
        //?}

        // Download arrow (← top row): reveal the filled ← sprite from RIGHT to LEFT.
        int progressDown = menu.getSyncedProgressDown();
        if (progressDown > 0) {
            int w = Math.min(ARROW_W, Math.max(1, (int) Math.ceil(ARROW_W * (progressDown / 50.0f))));
            // Source region starts at (FILLED_DOWN_U + ARROW_W - w, FILLED_DOWN_V) — the right w
            // pixels of the ← sprite. Draw at the matching right edge of the static arrow slot.
            //? if >=1.21.10 {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    leftPos + ARROW_DOWN_X + ARROW_W - w, topPos + ARROW_DOWN_Y,
                    (float) (FILLED_DOWN_U + ARROW_W - w), (float) FILLED_DOWN_V,
                    w, ARROW_H,
                    256, 256);
            //?} else {
            /*graphics.blit(TEXTURE,
                    leftPos + ARROW_DOWN_X + ARROW_W - w, topPos + ARROW_DOWN_Y,
                    (float) (FILLED_DOWN_U + ARROW_W - w), (float) FILLED_DOWN_V,
                    w, ARROW_H,
                    256, 256);*/
            //?}
        }
        // Upload arrow (→ bottom row): reveal the filled → sprite from LEFT to RIGHT.
        int progressUp = menu.getSyncedProgressUp();
        if (progressUp > 0) {
            int w = Math.min(ARROW_W, Math.max(1, (int) Math.ceil(ARROW_W * (progressUp / 50.0f))));
            //? if >=1.21.10 {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    leftPos + ARROW_UP_X, topPos + ARROW_UP_Y,
                    (float) FILLED_UP_U, (float) FILLED_UP_V,
                    w, ARROW_H,
                    256, 256);
            //?} else {
            /*graphics.blit(TEXTURE,
                    leftPos + ARROW_UP_X, topPos + ARROW_UP_Y,
                    (float) FILLED_UP_U, (float) FILLED_UP_V,
                    w, ARROW_H,
                    256, 256);*/
            //?}
        }
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        List<Snapshot.Key> list = refreshList();
        Snapshot.Key selected = menu.tile != null ? menu.tile.selected : null;

        // drawForegroundLayer now runs in GUI-local pose (origin = GUI top-left), so use GUI-local
        // coords here (no leftPos/topPos) — consistent with the centered title below.
        int rowY = LIST_Y;
        for (int i = scroll.getFirstVisible(); i < scroll.getEndVisible(); i++) {
            Snapshot.Key key = list.get(i);
            boolean isSelected = key.equals(selected);
            if (isSelected) {
                graphics.fill(LIST_X, rowY,
                        LIST_X + LIST_W, rowY + LIST_ROW_H, 0x80_55_55_55);
            }
            int colour = isSelected ? 0xFF_FF_FA_A0 : 0xFF_E0_E0_E0;
            String text = key.header == null ? key.toString() : key.header.name;
            // Clip to the row so a long name can't run over the scrollbar (1.7.10 trimmed the same way).
            graphics.text(font, font.plainSubstrByWidth(text, LIST_W), LIST_X, rowY, colour, false);
            rowY += LIST_ROW_H;
        }

        // Centered title — drawn even under a full-override popup (which sorts on top at a higher
        // stratum via drawMenuOverlayLayer), consistent with the snapshot list above that already
        // draws unconditionally.
        String titleStr = Component.translatable("tile.buildcraftunofficial.library.name").getString();
        graphics.text(font, titleStr, (imageWidth - font.width(titleStr)) / 2, 6, 0xFF404040, false);
    }

    /** Selects the snapshot under the cursor. @return true if a list row was clicked. */
    private boolean clickList(double mouseX, double mouseY) {
        if (mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + LIST_W) {
            return false;
        }
        List<Snapshot.Key> list = refreshList();
        int index = scroll.indexAt(mouseY - (topPos + LIST_Y), LIST_ROW_H);
        if (index < 0) return false;
        Snapshot.Key key = list.get(index);
        menu.sendSelectedToServer(key);
        // Optimistic client-side update for immediate visual feedback
        if (menu.tile != null) {
            menu.tile.selected = key;
        }
        selectionFollower.markShown(key); // already on screen, nothing to follow
        updateDeleteButtonActive();
        return true;
    }

    // Same signature on every node, so no directive: the wheel over the list panel or the scrollbar scrolls the list.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= LIST_HELP_X && x < SCROLL_X + SCROLL_W && y >= SCROLL_Y && y < SCROLL_Y + SCROLL_H) {
            refreshList();
            scroll.scrollBy(ScrollWindow.wheelRows(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    //? if >=1.21.10 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return clickList(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickList(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }*/
    //?}
}
