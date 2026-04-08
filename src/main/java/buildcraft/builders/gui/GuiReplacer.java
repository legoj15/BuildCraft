/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.builders.container.ContainerReplacer;

public class GuiReplacer extends AbstractContainerScreen<ContainerReplacer> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/replacer.png");
    private static final int SIZE_X = 176, SIZE_Y = 241;

    public GuiReplacer(ContainerReplacer container, Inventory playerInv, Component title) {
        super(container, playerInv, title, SIZE_X, SIZE_Y);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos, topPos,
                0f, 0f,
                imageWidth, imageHeight,
                256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
    }
}
