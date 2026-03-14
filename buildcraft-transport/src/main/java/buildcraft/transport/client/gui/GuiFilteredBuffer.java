/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.transport.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.transport.container.ContainerFilteredBuffer;

public class GuiFilteredBuffer extends GuiBC8<ContainerFilteredBuffer> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcrafttransport:textures/gui/filtered_buffer.png");
    private static final int SIZE_X = 176, SIZE_Y = 169;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    public GuiFilteredBuffer(ContainerFilteredBuffer menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void initGuiElements() {
        // No ledgers or special elements — simple filtered buffer GUI
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title is drawn as part of the GUI texture in 1.12.2
    }
}
