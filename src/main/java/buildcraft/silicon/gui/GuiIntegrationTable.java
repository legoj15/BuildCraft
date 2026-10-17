/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.gui;

import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;

import buildcraft.silicon.container.ContainerIntegrationTable;
import buildcraft.silicon.container.IntegrationTableLayout;

public class GuiIntegrationTable extends GuiBC8<ContainerIntegrationTable> {
    private static final Identifier TEXTURE_BASE = Identifier.parse("buildcraftunofficial:textures/gui/integration_table.png");
    // All geometry is IntegrationTableLayout's, shared with the menu and the JEI category.
    private static final int SIZE_X = IntegrationTableLayout.SIZE_X, SIZE_Y = IntegrationTableLayout.SIZE_Y;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_PROGRESS = new GuiIcon(TEXTURE_BASE, IntegrationTableLayout.BAR_FILL_U,
            IntegrationTableLayout.BAR_FILL_V, IntegrationTableLayout.BAR_W, IntegrationTableLayout.BAR_H);
    private static final GuiRectangle RECT_PROGRESS = new GuiRectangle(IntegrationTableLayout.BAR_X,
            IntegrationTableLayout.BAR_Y, IntegrationTableLayout.BAR_W, IntegrationTableLayout.BAR_H);

    // Help regions — the 3×3 input ring...
    private static final int INPUT_X = IntegrationTableLayout.RING_X, INPUT_Y = IntegrationTableLayout.RING_Y,
            INPUT_W = IntegrationTableLayout.ringSpan(), INPUT_H = IntegrationTableLayout.ringSpan();
    // ...and the display + output slots, one block covering both.
    private static final int OUTPUT_X = IntegrationTableLayout.PREVIEW_X, OUTPUT_Y = IntegrationTableLayout.PREVIEW_Y,
            OUTPUT_W = IntegrationTableLayout.RESULT_X + IntegrationTableLayout.CELL_SIZE - IntegrationTableLayout.PREVIEW_X,
            OUTPUT_H = IntegrationTableLayout.RESULT_Y + IntegrationTableLayout.CELL_SIZE - IntegrationTableLayout.PREVIEW_Y;

    public GuiIntegrationTable(ContainerIntegrationTable container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        mainGui.shownElements.add(new LedgerTablePower(mainGui, menu.tile, true));

        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(INPUT_X, INPUT_Y, INPUT_W, INPUT_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.integration_table.input.title", 0xFF_FF_CC_88,
                        "buildcraft.help.integration_table.input.desc")));
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(OUTPUT_X, OUTPUT_Y, OUTPUT_W, OUTPUT_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.integration_table.output.title", 0xFF_88_CC_88,
                        "buildcraft.help.integration_table.output.desc")));
        mainGui.shownElements.add(new DummyHelpElement(
                RECT_PROGRESS.offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.integration_table.power.title", 0xFF_DD_AA_FF,
                        "buildcraft.help.integration_table.power.desc")));
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
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        String title = I18n.get("block.buildcraftunofficial.integration_table");
        graphics.text(font, title, (imageWidth - font.width(title)) / 2, 10, 0xFF404040, false);
    }
}
