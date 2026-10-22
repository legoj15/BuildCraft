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
    public void bothGridsRunInReadingOrderAtTheSlotPitch() {
        int cols = AssemblyTableLayout.GRID_COLS, last = AssemblyTableLayout.GRID_CELLS - 1;
        Assertions.assertEquals(8, AssemblyTableLayout.inputX(0));
        Assertions.assertEquals(36, AssemblyTableLayout.inputY(0));
        Assertions.assertEquals(8 + 18, AssemblyTableLayout.inputX(1), "the next cell is one pitch to the right");
        Assertions.assertEquals(8, AssemblyTableLayout.inputX(cols), "a full row later it wraps to the left edge");
        Assertions.assertEquals(36 + 18, AssemblyTableLayout.inputY(cols), "one pitch down");
        Assertions.assertEquals(8 + (cols - 1) * 18, AssemblyTableLayout.inputX(last));
        Assertions.assertEquals(36 + (AssemblyTableLayout.GRID_ROWS - 1) * 18, AssemblyTableLayout.inputY(last));

        // The display cells are the same grid, shifted to the right of the energy bar.
        for (int i = 0; i <= last; i++) {
            Assertions.assertEquals(AssemblyTableLayout.inputX(i) - 8 + 116, AssemblyTableLayout.displayX(i),
                    "display cell " + i + " sits over the same column as input slot " + i);
            Assertions.assertEquals(AssemblyTableLayout.inputY(i), AssemblyTableLayout.displayY(i),
                    "and on the same row");
        }

        // The grid's extent excludes the gap after the last cell: 3 cells + 2 gaps, 4 cells + 3 gaps.
        Assertions.assertEquals(3 * 16 + 2 * 2, AssemblyTableLayout.gridWidth());
        Assertions.assertEquals(4 * 16 + 3 * 2, AssemblyTableLayout.gridHeight());
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
