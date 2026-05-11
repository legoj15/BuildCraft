/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.transport.container.ContainerDiamondPipe;

public class GuiDiamondPipe extends GuiBC8<ContainerDiamondPipe> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/filter.png");
    private static final Identifier TEXTURE_CB =
            Identifier.parse("buildcraftunofficial:textures/gui/filter_cb.png");
    private static final int SIZE_X = 175, SIZE_Y = 225;
    private static final GuiIcon ICON_GUI    = new GuiIcon(TEXTURE,    0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_GUI_CB = new GuiIcon(TEXTURE_CB, 0, 0, SIZE_X, SIZE_Y);

    public GuiDiamondPipe(ContainerDiamondPipe menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        GuiIcon icon = buildcraft.lib.client.ColorBlindUtil.isActive() ? ICON_GUI_CB : ICON_GUI;
        icon.drawAt(mainGui.rootElement);
    }

    @Override
    protected void initGuiElements() {
        // No ledgers or special elements — simple filter GUI
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Title drawn as part of the background texture in 1.12.2
    }
}
