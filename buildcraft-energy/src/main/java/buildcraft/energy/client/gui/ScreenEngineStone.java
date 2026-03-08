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
import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.lib.gui.ledger.LedgerHelp;
import buildcraft.lib.gui.pos.IGuiArea;

public class ScreenEngineStone extends AbstractContainerScreen<ContainerEngineStone> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftenergy:textures/gui/steam_engine_gui.png");

    private BuildCraftGui mainGui;
    private LedgerEngine ledgerPower;
    private LedgerHelp ledgerHelp;

    public ScreenEngineStone(ContainerEngineStone menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        IGuiArea rootArea = BuildCraftGui.createWindowedArea(this);
        mainGui = new BuildCraftGui(this, rootArea);

        if (menu.engine != null) {
            // Power ledger on the right side
            ledgerPower = new LedgerEngine(mainGui, menu.engine, true);
            ledgerPower.setPosition(leftPos + imageWidth, topPos + 5);
            mainGui.shownElements.add(ledgerPower);

            // Help ledger below the power ledger
            ledgerHelp = new LedgerHelp(mainGui, true,
                "gui.buildcraft.stirling_engine.help"
            );
            ledgerHelp.setPosition(leftPos + imageWidth, topPos + 30);
            mainGui.shownElements.add(ledgerHelp);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (mainGui != null) mainGui.tick();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        // Draw flame animation
        if (menu.isBurning()) {
            float progress = menu.getBurnProgress();
            int flameHeight = (int) Math.ceil(progress * 14);
            graphics.blit(TEXTURE,
                leftPos + 81,
                topPos + 25 + 14 - flameHeight,
                176,
                14 - flameHeight,
                14,
                flameHeight + 2,
                256, 256
            );
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Draw ledgers on top of everything else
        if (ledgerPower != null) ledgerPower.drawWithGraphics(graphics);
        if (ledgerHelp != null) ledgerHelp.drawWithGraphics(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }
}
