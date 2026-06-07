/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.ChatFormatting;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.container.ContainerTank;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;

public class GuiTank extends GuiBC8<ContainerTank> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/tank.png");
    private static final int SIZE_X = 176, SIZE_Y = 181;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_TANK_OVERLAY = new GuiIcon(TEXTURE, 176, 0, 16, 64);

    // Tank area position relative to the GUI top-left
    private static final int TANK_X = 80, TANK_Y = 18;
    private static final int TANK_WIDTH = 16, TANK_HEIGHT = 64;

    public GuiTank(ContainerTank menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new GuiElementFluidTank(
                mainGui,
                new GuiRectangle(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT).offset(mainGui.rootElement),
                menu.tile.tank,
                menu.widgetTank,
                ICON_TANK_OVERLAY
            ));

            mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.tank.title.tankGeneric", 0xFF_55_AA_FF,
                    "buildcraft.help.tank.generic_block.desc",
                    "buildcraft.help.tank.generic")
            ));
        }
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        // Draw "Tank" title centered at the top
        String titleStr = title.getString();
        int titleWidth = font.width(titleStr);
        int titleX = (imageWidth - titleWidth) / 2;
        graphics.text(font, titleStr, titleX, 6, 0xFF404040, false);

        // Draw "Inventory" label above the player inventory
        graphics.text(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0xFF404040, false);
    }

    @Override
    protected void drawTooltipLayer(int mouseX, int mouseY, float partialTick) {
        // Draw tank tooltip when hovering over the tank area
        renderTankTooltip(GuiIcon.getGuiGraphics(), mouseX, mouseY);
    }

    private void renderTankTooltip(BCGraphics graphics, int mouseX, int mouseY) {
        if (menu.tile == null) return;

        int absX = leftPos + TANK_X;
        int absY = topPos + TANK_Y;
        if (mouseX >= absX && mouseX < absX + TANK_WIDTH
                && mouseY >= absY && mouseY < absY + TANK_HEIGHT) {

            int amount = menu.tile.tank.getAmountMb(0);
            int capacity = menu.tile.tank.getCapacityMb(0);

            List<Component> lines = buildcraft.lib.gui.elem.GuiElementFluidTank.buildTankTooltip(
                    menu.tile.tank.getFluidStack(0), amount, capacity);
            java.util.List<net.minecraft.util.FormattedCharSequence> comps = new java.util.ArrayList<>();
            for (net.minecraft.network.chat.Component c : lines) {
                comps.add(c.getVisualOrderText());
            }
            graphics.setTooltipForNextFrame(font, comps, mouseX, mouseY);
        }
    }
}
