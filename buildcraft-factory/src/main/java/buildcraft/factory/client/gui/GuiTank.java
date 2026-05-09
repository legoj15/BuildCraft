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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.container.ContainerTank;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.pos.GuiRectangle;

public class GuiTank extends GuiBC8<ContainerTank> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.parse("buildcraftfactory:textures/gui/tank.png");
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
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Draw "Tank" title centered at the top
        String titleStr = title.getString();
        int titleWidth = font.width(titleStr);
        int titleX = (imageWidth - titleWidth) / 2;
        graphics.drawString(font, titleStr, titleX, 6, 0xFF404040, false);

        // Draw "Inventory" label above the player inventory
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        // Draw tank tooltip when hovering over the tank area
        renderTankTooltip(graphics, mouseX, mouseY);
    }

    private void renderTankTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.tile == null) return;

        int absX = leftPos + TANK_X;
        int absY = topPos + TANK_Y;
        if (mouseX >= absX && mouseX < absX + TANK_WIDTH
                && mouseY >= absY && mouseY < absY + TANK_HEIGHT) {

            FluidStack fluid = menu.tile.tank.getFluid();
            int amount = menu.tile.tank.getFluidAmount();
            int capacity = menu.tile.tank.getCapacity();

            List<Component> lines = new ArrayList<>();
            if (!fluid.isEmpty() && amount > 0) {
                lines.add(fluid.getHoverName());
            }
            lines.add(Component.literal(amount + " / " + capacity + " mB")
                    .withStyle(ChatFormatting.GRAY));

            List<ClientTooltipComponent> tooltipLines = lines.stream()
                    .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                    .collect(Collectors.toList());

            graphics.renderTooltip(font, tooltipLines, mouseX, mouseY,
                    DefaultTooltipPositioner.INSTANCE, null);
        }
    }
}
