/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;

import buildcraft.core.client.MarkerDistanceTracker.Reach;
import buildcraft.core.client.MarkerDistanceTracker.Readout;

/** The distance line promises "a marker placed where you stand would connect". The marker connects across gaps of 1
 *  to the limit inclusive (VolumeConnection.canCreateConnection), so the readout's colour flips on an exact boundary,
 *  and only along an axis the marker's connection has not used. These pin both edges. */
public class MarkerDistanceTrackerTester {

    private static final BlockPos MARKER = new BlockPos(10, 64, -20);
    private static final Set<Axis> NONE = EnumSet.noneOf(Axis.class);
    private static final int LIMIT = 8;

    private static Readout at(int dx, int dy, int dz, Set<Axis> taken) {
        return MarkerDistanceTracker.classify(MARKER, MARKER.offset(dx, dy, dz), taken, LIMIT);
    }

    @Test
    public void theLimitItselfConnectsAndOneMoreDoesNot() {
        assertEquals(new Readout(LIMIT - 1, Reach.OK, true), at(LIMIT - 1, 0, 0, NONE));
        assertEquals(new Readout(LIMIT, Reach.AT_LIMIT, true), at(LIMIT, 0, 0, NONE));
        assertEquals(new Readout(LIMIT + 1, Reach.TOO_FAR, true), at(LIMIT + 1, 0, 0, NONE));
        // The gap is unsigned and the same on every axis and either side of the marker.
        assertEquals(new Readout(LIMIT, Reach.AT_LIMIT, true), at(0, -LIMIT, 0, NONE));
        assertEquals(new Readout(LIMIT + 1, Reach.TOO_FAR, true), at(0, 0, -(LIMIT + 1), NONE));
    }

    @Test
    public void alignedOnlyAlongAFreeAxis() {
        // Standing in the marker's own block is aligned, at no distance.
        assertEquals(new Readout(0, Reach.OK, true), at(0, 0, 0, NONE));
        assertEquals(new Readout(0, Reach.OK, true), at(0, 0, 0, EnumSet.allOf(Axis.class)));
        // An axis the connection already uses cannot take another marker; the distance is still read.
        assertEquals(new Readout(3, Reach.OK, false), at(3, 0, 0, EnumSet.of(Axis.X)));
        assertEquals(new Readout(3, Reach.OK, true), at(3, 0, 0, EnumSet.of(Axis.Y, Axis.Z)));
        // Off the line: the largest gap is shown, and it is not aligned however free the axes are.
        assertEquals(new Readout(5, Reach.OK, false), at(5, 2, 0, NONE));
        assertEquals(new Readout(LIMIT + 1, Reach.TOO_FAR, false), at(1, -(LIMIT + 1), 1, NONE));
    }
}
