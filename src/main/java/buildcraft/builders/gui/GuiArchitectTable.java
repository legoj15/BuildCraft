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

public class GuiArchitectTable extends GuiBC8<ContainerArchitectTable> {
    private static final Identifier TEXTURE_BASE =
            Identifier.parse("buildcraftunofficial:textures/gui/architect.png");
    private static final int SIZE_X = 176, SIZE_Y = 240;

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

        mainGui.shownElements.add(new buildcraft.lib.gui.ledger.LedgerHelp(mainGui, false));
    }

    @Override
    protected void init() {
        super.init();
        nameField = new EditBox(this.font, leftPos + 8, topPos + 145, 160, 12,
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

        // Draw progress bar
        int total = menu.getSyncedTotal();
        if (total > 0) {
            int progress = menu.getSyncedProgress();
            int progressWidth = (int) (24.0f * progress / total);
            // Filled progress texture might not be correctly mapped in the large GUI
            // Assuming it sits roughly around the area
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_BASE,
                    leftPos + 76, topPos + 124,
                    232f, 0f, // Sample fallback mapping for filled progress in large GUI
                    progressWidth, 17,
                    256, 256);
        }

        // Draw Blueprint Preview
        net.minecraft.world.item.ItemStack snapshotStack = menu.getSlot(1).getItem();
        if (snapshotStack.isEmpty()) {
            snapshotStack = menu.getSlot(0).getItem();
        }
        if (!snapshotStack.isEmpty() && snapshotStack.getItem() instanceof buildcraft.builders.item.ItemSnapshot snapshotItem) {
            buildcraft.builders.snapshot.Snapshot.Header header = snapshotItem.getHeader(snapshotStack);
            if (header != null) {
                buildcraft.builders.snapshot.Snapshot snapshot = buildcraft.builders.snapshot.ClientSnapshots.INSTANCE.getSnapshot(header.key);
                if (snapshot != null) {
                    // Preview window is located at x=8, y=18, w=160, h=100 in the UI
                    buildcraft.builders.client.render.BlueprintRenderer.renderSnapshot(
                        graphics, snapshot,
                        leftPos + 8, topPos + 18, 160, 100
                    );
                }
            }
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
