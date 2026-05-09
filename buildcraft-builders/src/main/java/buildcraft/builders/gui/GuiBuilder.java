/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.builders.container.ContainerBuilder;

public class GuiBuilder extends AbstractContainerScreen<ContainerBuilder> {
    private static final ResourceLocation TEXTURE_BASE =
            ResourceLocation.parse("buildcraftbuilders:textures/gui/builder.png");
    private static final ResourceLocation TEXTURE_BLUEPRINT =
            ResourceLocation.parse("buildcraftbuilders:textures/gui/builder_blueprint.png");
    private static final int SIZE_X = 176, SIZE_BLUEPRINT_X = 256, SIZE_Y = 222, BLUEPRINT_WIDTH = 87;

    public GuiBuilder(ContainerBuilder container, Inventory playerInv, Component title) {
        super(container, playerInv, title);
        this.imageWidth = SIZE_X;
        this.imageHeight = SIZE_Y;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Draw main GUI background
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_BASE,
                leftPos, topPos,
                0f, 0f,
                SIZE_X, SIZE_Y,
                256, 256);
        // TODO: Draw blueprint panel when a Blueprint snapshot is loaded
        // guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_BLUEPRINT,
        //         leftPos + SIZE_BLUEPRINT_X - BLUEPRINT_WIDTH, topPos,
        //         SIZE_BLUEPRINT_X - BLUEPRINT_WIDTH, 0f,
        //         BLUEPRINT_WIDTH, SIZE_Y,
        //         256, 256);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
