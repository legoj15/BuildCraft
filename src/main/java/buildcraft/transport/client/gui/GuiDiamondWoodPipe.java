/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.button.BCButton;
import buildcraft.lib.gui.button.ButtonSprite;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;

public class GuiDiamondWoodPipe extends GuiBC8<ContainerDiamondWoodPipe> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/pipe_emerald.png");
    private static final int SIZE_X = 175, SIZE_Y = 161;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_ROUND_ROBIN_INDEX = new GuiIcon(TEXTURE, 176, 0, 20, 20);
    private static final GuiIcon ICON_ROUND_ROBIN_NONE = new GuiIcon(TEXTURE, 176, 20, 20, 20);

    // Filter row matches ContainerDiamondWoodPipe slot layout: 9 slots at (8+i*18, 18).
    private static final int FILTER_X = 8, FILTER_Y = 18, FILTER_W = 9 * 18 - 2, FILTER_H = 16;
    // Mode buttons row — three 18×18 buttons at (7, 41) packed shoulder-to-shoulder.
    private static final int MODE_BUTTON = 18;
    private static final int MODE_X = 7, MODE_Y = 41, MODE_W = MODE_BUTTON * 3, MODE_H = MODE_BUTTON;

    public GuiDiamondWoodPipe(ContainerDiamondWoodPipe menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        if (menu.behaviour.pipe.getFlow() instanceof buildcraft.api.transport.pipe.IFlowItems) {
            if (menu.behaviour.filterMode == FilterMode.ROUND_ROBIN) {
                GuiIcon icon = menu.behaviour.filterValid ? ICON_ROUND_ROBIN_INDEX : ICON_ROUND_ROBIN_NONE;
                int xOffset = menu.behaviour.filterValid ? 18 * menu.behaviour.currentFilter : 0;
                icon.drawAt(mainGui.rootElement.getX() + 6 + xOffset, mainGui.rootElement.getY() + 16);
            }
        }
    }

    @Override
    protected void initGuiElements() {
        // A radio group: the pipe's current mode shows "latched" (pressed in).
        addModeButton(0, FilterMode.WHITE_LIST, ButtonSprite.WHITELIST, "tip.PipeItemsEmerald.whitelist");
        addModeButton(1, FilterMode.BLACK_LIST, ButtonSprite.BLACKLIST, "tip.PipeItemsEmerald.blacklist");
        if (menu.behaviour.pipe.getFlow() instanceof buildcraft.api.transport.pipe.IFlowItems) {
            // Round robin is item-only, as in 1.12.2 (the fluid pipe never implemented it).
            addModeButton(2, FilterMode.ROUND_ROBIN, ButtonSprite.ROUND_ROBIN, "tip.PipeItemsEmerald.roundrobin");
        }

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(FILTER_X, FILTER_Y, FILTER_W, FILTER_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.diamond_wood_pipe.filter.title", 0xFF_88_CC_FF,
                        "buildcraft.help.diamond_wood_pipe.filter.desc1",
                        "buildcraft.help.diamond_wood_pipe.filter.desc2")));
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(MODE_X, MODE_Y, MODE_W, MODE_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.diamond_wood_pipe.mode.title", 0xFF_FF_CC_88,
                        "buildcraft.help.diamond_wood_pipe.mode.desc")));
    }

    private void addModeButton(int column, FilterMode mode, ButtonSprite icon, String tooltipKey) {
        addRenderableWidget(BCButton.builder(leftPos + MODE_X + column * MODE_BUTTON, topPos + MODE_Y,
                MODE_BUTTON, MODE_BUTTON)
            .icon(icon)
            .latched(() -> menu.behaviour.filterMode == mode)
            .tooltip(Component.translatable(tooltipKey))
            .onPress(() -> setFilterMode(mode))
            .build());
    }

    private void setFilterMode(FilterMode mode) {
        menu.behaviour.filterMode = mode;
        menu.sendNewFilterMode(mode);
    }
}
