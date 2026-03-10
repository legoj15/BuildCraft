/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.lib.gui.GuiBC8;

public class GuiAutoCraftItems extends GuiBC8<ContainerAutoCraftItems> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftfactory:textures/gui/autobench_item.png");

    public GuiAutoCraftItems(ContainerAutoCraftItems menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        // 1.12.2 GUI dimensions: 176 x 237
        this.imageWidth = 176;
        this.imageHeight = 237;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void initGuiElements() {
        // TODO: Add recipe book button and progress bar
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, 256, 256);
    }
}
