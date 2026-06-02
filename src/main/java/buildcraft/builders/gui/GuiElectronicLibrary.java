/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.gui;

import java.util.List;

import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
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

    // Visible list panel in the texture, used only for the help-ledger hover-highlight. The
    // texture panel extends 1 px further left than the text inset and continues well below the
    // 12th-row baseline (the area beyond LIST_MAX_ROWS rows is empty space reserved for future
    // scrolling). Matching the panel rather than the text-render bounds means the help
    // highlight covers the whole dark rectangle the player sees.
    private static final int LIST_HELP_X = LIST_X - 1;
    private static final int LIST_HELP_W = LIST_W + 1;
    private static final int LIST_HELP_H = 108;

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
                        "buildcraft.help.library.list.desc2")));

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
        updateDeleteButtonActive();
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
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos, topPos,
                0f, 0f,
                imageWidth, imageHeight,
                256, 256);

        // Download arrow (← top row): reveal the filled ← sprite from RIGHT to LEFT.
        int progressDown = menu.getSyncedProgressDown();
        if (progressDown > 0) {
            int w = Math.min(ARROW_W, Math.max(1, (int) Math.ceil(ARROW_W * (progressDown / 50.0f))));
            // Source region starts at (FILLED_DOWN_U + ARROW_W - w, FILLED_DOWN_V) — the right w
            // pixels of the ← sprite. Draw at the matching right edge of the static arrow slot.
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    leftPos + ARROW_DOWN_X + ARROW_W - w, topPos + ARROW_DOWN_Y,
                    (float) (FILLED_DOWN_U + ARROW_W - w), (float) FILLED_DOWN_V,
                    w, ARROW_H,
                    256, 256);
        }
        // Upload arrow (→ bottom row): reveal the filled → sprite from LEFT to RIGHT.
        int progressUp = menu.getSyncedProgressUp();
        if (progressUp > 0) {
            int w = Math.min(ARROW_W, Math.max(1, (int) Math.ceil(ARROW_W * (progressUp / 50.0f))));
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    leftPos + ARROW_UP_X, topPos + ARROW_UP_Y,
                    (float) FILLED_UP_U, (float) FILLED_UP_V,
                    w, ARROW_H,
                    256, 256);
        }
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        GlobalSavedDataSnapshots snapshots = GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT);
        List<Snapshot.Key> list = snapshots.getList();
        Snapshot.Key selected = menu.tile != null ? menu.tile.selected : null;

        // drawForegroundLayer now runs in GUI-local pose (origin = GUI top-left), so use GUI-local
        // coords here (no leftPos/topPos) — consistent with the centered title below.
        int rowY = LIST_Y;
        for (int i = 0; i < list.size() && i < LIST_MAX_ROWS; i++) {
            Snapshot.Key key = list.get(i);
            boolean isSelected = key.equals(selected);
            if (isSelected) {
                graphics.fill(LIST_X, rowY,
                        LIST_X + LIST_W, rowY + LIST_ROW_H, 0x80_55_55_55);
            }
            int colour = isSelected ? 0xFF_FF_FA_A0 : 0xFF_E0_E0_E0;
            String text = key.header == null ? key.toString() : key.header.name;
            graphics.text(font, text, LIST_X, rowY, colour, false);
            rowY += LIST_ROW_H;
        }

        // Centered title — drawn even under a full-override popup (which sorts on top at a higher
        // stratum via drawMenuOverlayLayer), consistent with the snapshot list above that already
        // draws unconditionally.
        String titleStr = Component.translatable("tile.buildcraftunofficial.library.name").getString();
        graphics.text(font, titleStr, (imageWidth - font.width(titleStr)) / 2, 6, 0xFF404040, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Snapshot list row selection.
        GlobalSavedDataSnapshots snapshots = GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT);
        List<Snapshot.Key> list = snapshots.getList();
        int rowY = topPos + LIST_Y;
        for (int i = 0; i < list.size() && i < LIST_MAX_ROWS; i++) {
            if (mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W
                    && mouseY >= rowY && mouseY < rowY + LIST_ROW_H) {
                Snapshot.Key key = list.get(i);
                menu.sendSelectedToServer(key);
                // Optimistic client-side update for immediate visual feedback
                if (menu.tile != null) {
                    menu.tile.selected = key;
                }
                updateDeleteButtonActive();
                return true;
            }
            rowY += LIST_ROW_H;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
