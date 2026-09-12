/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.client.gui;

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

import buildcraft.robotics.container.ContainerProgrammingTable;
import buildcraft.silicon.gui.LedgerTablePower;
import buildcraft.silicon.tile.TileProgrammingTable;

/** Ported from 7.1.x {@code GuiProgrammingTable} against the 8.0.x-era GUI texture (256×256 atlas, 176×207 base).
 *  The 6×4 option grid is drawn by the display slots; this class hit-tests clicks and routes them as
 *  {@code handleInventoryButtonClick} (toggle: clicking the selected option sends -1, deselecting it, like 7.1.x),
 *  and highlights the current selection plus the vertical energy bar. */
public class GuiProgrammingTable extends GuiBC8<ContainerProgrammingTable> {
    private static final Identifier TEXTURE_BASE = Identifier.parse("buildcraftunofficial:textures/gui/programming_table.png");
    private static final int SIZE_X = 176, SIZE_Y = 207;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    // 7.1.x selection highlight quad, drawn from (196, 1) in the texture's icon strip.
    private static final GuiIcon ICON_SELECTED = new GuiIcon(TEXTURE_BASE, 196, 1, 16, 16);
    private static final GuiIcon ICON_PROGRESS = new GuiIcon(TEXTURE_BASE, 176, 18, 4, 70);
    private static final GuiRectangle RECT_PROGRESS = new GuiRectangle(164, 36, 4, 70);

    private static final int GRID_X = 43, GRID_Y = 36;
    private static final int INPUT_X = 8, INPUT_Y = 36;
    private static final int OUTPUT_X = 8, OUTPUT_Y = 90;

    public GuiProgrammingTable(ContainerProgrammingTable container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                    () -> menu.tile != null ? menu.tile.getOwner() : null, true));
        }
        mainGui.shownElements.add(new LedgerTablePower(mainGui, menu.tile, true));

        addHelp(INPUT_X, INPUT_Y, 16, 16, "input");
        addHelp(OUTPUT_X, OUTPUT_Y, 16, 16, "output");
        addHelp(GRID_X, GRID_Y, TileProgrammingTable.OPTION_COLS * 18 - 2,
                TileProgrammingTable.OPTION_ROWS * 18 - 2, "boards");
        mainGui.shownElements.add(new DummyHelpElement(
                RECT_PROGRESS.offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.programming_table.power.title", 0xFF_DD_AA_FF,
                        "buildcraft.help.programming_table.power.desc")));
    }

    private void addHelp(int x, int y, int w, int h, String key) {
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(x, y, w, h).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.programming_table." + key + ".title", 0xFF_FF_CC_88,
                        "buildcraft.help.programming_table." + key + ".desc")));
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
        if (menu.tile.optionId >= 0) {
            ICON_SELECTED.drawAt(getOptionArea(menu.tile.optionId));
        }
    }

    private IGuiArea getOptionArea(int index) {
        int posX = index % TileProgrammingTable.OPTION_COLS;
        int posY = index / TileProgrammingTable.OPTION_COLS;
        return new GuiRectangle(16, 16).offset(mainGui.rootElement)
                .offset(GRID_X + posX * 18, GRID_Y + posY * 18);
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        String title = I18n.get("block.buildcraftunofficial.programming_table");
        graphics.text(font, title, (imageWidth - font.width(title)) / 2, 15, 0xFF404040, false);
    }

    //? if >=1.21.10 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            int clicked = hitTestOption(mouseX, mouseY);
            if (clicked >= 0 && minecraft != null && minecraft.gameMode != null) {
                // Toggle: clicking the selected option deselects it (7.1.x behaviour).
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        clicked == menu.tile.optionId ? -1 : clicked);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseXd, double mouseYd, int button) {
        if (button == 0) {
            int mouseX = (int) mouseXd;
            int mouseY = (int) mouseYd;
            int clicked = hitTestOption(mouseX, mouseY);
            if (clicked >= 0 && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        clicked == menu.tile.optionId ? -1 : clicked);
                return true;
            }
        }
        return super.mouseClicked(mouseXd, mouseYd, button);
    }*/
    //?}

    private int hitTestOption(int mouseX, int mouseY) {
        int count = menu.tile.options == null ? 0 : Math.min(menu.tile.options.size(),
                TileProgrammingTable.OPTION_COLS * TileProgrammingTable.OPTION_ROWS);
        for (int i = 0; i < count; i++) {
            if (getOptionArea(i).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }
}
