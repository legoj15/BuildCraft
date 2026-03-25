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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.pos.GuiRectangle;

public class GuiDistiller extends GuiBC8<ContainerDistiller> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftfactory:textures/gui/distiller.png");
    private static final int SIZE_X = 176, SIZE_Y = 166;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    // Tank positions (matching the 1.12.2 GUI layout)
    // Input tank: x=44, y=23, 16x38
    private static final int TANK_IN_X = 44, TANK_IN_Y = 23;
    private static final int TANK_IN_W = 16, TANK_IN_H = 38;

    // Gas output tank: x=98, y=10, 34x17
    private static final int TANK_GAS_X = 98, TANK_GAS_Y = 10;
    private static final int TANK_GAS_W = 34, TANK_GAS_H = 17;

    // Liquid output tank: x=98, y=54, 34x17
    private static final int TANK_LIQ_X = 98, TANK_LIQ_Y = 54;
    private static final int TANK_LIQ_W = 34, TANK_LIQ_H = 17;

    public GuiDistiller(ContainerDistiller menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new GuiElementFluidTank(
                mainGui,
                new GuiRectangle(TANK_IN_X, TANK_IN_Y, TANK_IN_W, TANK_IN_H).offset(mainGui.rootElement),
                menu.tile.getTankIn(),
                menu.widgetTankIn,
                null
            ));
            mainGui.shownElements.add(new GuiElementFluidTank(
                mainGui,
                new GuiRectangle(TANK_GAS_X, TANK_GAS_Y, TANK_GAS_W, TANK_GAS_H).offset(mainGui.rootElement),
                menu.tile.getTankGasOut(),
                menu.widgetTankGasOut,
                null
            ));
            mainGui.shownElements.add(new GuiElementFluidTank(
                mainGui,
                new GuiRectangle(TANK_LIQ_X, TANK_LIQ_Y, TANK_LIQ_W, TANK_LIQ_H).offset(mainGui.rootElement),
                menu.tile.getTankLiquidOut(),
                menu.widgetTankLiquidOut,
                null
            ));
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String titleStr = title.getString();
        int titleWidth = font.width(titleStr);
        int titleX = (imageWidth - titleWidth) / 2;
        graphics.text(font, titleStr, titleX, 6, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0xFF404040, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        renderTankTooltip(graphics, mouseX, mouseY, menu.tile != null ? menu.tile.getTankIn() : null,
                TANK_IN_X, TANK_IN_Y, TANK_IN_W, TANK_IN_H);
        renderTankTooltip(graphics, mouseX, mouseY, menu.tile != null ? menu.tile.getTankGasOut() : null,
                TANK_GAS_X, TANK_GAS_Y, TANK_GAS_W, TANK_GAS_H);
        renderTankTooltip(graphics, mouseX, mouseY, menu.tile != null ? menu.tile.getTankLiquidOut() : null,
                TANK_LIQ_X, TANK_LIQ_Y, TANK_LIQ_W, TANK_LIQ_H);
    }

    private void renderTankTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            FluidTank tank, int relX, int relY, int w, int h) {
        if (tank == null) return;
        int absX = leftPos + relX;
        int absY = topPos + relY;
        if (mouseX >= absX && mouseX < absX + w && mouseY >= absY && mouseY < absY + h) {
            FluidStack fluid = tank.getFluid();
            int amount = tank.getFluidAmount();
            int capacity = tank.getCapacity();

            List<Component> lines = new ArrayList<>();
            if (!fluid.isEmpty() && amount > 0) {
                lines.add(fluid.getHoverName());
            }
            lines.add(Component.literal(amount + " / " + capacity + " mB")
                    .withStyle(ChatFormatting.GRAY));

            // MC 26.1: Tooltip APIs changed. Stubbing tooltip for now.
            // TODO: Implement proper tank tooltip with new MC 26.1 API.
        }
    }
}
