/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Characterization of {@link ZoneFacePreview#worldColumn} — the in-world face preview's screen-cell &rarr;
 * world-column sampling, ported sign-for-sign from 1.12.2's {@code RenderZonePlanner}. These lock the two
 * things easiest to get wrong: the per-facing axis swap (the map rotates with the block) and the
 * world&rarr;cell offsets. The actual on-face placement (mirroring/up-down) is geometric and verified
 * in-client; this only pins the data the screen shows.
 */
public class ZoneFacePreviewTest {

    private static final int TX = 100, TZ = -40;

    private static void assertColumn(Direction facing, int sx, int sy, int expX, int expZ) {
        int[] c = ZoneFacePreview.worldColumn(TX, TZ, facing, sx, sy);
        Assertions.assertArrayEquals(new int[]{ expX, expZ }, c,
                facing + " cell (" + sx + "," + sy + ")");
    }

    @Test
    void gridDimensionsMatch1122() {
        Assertions.assertEquals(10, ZoneFacePreview.GRID_W);
        Assertions.assertEquals(8, ZoneFacePreview.GRID_H);
        Assertions.assertEquals(4, ZoneFacePreview.SCALE);
    }

    @Test
    void centreCellIsTheTileColumnForEveryFacing() {
        // sx=GRID_W/2, sy=GRID_H/2 → zero offset on both axes, regardless of facing.
        for (Direction f : Direction.Plane.HORIZONTAL) {
            assertColumn(f, 5, 4, TX, TZ);
        }
    }

    @Test
    void northSamplesAlongXandZ() {
        // Horizontal cell → world X; vertical cell → world -Z (forward/up the screen is -Z).
        assertColumn(Direction.NORTH, 9, 4, TX + 16, TZ);       // far right
        assertColumn(Direction.NORTH, 0, 4, TX - 20, TZ);       // far left
        assertColumn(Direction.NORTH, 5, 7, TX, TZ - 12);       // top
        assertColumn(Direction.NORTH, 5, 0, TX, TZ + 16);       // bottom
    }

    @Test
    void facingSwapsTheSampledAxes() {
        // The same (sx,sy) maps onto rotated world axes per facing — the rotate-with-block behaviour.
        assertColumn(Direction.EAST,  9, 4, TX, TZ + 16);       // horizontal → +Z
        assertColumn(Direction.EAST,  5, 7, TX + 12, TZ);       // vertical → +X
        assertColumn(Direction.SOUTH, 9, 4, TX + 16, TZ);       // horizontal → +X (mirror of north's Z)
        assertColumn(Direction.SOUTH, 5, 7, TX, TZ + 12);       // vertical → +Z
        assertColumn(Direction.WEST,  9, 4, TX, TZ + 16);       // horizontal → +Z
        assertColumn(Direction.WEST,  5, 7, TX - 12, TZ);       // vertical → -X
    }

    @Test
    void horizontalRowKeepsTheOtherAxisFixed() {
        // Sweeping sx along a row must not move the perpendicular world axis (and vice versa) — a swapped
        // axis would smear the map diagonally.
        int z0 = ZoneFacePreview.worldColumn(TX, TZ, Direction.NORTH, 0, 3)[1];
        for (int sx = 0; sx < ZoneFacePreview.GRID_W; sx++) {
            Assertions.assertEquals(z0, ZoneFacePreview.worldColumn(TX, TZ, Direction.NORTH, sx, 3)[1],
                    "north row sx=" + sx + " drifted in Z");
        }
        int x0 = ZoneFacePreview.worldColumn(TX, TZ, Direction.NORTH, 6, 0)[0];
        for (int sy = 0; sy < ZoneFacePreview.GRID_H; sy++) {
            Assertions.assertEquals(x0, ZoneFacePreview.worldColumn(TX, TZ, Direction.NORTH, 6, sy)[0],
                    "north column sy=" + sy + " drifted in X");
        }
    }

    @Test
    void windowStaysWithinThePortedFootprint() {
        // The full grid samples a bounded box around the planner (~36x28 here for the 10x8 grid at scale 4),
        // confirming it never reaches beyond a chunk or two — i.e. inside normal render distance.
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int sx = 0; sx < ZoneFacePreview.GRID_W; sx++) {
            for (int sy = 0; sy < ZoneFacePreview.GRID_H; sy++) {
                int[] c = ZoneFacePreview.worldColumn(TX, TZ, Direction.NORTH, sx, sy);
                minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
                minZ = Math.min(minZ, c[1]); maxZ = Math.max(maxZ, c[1]);
            }
        }
        Assertions.assertEquals(TX - 20, minX);
        Assertions.assertEquals(TX + 16, maxX);
        Assertions.assertEquals(TZ - 12, minZ);
        Assertions.assertEquals(TZ + 16, maxZ);
    }
}
