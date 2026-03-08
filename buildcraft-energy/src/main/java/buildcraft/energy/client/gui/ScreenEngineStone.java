/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.energy.container.ContainerEngineStone;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.lib.gui.ledger.LedgerHelp;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.misc.LocaleUtil;

public class ScreenEngineStone extends GuiBC8<ContainerEngineStone> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftenergy:textures/gui/steam_engine_gui.png");
    private static final int SIZE_X = 176, SIZE_Y = 166;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    private final IGuiArea flameRect;

    public ScreenEngineStone(ContainerEngineStone menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
        this.flameRect = new GuiRectangle(81, 25, 14, 14).offset(mainGui.rootElement);
    }

    @Override
    protected void initGuiElements() {
        if (menu.engine != null) {
            // Power ledger on the right side (auto-stacked via Ledger_Neptune)
            mainGui.shownElements.add(new LedgerEngine(mainGui,
                menu::getSyncedCurrentOutput,
                menu::getSyncedPower,
                menu::getSyncedHeat,
                menu::getSyncedPowerStage,
                menu::isSyncedBurningEngine,
                true
            ));

            // Help ledger on the LEFT side (auto-stacked via Ledger_Neptune)
            mainGui.shownElements.add(new LedgerHelp(mainGui,
                "gui.buildcraft.stirling_engine.help"
            ));
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        // Draw flame animation
        if (menu.isBurning()) {
            float progress = menu.getBurnProgress();
            int flameHeight = (int) Math.ceil(progress * 14);
            graphics.blit(
                RenderPipelines.GUI_TEXTURED, TEXTURE,
                (int) flameRect.getX(),
                (int) (flameRect.getY() + flameRect.getHeight() - flameHeight),
                176f,
                (float) (14 - flameHeight),
                14,
                flameHeight + 2,
                256, 256
            );
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String str = LocaleUtil.localize("tile.engineStone.name");
        int strWidth = font.width(str);
        int titleX = (imageWidth - strWidth) / 2;
        graphics.drawString(font, str, titleX, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }
}
