/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.compat.jei;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.silicon.container.AssemblyTableLayout;

/** Pins the Assembly Table's shared GUI geometry, the way {@code TableLayoutTest} does for the two robot tables.
 *  The menu, the GUI, the JEI category and the JEI click area all read {@link AssemblyTableLayout}; this guards the
 *  helper maths those readers lean on, and that the JEI category's texture crop still frames every element it draws
 *  (the crop is the one number the category owns, so a layout change that pushes a slot out of it would otherwise
 *  only show up as a clipped screen). The menu's real slot positions are pinned by the
 *  {@code robotics_table_menus_keep_slot_layout} game test. */
public class AssemblyTableLayoutTest {

    @Test
    public void bothGridsRunInReadingOrder() {
        int cols = AssemblyTableLayout.GRID_COLS, last = AssemblyTableLayout.GRID_CELLS - 1;
        Assertions.assertEquals(AssemblyTableLayout.INPUT_X, AssemblyTableLayout.inputX(0));
        Assertions.assertEquals(AssemblyTableLayout.INPUT_Y, AssemblyTableLayout.inputY(0));
        for (int i = 1; i <= last; i++) {
            if (i % cols == 0) {
                Assertions.assertEquals(AssemblyTableLayout.INPUT_X, AssemblyTableLayout.inputX(i),
                        "input slot " + i + " starts a new row at the left edge");
                Assertions.assertTrue(AssemblyTableLayout.inputY(i) >= AssemblyTableLayout.inputY(i - 1)
                        + AssemblyTableLayout.CELL_SIZE, "below the previous row, without overlapping it");
            } else {
                Assertions.assertEquals(AssemblyTableLayout.inputY(i - 1), AssemblyTableLayout.inputY(i),
                        "input slot " + i + " stays on its row");
                Assertions.assertTrue(AssemblyTableLayout.inputX(i) >= AssemblyTableLayout.inputX(i - 1)
                        + AssemblyTableLayout.CELL_SIZE, "right of its neighbour, without overlapping it");
            }
        }

        // The display cells are the same grid, moved to the right of the energy bar.
        for (int i = 0; i <= last; i++) {
            Assertions.assertEquals(AssemblyTableLayout.inputX(i) - AssemblyTableLayout.INPUT_X,
                    AssemblyTableLayout.displayX(i) - AssemblyTableLayout.DISPLAY_X,
                    "display cell " + i + " sits in the same column as input slot " + i);
            Assertions.assertEquals(AssemblyTableLayout.inputY(i) - AssemblyTableLayout.INPUT_Y,
                    AssemblyTableLayout.displayY(i) - AssemblyTableLayout.DISPLAY_Y, "and on the same row");
        }

        // The extent the crop check below relies on ends exactly at the last cell's far edge.
        Assertions.assertEquals(AssemblyTableLayout.inputX(last) + AssemblyTableLayout.CELL_SIZE,
                AssemblyTableLayout.INPUT_X + AssemblyTableLayout.gridWidth());
        Assertions.assertEquals(AssemblyTableLayout.inputY(last) + AssemblyTableLayout.CELL_SIZE,
                AssemblyTableLayout.INPUT_Y + AssemblyTableLayout.gridHeight());
    }

    @Test
    public void theEnergyBarSitsBetweenTheGrids() {
        Assertions.assertTrue(AssemblyTableLayout.INPUT_X + AssemblyTableLayout.gridWidth() < AssemblyTableLayout.BAR_X,
                "the bar starts right of the input grid");
        Assertions.assertTrue(AssemblyTableLayout.BAR_X + AssemblyTableLayout.BAR_W < AssemblyTableLayout.DISPLAY_X,
                "and ends left of the recipe cells, so a click on either is unambiguous");
    }

    @Test
    public void categoryCropFramesWhatItDraws() {
        int left = AssemblyTableCategory.TEX_U, top = AssemblyTableCategory.TEX_V;
        int right = left + AssemblyTableCategory.TEX_W, bottom = top + AssemblyTableCategory.TEX_H;
        assertInside("input grid", AssemblyTableLayout.INPUT_X, AssemblyTableLayout.INPUT_Y,
                AssemblyTableLayout.gridWidth(), AssemblyTableLayout.gridHeight(), left, top, right, bottom);
        assertInside("energy bar", AssemblyTableLayout.BAR_X, AssemblyTableLayout.BAR_Y,
                AssemblyTableLayout.BAR_W, AssemblyTableLayout.BAR_H, left, top, right, bottom);
    }

    private static void assertInside(String what, int x, int y, int w, int h, int left, int top, int right,
            int bottom) {
        Assertions.assertTrue(x >= left && y >= top && x + w <= right && y + h <= bottom,
                what + " (" + x + ", " + y + ", " + w + "x" + h + ") must lie inside the category's texture crop ("
                        + left + ", " + top + ")-(" + right + ", " + bottom + ")");
    }
}
