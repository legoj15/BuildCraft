/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.energy.container.ContainerEngineStone;

public class ScreenEngineStone extends AbstractContainerScreen<ContainerEngineStone> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftenergy:textures/gui/steam_engine_gui.png");

    public ScreenEngineStone(ContainerEngineStone menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Draw main GUI background
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        // Draw flame animation
        if (menu.isBurning()) {
            float progress = menu.getBurnProgress();
            int flameHeight = (int) Math.ceil(progress * 14);
            // Flame source is at (176, 0) in the texture, 14x14 pixels
            guiGraphics.blit(TEXTURE,
                leftPos + 81,                          // x: flame position
                topPos + 25 + 14 - flameHeight,        // y: bottom-aligned
                176,                                   // u: source x in texture
                14 - flameHeight,                      // v: source y in texture
                14,                                    // width
                flameHeight + 2,                       // height
                256, 256                               // texture dimensions
            );
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Title centered at top
        guiGraphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, 0x404040, false);
        // "Inventory" label
        guiGraphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }
}
