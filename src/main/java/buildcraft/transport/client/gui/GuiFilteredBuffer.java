/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.transport.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.transport.container.ContainerFilteredBuffer;

public class GuiFilteredBuffer extends GuiBC8<ContainerFilteredBuffer> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/filtered_buffer.png");
    private static final int SIZE_X = 176, SIZE_Y = 169;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    public GuiFilteredBuffer(ContainerFilteredBuffer menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stackFilter = menu.tile.invFilter.getStackInSlot(i);
            net.minecraft.world.item.ItemStack stackMain = menu.tile.invMain.getStackInSlot(i);

            int currentX = (int) mainGui.rootElement.getX() + 8 + i * 18;
            int currentY = (int) mainGui.rootElement.getY() + 61;

            if (stackMain.isEmpty()) {
                if (!stackFilter.isEmpty()) {
                    graphics.item(stackFilter, currentX, currentY);
                    graphics.fill(currentX, currentY, currentX + 16, currentY + 16, 0x608B8B8B);
                } else {
                    buildcraft.lib.gui.GuiIcon.drawAt(buildcraft.transport.BCTransportSprites.NOTHING_FILTERED_BUFFER_SLOT, currentX, currentY, 16);
                }
            }
        }
    }

    @Override
    protected void initGuiElements() {
        // No ledgers or special elements — simple filtered buffer GUI
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Title is drawn as part of the GUI texture in 1.12.2
    }
}
