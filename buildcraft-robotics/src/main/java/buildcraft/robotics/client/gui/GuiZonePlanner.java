/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;

public class GuiZonePlanner extends GuiBC8<ContainerZonePlanner> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/zone_planner.png");
    private static final int SIZE_X = 256, SIZE_Y = 228;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    public GuiZonePlanner(ContainerZonePlanner menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void initGuiElements() {
        // Map viewport rendering is deferred — requires porting ZonePlannerMapRenderer
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // No labels — matches 1.12.2 which only has the GUI texture
    }
}
