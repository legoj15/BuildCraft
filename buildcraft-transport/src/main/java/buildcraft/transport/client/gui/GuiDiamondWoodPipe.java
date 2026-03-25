/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;

public class GuiDiamondWoodPipe extends GuiBC8<ContainerDiamondWoodPipe> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcrafttransport:textures/gui/pipe_emerald.png");
    private static final int SIZE_X = 175, SIZE_Y = 161;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    private Button whiteListButton;
    private Button blackListButton;
    private Button roundRobinButton;

    public GuiDiamondWoodPipe(ContainerDiamondWoodPipe menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void initGuiElements() {
        // Filter mode toggle buttons below the filter slots
        int bx = leftPos + 7;
        int by = topPos + 41;
        int bw = 50;
        int bh = 14;

        whiteListButton = Button.builder(Component.literal("White"), b -> setFilterMode(FilterMode.WHITE_LIST))
                .bounds(bx, by, bw, bh).build();
        blackListButton = Button.builder(Component.literal("Black"), b -> setFilterMode(FilterMode.BLACK_LIST))
                .bounds(bx + bw + 2, by, bw, bh).build();
        roundRobinButton = Button.builder(Component.literal("Round"), b -> setFilterMode(FilterMode.ROUND_ROBIN))
                .bounds(bx + (bw + 2) * 2, by, bw, bh).build();

        addRenderableWidget(whiteListButton);
        addRenderableWidget(blackListButton);
        addRenderableWidget(roundRobinButton);
    }

    private void setFilterMode(FilterMode mode) {
        menu.behaviour.filterMode = mode;
        menu.sendNewFilterMode(mode);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Draw current filter mode indicator
        String modeStr = switch (menu.behaviour.filterMode) {
            case WHITE_LIST -> "§a[Whitelist]";
            case BLACK_LIST -> "§c[Blacklist]";
            case ROUND_ROBIN -> "§e[Round Robin]";
        };
        graphics.text(font, modeStr, 8, 58, 0x404040, false);
    }
}
