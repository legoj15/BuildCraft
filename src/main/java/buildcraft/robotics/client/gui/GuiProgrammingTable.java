/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.button.BCButton;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;

import buildcraft.robotics.container.ContainerProgrammingTable;
import buildcraft.robotics.container.ProgrammingTableLayout;
import buildcraft.silicon.gui.LedgerTablePower;

/** Ported from 7.1.x {@code GuiProgrammingTable} against the 8.0.x-era GUI texture (256×256 atlas, 176×207 base).
 *  The 6×4 option grid is drawn by the display slots; this class hit-tests clicks and routes them as
 *  {@code handleInventoryButtonClick} (toggle: clicking the selected option sends -1, deselecting it, like 7.1.x),
 *  and highlights the current selection plus the vertical energy bar.
 *
 *  <p>A recipe offering more options than the grid has cells is shown a page at a time, flipped with the two
 *  buttons under the grid (shown only when there is more than one page). The page is client-side view state on the
 *  menu; the selection the server keeps counts over all the options, so a click sends the option's overall index. */
public class GuiProgrammingTable extends GuiBC8<ContainerProgrammingTable> {
    private static final Identifier TEXTURE_BASE = Identifier.parse("buildcraftunofficial:textures/gui/programming_table.png");
    // All geometry (slots, option grid, selection frame, energy bar) is ProgrammingTableLayout's, shared with the
    // menu and the JEI category.
    private static final int SIZE_X = ProgrammingTableLayout.SIZE_X, SIZE_Y = ProgrammingTableLayout.SIZE_Y;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    // 7.1.x selection highlight quad, drawn from the texture's icon strip.
    private static final GuiIcon ICON_SELECTED = new GuiIcon(TEXTURE_BASE, ProgrammingTableLayout.SELECTED_U,
            ProgrammingTableLayout.SELECTED_V, ProgrammingTableLayout.CELL_SIZE, ProgrammingTableLayout.CELL_SIZE);
    private static final GuiIcon ICON_PROGRESS = new GuiIcon(TEXTURE_BASE, ProgrammingTableLayout.BAR_FILL_U,
            ProgrammingTableLayout.BAR_FILL_V, ProgrammingTableLayout.BAR_W, ProgrammingTableLayout.BAR_H);
    private static final GuiRectangle RECT_PROGRESS = new GuiRectangle(ProgrammingTableLayout.BAR_X,
            ProgrammingTableLayout.BAR_Y, ProgrammingTableLayout.BAR_W, ProgrammingTableLayout.BAR_H);

    private BCButton prevPageButton;
    private BCButton nextPageButton;
    /** The selection the page last followed; -2 until the first tick, so a table opened on a selection past the
     *  first page shows that page. */
    private int followedSelection = -2;

