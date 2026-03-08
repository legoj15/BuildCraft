/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.energy.container.ContainerEngineIron;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.lib.gui.ledger.LedgerHelp;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.misc.LocaleUtil;

/**
 * Screen (GUI) for the combustion engine. Displays 3 fluid tanks
 * (fuel, coolant, residue) with fill level bars.
 */
public class ScreenEngineIron extends GuiBC8<ContainerEngineIron> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftenergy:textures/gui/combustion_engine_gui.png");
    private static final int SIZE_X = 176, SIZE_Y = 177;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_TANK_OVERLAY = new GuiIcon(TEXTURE, 176, 0, 16, 60);

    public ScreenEngineIron(ContainerEngineIron menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        if (menu.engine != null) {
            // Power ledger on the right side
            mainGui.shownElements.add(new LedgerEngine(mainGui,
                menu::getSyncedCurrentOutput,
                menu::getSyncedPower,
                menu::getSyncedHeat,
                menu::getSyncedPowerStage,
                menu::isSyncedBurning,
                true
            ));

            // Help ledger on the left side
            mainGui.shownElements.add(new LedgerHelp(mainGui,
                "gui.buildcraft.combustion_engine.help"
            ));
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        // Draw fluid tank fill levels
        drawTankFill(graphics, 26, 18, menu.getSyncedFuelAmount(), TileEngineIron_BC8.MAX_FLUID);
        drawTankFill(graphics, 80, 18, menu.getSyncedCoolantAmount(), TileEngineIron_BC8.MAX_FLUID);
        drawTankFill(graphics, 134, 18, menu.getSyncedResidueAmount(), TileEngineIron_BC8.MAX_FLUID);
    }

    /**
     * Draw a simple colored bar to represent fluid fill level inside a tank slot.
     */
    private void drawTankFill(GuiGraphics graphics, int x, int y, int amount, int maxAmount) {
        if (amount <= 0 || maxAmount <= 0) return;
        int tankHeight = 60;
        int fillHeight = (int) ((float) amount / maxAmount * tankHeight);
        if (fillHeight <= 0) return;

        int drawX = (int) mainGui.rootElement.getX() + x;
        int drawY = (int) mainGui.rootElement.getY() + y + (tankHeight - fillHeight);

        // Draw a simple blue bar for now — in the future, could render actual fluid textures
        graphics.fill(drawX, drawY, drawX + 16, drawY + fillHeight, 0x80_40_80_FF);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String str = LocaleUtil.localize("tile.engineIron.name");
        int strWidth = font.width(str);
        int titleX = (imageWidth - strWidth) / 2;
        graphics.drawString(font, str, titleX, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }
}
