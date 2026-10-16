/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.guide.parts.recipe;

import java.util.Arrays;
import java.util.Collections;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePartItem;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.ChangingObject;

/** One laser-table step on a guide page — inputs on the left, the table's own item over an arrow in the middle, the
 *  result on the right — for the tables whose recipes don't fit the Assembly Table's six-slot frame (the Programming
 *  and Integration Tables). Built only from sprites the crafting and smelting panels already use (the lone smelting
 *  slot, the crafting arrow and output frame), drawn the same way {@link GuideAssembly} draws, so it adds no render
 *  call that could cliff between nodes. Hovering the arrow shows the laser energy the step needs; hovering any stack
 *  shows its tooltip, and clicking one opens its page. */
public class GuideLaserTable extends GuidePartItem {
    public static final GuiIcon SLOT = new GuiIcon(GuiGuide.ICONS_2, 119, 54, 18, 18);
    public static final GuiIcon ARROW = new GuiIcon(GuiGuide.ICONS_2, 180, 19, 22, 15);
    public static final GuiIcon OUTPUT_FRAME = new GuiIcon(GuiGuide.ICONS_2, 209, 14, 26, 26);
    /** Height of the drawn panel; matches the other recipe panels so a page mixing them lines up. */
    public static final int PANEL_HEIGHT = 54;
    public static final int PIXEL_HEIGHT = 60;
    /** Input slots stack in columns of this many before a new column starts. */
    public static final int SLOTS_PER_COLUMN = 3;
    private static final int GAP = 4;

    private final ChangingItemStack[] input;
    private final ChangingItemStack table;
    private final ChangingItemStack output;
    private final ChangingObject<Long> mjCost;
    private final int hash;

    // Layout, relative to the panel's top-left — fixed per part, since it only depends on the input count.
    private final GuiRectangle[] inputPos;
    private final GuiRectangle arrowPos;
    private final GuiRectangle tablePos;
    private final GuiRectangle outputFramePos;
    private final GuiRectangle outputPos;
    private final int panelWidth;

    GuideLaserTable(GuiGuide gui, ChangingItemStack[] input, ChangingItemStack table, ChangingItemStack output,
        ChangingObject<Long> mjCost) {
        super(gui);
        this.input = input;
        this.table = table;
        this.output = output;
        this.mjCost = mjCost;
        this.hash = Arrays.deepHashCode(new Object[] { input, table, output, mjCost });

        int rows = Math.max(1, Math.min(input.length, SLOTS_PER_COLUMN));
        int columns = Math.max(1, (input.length + SLOTS_PER_COLUMN - 1) / SLOTS_PER_COLUMN);
        int top = (PANEL_HEIGHT - rows * 18) / 2;
        inputPos = new GuiRectangle[input.length];
        for (int i = 0; i < input.length; i++) {
            inputPos[i] = new GuiRectangle((i / SLOTS_PER_COLUMN) * 18, top + (i % SLOTS_PER_COLUMN) * 18, 18, 18);
        }
        int arrowX = columns * 18 + GAP;
        int arrowY = (PANEL_HEIGHT - ARROW.height) / 2;
        arrowPos = new GuiRectangle(arrowX, arrowY, ARROW.width, ARROW.height);
        tablePos = new GuiRectangle(arrowX + (ARROW.width - 16) / 2, arrowY - 18, 16, 16);
        int outX = arrowX + ARROW.width + GAP;
        outputFramePos = new GuiRectangle(outX, (PANEL_HEIGHT - OUTPUT_FRAME.height) / 2, OUTPUT_FRAME.width,
            OUTPUT_FRAME.height);
        outputPos = new GuiRectangle(outputFramePos.x + 5, outputFramePos.y + 5, 16, 16);
        panelWidth = outX + OUTPUT_FRAME.width;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        GuideLaserTable other = (GuideLaserTable) obj;
        return Arrays.equals(input, other.input) && table.equals(other.table) && output.equals(other.output)
            && mjCost.equals(other.mjCost);
    }

    private int offsetX() {
        return ((int) GuiGuide.PAGE_LEFT_TEXT.width - panelWidth) / 2;
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        if (current.pixel + PIXEL_HEIGHT > height) {
            current = current.newPage();
        }
        x += offsetX();
        y += current.pixel;
        if (current.page == index) {
            for (int i = 0; i < input.length; i++) {
                GuiRectangle rect = inputPos[i];
                SLOT.drawAt(x + rect.x, y + rect.y);
                drawItemStack(input[i].get(), x + (int) rect.x + 1, y + (int) rect.y + 1);
            }
            ARROW.drawAt(x + arrowPos.x, y + arrowPos.y);
            OUTPUT_FRAME.drawAt(x + outputFramePos.x, y + outputFramePos.y);
            drawItemStack(table.get(), x + (int) tablePos.x, y + (int) tablePos.y);
            drawItemStack(output.get(), x + (int) outputPos.x, y + (int) outputPos.y);

            if (arrowPos.offset(x, y).contains(gui.mouse)) {
                gui.tooltips.add(Collections.singletonList(LocaleUtil.localizeMj(mjCost.get())));
            }
        }
        current = current.nextLine(PIXEL_HEIGHT, height);
        return current;
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        if (current.pixel + PIXEL_HEIGHT > height) {
            current = current.newPage();
        }
        x += offsetX();
        y += current.pixel;
        if (current.page == index) {
            for (int i = 0; i < input.length; i++) {
                GuiRectangle rect = inputPos[i];
                testClickItemStack(input[i].get(), x + (int) rect.x + 1, y + (int) rect.y + 1);
            }
            testClickItemStack(table.get(), x + (int) tablePos.x, y + (int) tablePos.y);
            testClickItemStack(output.get(), x + (int) outputPos.x, y + (int) outputPos.y);
        }
        current = current.nextLine(PIXEL_HEIGHT, height);
        return current;
    }
}
