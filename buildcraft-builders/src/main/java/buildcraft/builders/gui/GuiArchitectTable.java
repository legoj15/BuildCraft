/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.builders.container.ContainerArchitectTable;

public class GuiArchitectTable extends AbstractContainerScreen<ContainerArchitectTable> {
    private static final Identifier TEXTURE_BASE =
            Identifier.parse("buildcraftbuilders:textures/gui/architect.png");
    private static final int SIZE_X = 256, SIZE_Y = 166;

    private EditBox nameField;

    public GuiArchitectTable(ContainerArchitectTable container, Inventory playerInv, Component title) {
        super(container, playerInv, title, SIZE_X, SIZE_Y);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        nameField = new EditBox(this.font, leftPos + 90, topPos + 62, 156, 12,
                Component.empty());
        nameField.setValue(menu.getTileName());
        nameField.setFocused(true);
        nameField.setResponder(newText -> {
            // Send name update to server
            menu.setTileName(newText.trim());
        });
        addRenderableWidget(nameField);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        // Draw main GUI background
        GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_BASE,
                leftPos, topPos,
                0f, 0f,
                SIZE_X, SIZE_Y,
                256, 256);

        // Draw progress bar
        int total = menu.getSyncedTotal();
        if (total > 0) {
            int progress = menu.getSyncedProgress();
            int progressWidth = (int) (24.0f * progress / total);
            // The progress bar sprite is at (0, 166) in the texture, 24x17
            GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_BASE,
                    leftPos + 159, topPos + 34,
                    0f, 166f,
                    progressWidth, 17,
                    256, 256);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
    }
}