    public GuiProgrammingTable(ContainerProgrammingTable container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void init() {
        super.init();
        prevPageButton = addRenderableWidget(BCButton.builder(leftPos + ProgrammingTableLayout.PAGER_PREV_X,
                topPos + ProgrammingTableLayout.PAGER_Y, ProgrammingTableLayout.PAGER_W, ProgrammingTableLayout.PAGER_H)
            .label(Component.literal("<"))
            .tooltip(Component.translatable("gui.buildcraftunofficial.programming_table.page_previous"))
            .activeWhen(() -> menu.getOptionPage() > 0)
            .onPress(() -> menu.setOptionPage(menu.getOptionPage() - 1))
            .build());
        nextPageButton = addRenderableWidget(BCButton.builder(leftPos + ProgrammingTableLayout.PAGER_NEXT_X,
                topPos + ProgrammingTableLayout.PAGER_Y, ProgrammingTableLayout.PAGER_W, ProgrammingTableLayout.PAGER_H)
            .label(Component.literal(">"))
            .tooltip(Component.translatable("gui.buildcraftunofficial.programming_table.page_next"))
            .activeWhen(() -> menu.getOptionPage() < menu.getOptionPageCount() - 1)
            .onPress(() -> menu.setOptionPage(menu.getOptionPage() + 1))
            .build());
        updatePager();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        followSelection();
        updatePager();
    }

    /** Turns to the page of the selected option whenever the selection changes by any route (the GUI opening on a
     *  saved selection, the JEI "+" transfer selecting a board, the server clearing it), and not otherwise, so the
     *  player can page away from an unchanged selection. */
    private void followSelection() {
        int selected = menu.tile == null ? -1 : menu.tile.optionId;
        if (selected == followedSelection) {
            return;
        }
        followedSelection = selected;
        if (selected >= 0) {
            menu.setOptionPage(ProgrammingTableLayout.pageOf(selected));
        }
    }

    /** The page buttons only exist for a table with more than one page of options. */
    private void updatePager() {
        boolean paged = menu.getOptionPageCount() > 1;
        prevPageButton.visible = paged;
        nextPageButton.visible = paged;
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                    () -> menu.tile != null ? menu.tile.getOwner() : null, true));
        }
        mainGui.shownElements.add(new LedgerTablePower(mainGui, menu.tile, true));

        addHelp(ProgrammingTableLayout.BOARD_X, ProgrammingTableLayout.BOARD_Y,
                ProgrammingTableLayout.CELL_SIZE, ProgrammingTableLayout.CELL_SIZE, "input");
        addHelp(ProgrammingTableLayout.RESULT_X, ProgrammingTableLayout.RESULT_Y,
                ProgrammingTableLayout.CELL_SIZE, ProgrammingTableLayout.CELL_SIZE, "output");
        addHelp(ProgrammingTableLayout.GRID_X, ProgrammingTableLayout.GRID_Y,
                ProgrammingTableLayout.gridWidth(), ProgrammingTableLayout.gridHeight(), "boards");
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
        int selected = menu.tile.optionId;
        if (selected >= 0 && ProgrammingTableLayout.pageOf(selected) == menu.getOptionPage()) {
            ICON_SELECTED.drawAt(getOptionArea(ProgrammingTableLayout.cellOf(selected)));
        }
    }

    /** The screen area of grid cell {@code cell} (0-based on the page being shown). */
    private IGuiArea getOptionArea(int cell) {
        return new GuiRectangle(ProgrammingTableLayout.CELL_SIZE, ProgrammingTableLayout.CELL_SIZE)
                .offset(mainGui.rootElement)
                .offset(ProgrammingTableLayout.cellX(cell), ProgrammingTableLayout.cellY(cell));
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        String title = I18n.get("block.buildcraftunofficial.programming_table");
        graphics.text(font, title, (imageWidth - font.width(title)) / 2, 15, 0xFF404040, false);

        int pages = menu.getOptionPageCount();
        if (pages > 1) {
            String label = I18n.get("gui.buildcraftunofficial.programming_table.page", menu.getOptionPage() + 1, pages);
            int centre = ProgrammingTableLayout.GRID_X + ProgrammingTableLayout.gridWidth() / 2;
            graphics.text(font, label, centre - font.width(label) / 2, ProgrammingTableLayout.PAGER_Y + 2,
                    0xFF404040, false);
        }
    }

    //? if >=1.21.10 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
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
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
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

    /** The option under the mouse as an index over ALL the options (what the server selects by), or -1. */
    private int hitTestOption(int mouseX, int mouseY) {
        int first = ProgrammingTableLayout.firstOptionOn(menu.getOptionPage());
        int shown = menu.tile.options == null ? 0
                : Math.min(menu.tile.options.size() - first, ProgrammingTableLayout.PAGE_SIZE);
        for (int cell = 0; cell < shown; cell++) {
            if (getOptionArea(cell).contains(mouseX, mouseY)) {
                return first + cell;
            }
        }
        return -1;
    }
}
