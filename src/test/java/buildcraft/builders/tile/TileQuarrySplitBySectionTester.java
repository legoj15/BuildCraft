/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.AABB;

/**
 * Pins {@link TileQuarry#splitBySection}, the core of the quarry rig collision fix: each moving arm is
 * cut into 16-block, section-aligned segment entities so a player anywhere along it lands in an
 * entity-storage section MC's collision query actually scans. (A single long entity is only found near
 * its own filed position — which is why a boom-arm end, or a deep column, had no collision even though
 * the box rendered there.)
 *
 * <p>The load-bearing invariants: NO segment may straddle a 16-block section boundary (else the
 * far part of that segment is unreachable again), and the segments must tile the original box exactly —
 * contiguous, no gaps, no overlap, with the non-split axes untouched.
 */
public class TileQuarrySplitBySectionTester {

    private static final double EPS = 1.0e-6;

    private static double lo(AABB b, Axis a) {
        return a == Axis.X ? b.minX : a == Axis.Y ? b.minY : b.minZ;
    }

    private static double hi(AABB b, Axis a) {
        return a == Axis.X ? b.maxX : a == Axis.Y ? b.maxY : b.maxZ;
    }

    private static List<AABB> split(AABB box, Axis axis) {
        List<AABB> out = new ArrayList<>();
        TileQuarry.splitBySection(out, box, axis);
        return out;
    }

    /** Each segment lies within a single 16-block section, preserves the non-split axes, and the
     *  segments together tile {@code [box.lo, box.hi]} contiguously. */
    private static void assertValidSplit(AABB box, Axis axis) {
        List<AABB> out = split(box, axis);
        Assertions.assertFalse(out.isEmpty(), "split must produce at least one segment");

        double prevHi = lo(box, axis);
        for (AABB seg : out) {
            double sLo = lo(seg, axis);
            double sHi = hi(seg, axis);
            // Within one section: the low corner and the (boundary-safe) high corner share a section.
            int secLo = (int) Math.floor(sLo) >> 4;
            int secHi = (int) Math.floor(sHi - EPS) >> 4;
            Assertions.assertEquals(secLo, secHi,
                    "segment [" + sLo + ", " + sHi + "] straddles a 16-block section boundary");
            // Contiguous along the split axis — no gap, no overlap.
            Assertions.assertEquals(prevHi, sLo, EPS, "segments must be contiguous (gap/overlap near " + sLo + ")");
            prevHi = sHi;
            // The other two axes are copied verbatim from the source box.
            for (Axis other : Axis.values()) {
                if (other == axis) {
                    continue;
                }
                Assertions.assertEquals(lo(box, other), lo(seg, other), EPS, "non-split axis " + other + " min changed");
                Assertions.assertEquals(hi(box, other), hi(seg, other), EPS, "non-split axis " + other + " max changed");
            }
        }
        Assertions.assertEquals(hi(box, axis), prevHi, EPS, "segments must cover the whole box, end to end");
    }

    /** A beam that fits inside one section must not be split (small frames keep a single entity). */
    @Test
    public void boxWithinOneSectionStaysWhole() {
        AABB box = new AABB(130.25, 116.25, 138.5, 140.75, 116.75, 139.0); // X 130.25..140.75 ⊂ section 8
        Assertions.assertEquals(1, split(box, Axis.X).size(), "a box within one section must not be split");
        assertValidSplit(box, Axis.X);
    }

    /** A beam wider than a section is cut at the section boundaries. */
    @Test
    public void wideBeamSplitsAtSectionBoundaries() {
        AABB box = new AABB(5.25, 116.25, 10.5, 55.75, 116.75, 11.0); // X 5.25..55.75 spans sections 0..3
        Assertions.assertEquals(4, split(box, Axis.X).size(), "X 5.25..55.75 spans 4 sections (0,1,2,3)");
        assertValidSplit(box, Axis.X);
    }

    /** A deep drill column splits across negative-Y sections too (mining below y=0). */
    @Test
    public void deepColumnSplitsAcrossNegativeSections() {
        AABB box = new AABB(17.25, -62.25, 138.25, 17.75, 116.75, 138.75); // Y -62.25..116.75 spans sections -4..7
        Assertions.assertEquals(12, split(box, Axis.Y).size(), "Y -62.25..116.75 spans 12 sections (-4..7)");
        assertValidSplit(box, Axis.Y);
    }
}
