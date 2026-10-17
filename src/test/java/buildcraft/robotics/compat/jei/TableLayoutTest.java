/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.robotics.container.ProgrammingTableLayout;
import buildcraft.silicon.container.IntegrationTableLayout;
import buildcraft.silicon.tile.TileProgrammingTable;

/** Pins the shared GUI geometry of the Programming and Integration Tables. The menus, the GUIs, the JEI categories
 *  and the JEI click areas all read {@link ProgrammingTableLayout} / {@link IntegrationTableLayout}; this guards the
 *  helper maths those readers lean on, and that each JEI category's texture crop still frames every element it
 *  draws (the crop is the one number a category owns, so a layout change that pushes a slot out of it would
 *  otherwise only show up as a clipped screen). The slot positions of the real menus are pinned by the
 *  {@code robotics_table_menus_keep_slot_layout} game test. */
public class TableLayoutTest {

    @Test
    public void programmingGridCellsRunInReadingOrderAtTheSlotPitch() {
        Assertions.assertEquals(43, ProgrammingTableLayout.cellX(0));
        Assertions.assertEquals(36, ProgrammingTableLayout.cellY(0));
        Assertions.assertEquals(43 + 5 * 18, ProgrammingTableLayout.cellX(5));
        Assertions.assertEquals(36, ProgrammingTableLayout.cellY(5));
        Assertions.assertEquals(43, ProgrammingTableLayout.cellX(TileProgrammingTable.OPTION_COLS));
        Assertions.assertEquals(36 + 18, ProgrammingTableLayout.cellY(TileProgrammingTable.OPTION_COLS));
        int last = TileProgrammingTable.OPTION_COLS * TileProgrammingTable.OPTION_ROWS - 1;
        Assertions.assertEquals(43 + 5 * 18, ProgrammingTableLayout.cellX(last));
        Assertions.assertEquals(36 + 3 * 18, ProgrammingTableLayout.cellY(last));
        // The grid's extent excludes the gap after the last cell: 6 cells + 5 gaps, 4 cells + 3 gaps.
        Assertions.assertEquals(6 * 16 + 5 * 2, ProgrammingTableLayout.gridWidth());
        Assertions.assertEquals(4 * 16 + 3 * 2, ProgrammingTableLayout.gridHeight());
    }

    @Test
    public void integrationRingIsReadingOrderSkippingTheCentre() {
        int[][] expected = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}};
        Assertions.assertEquals(expected.length, IntegrationTableLayout.RING_SLOTS);
        for (int i = 0; i < expected.length; i++) {
            Assertions.assertEquals(expected[i][0], IntegrationTableLayout.ringColumn(i), "ring slot " + i + " column");
            Assertions.assertEquals(expected[i][1], IntegrationTableLayout.ringRow(i), "ring slot " + i + " row");
            Assertions.assertEquals(i, IntegrationTableLayout.ringSlot(expected[i][0], expected[i][1]),
                    "ring cell (" + expected[i][0] + ", " + expected[i][1] + ") maps back to slot " + i);
        }
        Assertions.assertTrue(IntegrationTableLayout.isCentre(1, 1));
        Assertions.assertEquals(-1, IntegrationTableLayout.ringSlot(1, 1), "the centre is the target slot, not a ring slot");
    }

    @Test
    public void integrationCellsSitOnTheRingPitch() {
        Assertions.assertEquals(19, IntegrationTableLayout.ringX(0));
        Assertions.assertEquals(19 + 2 * 25, IntegrationTableLayout.ringX(2));
        Assertions.assertEquals(24, IntegrationTableLayout.ringY(0));
        Assertions.assertEquals(24 + 2 * 25, IntegrationTableLayout.ringY(2));
        Assertions.assertEquals(2 * 25 + 16, IntegrationTableLayout.ringSpan());
    }

    @Test
    public void programmingCategoryCropFramesWhatItDraws() {
        int left = ProgrammingTableCategory.TEX_U, top = ProgrammingTableCategory.TEX_V;
        int right = left + ProgrammingTableCategory.TEX_W, bottom = top + ProgrammingTableCategory.TEX_H;
        int cell = ProgrammingTableLayout.CELL_SIZE;
        assertInside("board slot", ProgrammingTableLayout.BOARD_X, ProgrammingTableLayout.BOARD_Y, cell, cell,
                left, top, right, bottom);
        assertInside("result slot", ProgrammingTableLayout.RESULT_X, ProgrammingTableLayout.RESULT_Y, cell, cell,
                left, top, right, bottom);
        assertInside("option grid", ProgrammingTableLayout.GRID_X, ProgrammingTableLayout.GRID_Y,
                ProgrammingTableLayout.gridWidth(), ProgrammingTableLayout.gridHeight(), left, top, right, bottom);
        assertInside("energy bar", ProgrammingTableLayout.BAR_X, ProgrammingTableLayout.BAR_Y,
                ProgrammingTableLayout.BAR_W, ProgrammingTableLayout.BAR_H, left, top, right, bottom);
    }

    @Test
    public void integrationCategoryCropFramesWhatItDraws() {
        int left = IntegrationTableCategory.TEX_U, top = IntegrationTableCategory.TEX_V;
        int right = left + IntegrationTableCategory.TEX_W, bottom = top + IntegrationTableCategory.TEX_H;
        int cell = IntegrationTableLayout.CELL_SIZE;
        assertInside("ring", IntegrationTableLayout.RING_X, IntegrationTableLayout.RING_Y,
                IntegrationTableLayout.ringSpan(), IntegrationTableLayout.ringSpan(), left, top, right, bottom);
        assertInside("preview slot", IntegrationTableLayout.PREVIEW_X, IntegrationTableLayout.PREVIEW_Y, cell, cell,
                left, top, right, bottom);
        assertInside("result slot", IntegrationTableLayout.RESULT_X, IntegrationTableLayout.RESULT_Y, cell, cell,
                left, top, right, bottom);
        assertInside("energy bar", IntegrationTableLayout.BAR_X, IntegrationTableLayout.BAR_Y,
                IntegrationTableLayout.BAR_W, IntegrationTableLayout.BAR_H, left, top, right, bottom);
    }

    private static void assertInside(String what, int x, int y, int w, int h, int left, int top, int right,
            int bottom) {
        Assertions.assertTrue(x >= left && y >= top && x + w <= right && y + h <= bottom,
                what + " (" + x + ", " + y + ", " + w + "x" + h + ") must lie inside the category's texture crop ("
                        + left + ", " + top + ")-(" + right + ", " + bottom + ")");
    }
}
