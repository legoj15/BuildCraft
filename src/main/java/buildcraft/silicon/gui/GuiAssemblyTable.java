/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.gui;

import java.util.ArrayList;

import com.mojang.blaze3d.platform.InputConstants;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;

import buildcraft.silicon.EnumAssemblyRecipeState;
import buildcraft.silicon.container.AssemblyTableLayout;
import buildcraft.silicon.container.ContainerAssemblyTable;

public class GuiAssemblyTable extends GuiBC8<ContainerAssemblyTable> {
    private static final Identifier TEXTURE_BASE = Identifier.parse("buildcraftunofficial:textures/gui/assembly_table.png");
    // All geometry (slots, recipe cells, state markers, energy bar) is AssemblyTableLayout's, shared with the menu
    // and the JEI category.
    private static final int SIZE_X = AssemblyTableLayout.SIZE_X, SIZE_Y = AssemblyTableLayout.SIZE_Y;
    private static final int CELL = AssemblyTableLayout.CELL_SIZE;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_SAVED = new GuiIcon(TEXTURE_BASE, AssemblyTableLayout.STATE_U,
            AssemblyTableLayout.SAVED_V, CELL, CELL);
    private static final GuiIcon ICON_SAVED_ENOUGH = new GuiIcon(TEXTURE_BASE, AssemblyTableLayout.STATE_U,
            AssemblyTableLayout.SAVED_ENOUGH_V, CELL, CELL);
    private static final GuiIcon ICON_SAVED_ENOUGH_ACTIVE = new GuiIcon(TEXTURE_BASE, AssemblyTableLayout.STATE_U,
            AssemblyTableLayout.SAVED_ENOUGH_ACTIVE_V, CELL, CELL);
    private static final GuiIcon ICON_PROGRESS = new GuiIcon(TEXTURE_BASE, AssemblyTableLayout.BAR_FILL_U,
            AssemblyTableLayout.BAR_FILL_V, AssemblyTableLayout.BAR_W, AssemblyTableLayout.BAR_H);
    private static final GuiRectangle RECT_PROGRESS = new GuiRectangle(AssemblyTableLayout.BAR_X,
            AssemblyTableLayout.BAR_Y, AssemblyTableLayout.BAR_W, AssemblyTableLayout.BAR_H);

    public GuiAssemblyTable(ContainerAssemblyTable container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        // Right-side ledger order (matches 1.12.2): ownership on top, then power.
        if (menu.tile != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                    () -> menu.tile != null ? menu.tile.getOwner() : null, true));
        }
        mainGui.shownElements.add(new LedgerTablePower(mainGui, menu.tile, true));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(AssemblyTableLayout.INPUT_X, AssemblyTableLayout.INPUT_Y,
                        AssemblyTableLayout.gridWidth(), AssemblyTableLayout.gridHeight()).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.assembly_table.input.title", 0xFF_FF_CC_88,
                        "buildcraft.help.assembly_table.input.desc")));
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(AssemblyTableLayout.DISPLAY_X, AssemblyTableLayout.DISPLAY_Y,
                        AssemblyTableLayout.gridWidth(), AssemblyTableLayout.gridHeight()).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.assembly_table.recipes.title", 0xFF_88_CC_88,
                        "buildcraft.help.assembly_table.recipes.desc1",
                        "buildcraft.help.assembly_table.recipes.desc2")));
        mainGui.shownElements.add(new DummyHelpElement(
                RECT_PROGRESS.offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.assembly_table.power.title", 0xFF_DD_AA_FF,
                        "buildcraft.help.assembly_table.power.desc")));
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        long target = menu.tile.getTarget();
        if (target != 0) {
            double v = (double) menu.tile.power / target;
            ICON_PROGRESS.drawCutInside(
                new GuiRectangle(
                    RECT_PROGRESS.x,
                    (int) (RECT_PROGRESS.y + RECT_PROGRESS.height * Math.max(1 - v, 0)),
                    RECT_PROGRESS.width,
                    (int) Math.ceil(RECT_PROGRESS.height * Math.min(v, 1))
                ).offset(mainGui.rootElement)
            );
        }
        for (int i = 0; i < menu.tile.recipesStates.size(); i++) {
            EnumAssemblyRecipeState state = new ArrayList<>(menu.tile.recipesStates.values()).get(i);
            IGuiArea area = getRecipeArea(i);
            if (state == EnumAssemblyRecipeState.SAVED) {
                ICON_SAVED.drawAt(area);
            }
            if (state == EnumAssemblyRecipeState.PAUSED) {
                ICON_SAVED.drawAt(area);
            }
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH) {
                ICON_SAVED_ENOUGH.drawAt(area);
            }
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
                ICON_SAVED_ENOUGH_ACTIVE.drawAt(area);
            }
        }
    }

    private IGuiArea getRecipeArea(int index) {
        return new GuiRectangle(CELL, CELL).offset(mainGui.rootElement)
            .offset(AssemblyTableLayout.displayX(index), AssemblyTableLayout.displayY(index));
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        String title = I18n.get("block.buildcraftunofficial.assembly_table");
        graphics.text(font, title, (imageWidth - font.width(title)) / 2, 15, 0xFF404040, false);
    }

    //? if >=1.21.10 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            for (int i = 0; i < menu.tile.recipesStates.size(); i++) {
                IGuiArea area = getRecipeArea(i);
                if (area.contains(mouseX, mouseY)) {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseXd, double mouseYd, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            int mouseX = (int) mouseXd;
            int mouseY = (int) mouseYd;
            for (int i = 0; i < menu.tile.recipesStates.size(); i++) {
                IGuiArea area = getRecipeArea(i);
                if (area.contains(mouseX, mouseY)) {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseXd, mouseYd, button);
    }*/
    //?}
}
