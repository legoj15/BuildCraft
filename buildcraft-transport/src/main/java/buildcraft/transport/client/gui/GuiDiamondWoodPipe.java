/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;

public class GuiDiamondWoodPipe extends GuiBC8<ContainerDiamondWoodPipe> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/pipe_emerald.png");
    private static final Identifier TEXTURE_BUTTON =
            Identifier.parse("buildcraftunofficial:textures/gui/pipe_emerald_button.png");
    private static final int SIZE_X = 175, SIZE_Y = 161;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_ROUND_ROBIN_INDEX = new GuiIcon(TEXTURE, 176, 0, 20, 20);
    private static final GuiIcon ICON_ROUND_ROBIN_NONE = new GuiIcon(TEXTURE, 176, 20, 20, 20);

    private FilterButton whiteListButton;
    private FilterButton blackListButton;
    private FilterButton roundRobinButton;

    public GuiDiamondWoodPipe(ContainerDiamondWoodPipe menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
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
        int bx = leftPos + 7;
        int by = topPos + 41;

        whiteListButton = new FilterButton(bx, by, FilterMode.WHITE_LIST, 19, 19, "tip.PipeItemsEmerald.whitelist");
        blackListButton = new FilterButton(bx + 18, by, FilterMode.BLACK_LIST, 37, 19, "tip.PipeItemsEmerald.blacklist");

        addRenderableWidget(whiteListButton);
        addRenderableWidget(blackListButton);

        if (menu.behaviour.pipe.getFlow() instanceof buildcraft.api.transport.pipe.IFlowItems) {
            roundRobinButton = new FilterButton(bx + 36, by, FilterMode.ROUND_ROBIN, 55, 19, "tip.PipeItemsEmerald.roundrobin");
            addRenderableWidget(roundRobinButton);
        }
    }

    private void setFilterMode(FilterMode mode) {
        menu.behaviour.filterMode = mode;
        menu.sendNewFilterMode(mode);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    private class FilterButton extends AbstractButton {
        private final FilterMode mode;
        private final int iconU, iconV;

        public FilterButton(int x, int y, FilterMode mode, int u, int v, String tooltipKey) {
            super(x, y, 18, 18, Component.empty());
            this.mode = mode;
            this.iconU = u;
            this.iconV = v;
            this.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }

        @Override
        public void onPress(InputWithModifiers modifiers) {
            setFilterMode(mode);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int state;
            boolean selected = menu.behaviour.filterMode == mode;
            if (!this.active) {
                state = 0;
            } else if (isHovered()) {
                state = selected ? 4 : 2;
            } else {
                state = selected ? 3 : 1;
            }

            int baseU = state * 18;
            int baseV = 0;

            GuiIcon bgIcon = new GuiIcon(TEXTURE_BUTTON, baseU, baseV, width, height);
            bgIcon.drawAt(getX(), getY());
            
            GuiIcon fgIcon = new GuiIcon(TEXTURE_BUTTON, iconU, iconV, 16, 16);
            fgIcon.drawAt(getX() + 1, getY() + 1);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
