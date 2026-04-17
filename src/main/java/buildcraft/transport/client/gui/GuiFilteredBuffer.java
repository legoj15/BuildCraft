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
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;
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
                    graphics.fill(currentX, currentY, currentX + 16, currentY + 16, 0xB28B8B8B); // 70% opacity grey veil
                } else {
                    buildcraft.lib.gui.GuiIcon.drawAt(buildcraft.transport.BCTransportSprites.NOTHING_FILTERED_BUFFER_SLOT, currentX, currentY, 16);
                }
            }
        }
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new buildcraft.lib.gui.ledger.LedgerOwnership(mainGui,
                () -> menu.tile != null ? menu.tile.getOwner() : null,
                true
            ));
        }
        mainGui.shownElements.add(new buildcraft.lib.gui.ledger.LedgerHelp(mainGui, false));

        // Filter Slots Help Element (y=27)
        mainGui.shownElements.add(new DummyHelpElement(
            new GuiRectangle(8, 27, 162, 18).offset(mainGui.rootElement),
            new ElementHelpInfo("buildcraft.help.filtered_buffer.filter.title", 0xFF_55_55_FF,
                "buildcraft.help.filtered_buffer.filter")
        ));

        // Buffer Slots Help Element (y=61)
        mainGui.shownElements.add(new DummyHelpElement(
            new GuiRectangle(8, 61, 162, 18).offset(mainGui.rootElement),
            new ElementHelpInfo("buildcraft.help.filtered_buffer.main.title", 0xFF_55_FF_55,
                "buildcraft.help.filtered_buffer.main")
        ));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String titleStr = title.getString();
        int titleWidth = font.width(titleStr);
        int titleX = (imageWidth - titleWidth) / 2;
        graphics.text(font, titleStr, titleX, 10, 0xFF404040, false);
    }
}
