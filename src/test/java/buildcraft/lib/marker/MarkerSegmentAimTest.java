/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.marker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** The marker length indicator's pick: which laser the crosshair is on, and how many blocks it covers. */
public class MarkerSegmentAimTest {
    /** A laser along X from (0,64,0) to (9,64,0): centres at y=64.5, z=0.5. */
    private static final MarkerSegment LINE = new MarkerSegment(new BlockPos(9, 64, 0), new BlockPos(0, 64, 0));

    @Test
    void lengthCountsBothMarkerBlocks() {
        assertArrayEquals(new int[] {10, 1, 1}, LINE.spans());
        assertEquals(LINE, new MarkerSegment(new BlockPos(0, 64, 0), new BlockPos(9, 64, 0)));
    }

    @Test
    void aimingAtTheLaserHitsItAndMissingDoesNot() {
        // Standing 5 blocks off the laser's middle, looking straight at it.
        Vec3 eye = new Vec3(4.5, 64.5, 5.5);
        MarkerSegment.Aim aim = LINE.aim(eye, new Vec3(0, 0, -1), 64);
        assertNotNull(aim);
        assertEquals(5, aim.distance(), 1e-6);
        assertEquals(0, aim.offset(), 1e-6);

        // Looking a block above it, behind it, or past its end: no pick.
        assertNull(LINE.aim(eye, new Vec3(0, 0.2, -1), 64));
        assertNull(LINE.aim(eye, new Vec3(0, 0, 1), 64));
        assertNull(LINE.aim(new Vec3(14.5, 64.5, 5.5), new Vec3(0, 0, -1), 64));
        // Too far away to read.
        assertNull(LINE.aim(eye, new Vec3(0, 0, -1), 4));
    }

    @Test
    void aimingAlongTheLaserFromItsEndStillHits() {
        // Looking straight down the line (parallel case) from just past one end.
        MarkerSegment.Aim aim = LINE.aim(new Vec3(-2, 64.5, 0.5), new Vec3(1, 0, 0), 64);
        assertNotNull(aim);
        assertTrue(aim.offset() < 1e-6);
    }

    @Test
    void theLaserNearestTheCrosshairWins() {
        // Aimed dead on LINE; a second, nearer laser sits 0.1 off the same ray, within tolerance.
        Vec3 eye = new Vec3(4.5, 64.75, 5.5);
        Vec3 look = new Vec3(0, -0.05, -1);
        MarkerSegment near = new MarkerSegment(new BlockPos(0, 64, 2), new BlockPos(9, 64, 2));
        MarkerSegment.Aim onRay = LINE.aim(eye, look, 64);
        MarkerSegment.Aim offRay = near.aim(eye, look, 64);
        assertNotNull(onRay);
        assertNotNull(offRay);
        assertTrue(offRay.distance() < onRay.distance());
        assertTrue(onRay.score() < offRay.score());

        // A marker pair at one spot (malformed sync) never picks.
        BlockPos p = new BlockPos(0, 64, 0);
        assertNull(new MarkerSegment(p, p).aim(new Vec3(0.5, 64.5, 3.5), new Vec3(0, 0, -1), 64));
    }
}
