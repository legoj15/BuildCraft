/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** Pins the grid-cell bounds {@link MarkerArenaUtil.Session#place} enforces. The cell is the 6x7 slot the
 *  framework gives a {@code minecraft:empty} test: x 0..5 and z 0..6 (z=7 is the NEXT ROW, x=6 the next column).
 *  The old orientation test wrote a path marker at x=6, i.e. into its neighbour's arena. */
public class MarkerArenaUtilTester {

    @Test
    public void cornersOfTheCellAreInside() {
        Assertions.assertTrue(MarkerArenaUtil.isInsideCell(new BlockPos(0, 0, 0)));
        Assertions.assertTrue(MarkerArenaUtil.isInsideCell(new BlockPos(5, 2, 0)));
        Assertions.assertTrue(MarkerArenaUtil.isInsideCell(new BlockPos(0, 2, 6)));
        Assertions.assertTrue(MarkerArenaUtil.isInsideCell(new BlockPos(5, 5, 6)));
    }

    @Test
    public void oneBlockPastAnyEdgeIsOutside() {
        Assertions.assertFalse(MarkerArenaUtil.isInsideCell(new BlockPos(6, 2, 2)), "x=6 is the next column");
        Assertions.assertFalse(MarkerArenaUtil.isInsideCell(new BlockPos(-1, 2, 2)));
        Assertions.assertFalse(MarkerArenaUtil.isInsideCell(new BlockPos(2, 2, 7)), "z=7 is the next row");
        Assertions.assertFalse(MarkerArenaUtil.isInsideCell(new BlockPos(2, 2, -1)));
    }

    @Test
    public void heightIsNotConstrained() {
        Assertions.assertTrue(MarkerArenaUtil.isInsideCell(new BlockPos(2, 40, 2)));
        Assertions.assertTrue(MarkerArenaUtil.isInsideCell(new BlockPos(2, -3, 2)));
    }
}
