/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit pins for {@link ZoneSurveyBorder}: the terrain cells that outline the survey square. The terrain is
 * drawn on a world-aligned grid of {@code band x band} cells (a cell's height is its lowest-corner sample), and the
 * outline must be made of those same cells, or a border cell straddles two terrain cells and floats at the height
 * of one of them.
 */
public class ZoneSurveyBorderTest {

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    private static Set<Long> cells(int minX, int minZ, int maxX, int maxZ, double[] view, int band) {
        Set<Long> out = new HashSet<>();
        ZoneSurveyBorder.forEachCell(minX, minZ, maxX, maxZ, view[0], view[1], view[2], view[3], band,
                (x, z) -> Assertions.assertTrue(out.add(pack(x, z)), "cell " + x + "," + z + " listed twice"));
        return out;
    }

    private static final double[] WHOLE_WORLD = {-1e6, -1e6, 1e6, 1e6};

    @Test
    public void testCellsSitOnTheTerrainsStrideGrid() {
        // A planner at an arbitrary block puts the square's edges off the stride grid.
        for (int band : new int[] {1, 2, 4, 8}) {
            for (int[] tile : new int[][] {{0, 0}, {5, 13}, {-3, 7}, {123, -457}}) {
                int minX = tile[0] - 848;
                int minZ = tile[1] - 848;
                for (long cell : cells(minX, minZ, minX + 1696, minZ + 1696, WHOLE_WORLD, band)) {
                    int x = (int) (cell >> 32);
                    int z = (int) cell;
                    Assertions.assertEquals(0, Math.floorMod(x, band), "x " + x + " is on the grid, band " + band + ", tile " + tile[0]);
                    Assertions.assertEquals(0, Math.floorMod(z, band), "z " + z + " is on the grid, band " + band + ", tile " + tile[1]);
                }
            }
        }
    }

    @Test
    public void testEveryOutermostColumnOfTheVisibleEdgesIsCovered() {
        for (int band : new int[] {1, 2, 4, 8}) {
            int minX = 5 - 848;
            int minZ = 13 - 848;
            int maxX = minX + 1696;
            int maxZ = minZ + 1696;
            double[] view = {minX - 40.5, minZ - 40.0, minX + 700.25, minZ + 300.0}; // the north-west stretch
            Set<Long> cells = cells(minX, minZ, maxX, maxZ, view, band);
            for (int x = minX; x < minX + 700; x++) {
                Assertions.assertTrue(covered(cells, band, x, minZ), "north edge column " + x + ", band " + band);
            }
            for (int z = minZ; z < minZ + 299; z++) {
                Assertions.assertTrue(covered(cells, band, minX, z), "west edge column " + z + ", band " + band);
            }
        }
    }

    private static boolean covered(Set<Long> cells, int band, int x, int z) {
        return cells.contains(pack(Math.floorDiv(x, band) * band, Math.floorDiv(z, band) * band));
    }

    @Test
    public void testAllFourEdgesAreOutlinedWithNoGapOrOverlap() {
        int band = 8;
        int minX = -3, minZ = 11, maxX = minX + 400, maxZ = minZ + 240;
        Set<Long> cells = cells(minX, minZ, maxX, maxZ, WHOLE_WORLD, band);
        for (int x = minX; x < maxX; x++) {
            Assertions.assertTrue(covered(cells, band, x, minZ), "north " + x);
            Assertions.assertTrue(covered(cells, band, x, maxZ - 1), "south " + x);
        }
        for (int z = minZ; z < maxZ; z++) {
            Assertions.assertTrue(covered(cells, band, minX, z), "west " + z);
            Assertions.assertTrue(covered(cells, band, maxX - 1, z), "east " + z);
        }
        // Every cell lies on an edge: the interior of the square is not filled.
        for (long cell : cells) {
            int x = (int) (cell >> 32);
            int z = (int) cell;
            boolean onEdge = x <= minX || x + band >= maxX || z <= minZ || z + band >= maxZ;
            Assertions.assertTrue(onEdge, "cell " + x + "," + z + " is inside the square, not on its outline");
        }
    }

    @Test
    public void testAtOneBlockPerCellTheOutlineIsExactlyTheEdgeColumns() {
        Set<Long> cells = cells(0, 0, 20, 10, WHOLE_WORLD, 1);
        Set<Long> expected = new HashSet<>();
        for (int x = 0; x < 20; x++) {
            expected.add(pack(x, 0));
            expected.add(pack(x, 9));
        }
        for (int z = 0; z < 10; z++) {
            expected.add(pack(0, z));
            expected.add(pack(19, z));
        }
        Assertions.assertEquals(expected, cells);
    }

    @Test
    public void testNothingIsListedForASquareOutOfView() {
        Assertions.assertTrue(cells(0, 0, 100, 100, new double[] {500, 500, 600, 600}, 4).isEmpty());
        Assertions.assertTrue(cells(0, 0, 100, 100, new double[] {20, 20, 80, 80}, 4).isEmpty(),
                "a view wholly inside the square sees none of its outline");
    }
}
