/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

import buildcraft.builders.container.ContainerArchitectTable;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;

public class GuiArchitectTable extends GuiBC8<ContainerArchitectTable> {
    private static final Identifier TEXTURE_BASE =
            Identifier.parse("buildcraftunofficial:textures/gui/architect.png");
    private static final int SIZE_X = 176, SIZE_Y = 240;

    // Layout rectangles in GUI-local coordinates. Mirror the magic numbers used elsewhere in
    // this class (preview blit, progress blit, EditBox init, ContainerArchitectTable slot
    // positions) so the help-region highlights match the visible widgets exactly.
    private static final int PREVIEW_X = 8, PREVIEW_Y = 8;
    private static final int PREVIEW_W = 160, PREVIEW_H = 100;
    private static final int SNAPSHOT_IN_X = 52, SNAPSHOT_IN_Y = 125;
    private static final int PROGRESS_X = 77, PROGRESS_Y = 125;
    private static final int PROGRESS_W = 22, PROGRESS_H = 16;
    private static final int SNAPSHOT_OUT_X = 111, SNAPSHOT_OUT_Y = 125;
    private static final int NAME_X = 8, NAME_Y = 145;
    private static final int NAME_W = 160, NAME_H = 12;

    private EditBox nameField;

    public GuiArchitectTable(ContainerArchitectTable container, Inventory playerInv, Component title) {
        super(container, playerInv, title, SIZE_X, SIZE_Y);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new buildcraft.lib.gui.ledger.LedgerOwnership(mainGui,
                () -> menu.tile != null ? menu.tile.getOwner() : null,
                true
            ));
        }

        // Help-ledger entries — see GuiReplacer / GuiBuilder for the same pattern. The auto-attached
        // LedgerHelp iterates mainGui.shownElements at expand-time and pulls each element's
        // ElementHelpInfo via addHelpElements, so hovering any of the five regions below highlights
        // the matching entry in the ledger panel and vice versa.
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.architect.preview.title", 0xFF_88_CC_FF,
                        "buildcraft.help.architect.preview.desc1",
                        "buildcraft.help.architect.preview.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(SNAPSHOT_IN_X, SNAPSHOT_IN_Y, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.architect.snapshot_in.title", 0xFF_88_CC_88,
                        "buildcraft.help.architect.snapshot_in.desc1",
                        "buildcraft.help.architect.snapshot_in.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.architect.progress.title", 0xFF_FF_CC_88,
                        "buildcraft.help.architect.progress.desc")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(SNAPSHOT_OUT_X, SNAPSHOT_OUT_Y, 16, 16).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.architect.snapshot_out.title", 0xFF_88_FF_88,
                        "buildcraft.help.architect.snapshot_out.desc1",
                        "buildcraft.help.architect.snapshot_out.desc2")));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(NAME_X, NAME_Y, NAME_W, NAME_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.architect.name.title", 0xFF_E1_C9_2F,
                        "buildcraft.help.architect.name.desc")));
    }

    @Override
    protected void init() {
        super.init();
        nameField = new EditBox(this.font, leftPos + NAME_X, topPos + NAME_Y, NAME_W, NAME_H,
                Component.empty());
        nameField.setValue(menu.getTileName());
        nameField.setFocused(false);
        nameField.setResponder(newText -> {
            // Send name update to server
            String trimmed = newText.trim();
            menu.setTileName(trimmed);
            menu.sendMessage(ContainerArchitectTable.NET_SET_NAME, (buf) -> {
                buf.writeUtf(trimmed);
            });
        });
        addRenderableWidget(nameField);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.nameField.isFocused()) {
            if (event.key() == 257 || event.key() == 335) { // ENTER or NUMPAD_ENTER
                this.setFocused(null);
                return true;
            }
            if (this.minecraft.options.keyInventory.matches(event)) {
                return true; // Consume the inventory key so the screen doesn't close
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean entered) {
        if (this.nameField.isFocused() && !this.nameField.isMouseOver(event.x(), event.y())) {
            this.setFocused(null);
        }
        return super.mouseClicked(event, entered);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        // Draw main GUI background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_BASE,
                leftPos, topPos,
                0f, 0f,
                SIZE_X, SIZE_Y,
                256, 256);

        // Draw progress bar by blitting the 22×16 filled-arrow sprite at the bottom-left of
        // architect.png over the empty arrow baked into the GUI background. Partial-width blit
        // grows left-to-right as the scan progresses, matching the conventional Minecraft
        // progress arrow look.
        int total = menu.getSyncedTotal();
        if (total > 0) {
            int progress = menu.getSyncedProgress();
            int progressWidth = Math.min(22, (int) (22.0f * progress / total));
            if (progressWidth > 0) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_BASE,
                        leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                        0f, 240f,
                        progressWidth, PROGRESS_H,
                        256, 256);
            }
        }

        // Draw Blueprint Preview — prefer the output/input snapshot item if present, otherwise
        // fall back to a live preview of the current scan-area contents so the player can see
        // what will be captured before they actually build the blueprint.
        buildcraft.builders.snapshot.Snapshot snapshot = null;
        net.minecraft.world.item.ItemStack snapshotStack = menu.getSlot(1).getItem();
        if (snapshotStack.isEmpty()) {
            snapshotStack = menu.getSlot(0).getItem();
        }
        if (!snapshotStack.isEmpty() && snapshotStack.getItem() instanceof buildcraft.builders.item.ItemSnapshot) {
            buildcraft.builders.snapshot.Snapshot.Header header = buildcraft.builders.item.ItemSnapshot.getHeader(snapshotStack);
            if (header != null) {
                snapshot = buildcraft.builders.snapshot.ClientSnapshots.INSTANCE.getSnapshot(header.key);
            }
        }
        if (snapshot == null && menu.tile != null) {
            snapshot = buildcraft.builders.snapshot.ClientArchitectPreviews.INSTANCE.get(menu.tile.getBlockPos());
        }
        if (snapshot != null) {
            // Dark backdrop in architect.png lives at (8, 8)–(167, 107); align the PiP viewport
            // to that rectangle so the rotating block stays visually contained in the preview
            // area instead of drifting into the gray GUI below it.
            buildcraft.builders.client.render.BlueprintRenderer.renderSnapshot(
                graphics, snapshot,
                leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_W, PREVIEW_H
            );
        }
    }

    private int previewRefreshCounter = 0;

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.tile != null) {
            previewRefreshCounter++;
            if (previewRefreshCounter >= 40) {
                previewRefreshCounter = 0;
                // Fire a refresh without dropping the cached preview — the old one stays on
                // screen until the reply arrives (if content changed) or forever (if it didn't),
                // so block edits inside the scan area surface within ~2s without any visible
                // render-frame gap.
                buildcraft.builders.snapshot.ClientArchitectPreviews.INSTANCE
                        .requestRefresh(menu.tile.getBlockPos());
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (menu.tile != null) {
            buildcraft.builders.snapshot.ClientArchitectPreviews.INSTANCE
                    .invalidate(menu.tile.getBlockPos());
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        // Draw the custom titles if needed
        String titleStr = this.title.getString();
        graphics.text(font, titleStr, (imageWidth - font.width(titleStr)) / 2, 111, 0xFF404040, false);
    }
}
