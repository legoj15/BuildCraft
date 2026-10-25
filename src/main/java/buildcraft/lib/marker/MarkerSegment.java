/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.marker;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * One straight laser between two marker blocks, drawn centre to centre. Order-independent: the
 * constructor sorts the ends so a segment reached from either marker compares equal.
 */
public record MarkerSegment(BlockPos from, BlockPos to) {

    /** How far off the laser the view ray may pass and still count as aiming at it, at point-blank range. */
    static final double AIM_TOLERANCE = 0.15;
    /** Extra tolerance per block of distance (just under 1 degree), so a far laser stays aimable. */
    static final double AIM_TOLERANCE_PER_BLOCK = 0.015;
    /** How much a block of distance weighs against a block of offset when two lasers compete. */
    private static final double DISTANCE_WEIGHT = 0.0005;

    public MarkerSegment {
        if (from.compareTo(to) > 0) {
            BlockPos swap = from;
            from = to;
            to = swap;
        }
    }

    /** The number of blocks the segment covers on each axis, both marker blocks included. */
    public int[] spans() {
        return new int[] {
            Math.abs(to.getX() - from.getX()) + 1,
            Math.abs(to.getY() - from.getY()) + 1,
            Math.abs(to.getZ() - from.getZ()) + 1
        };
    }

    /**
     * Where a view ray passes this segment, if it passes close enough to count as aiming at it.
     *
     * @param eye the ray's origin.
     * @param look the ray's direction; need not be normalised.
     * @param maxDistance the furthest along the ray a hit may lie.
     */
    @Nullable
    public Aim aim(Vec3 eye, Vec3 look, double maxDistance) {
        Vec3 a = Vec3.atCenterOf(from);
        Vec3 u = Vec3.atCenterOf(to).subtract(a);
        Vec3 d = look.normalize();
        Vec3 w = a.subtract(eye);
        double uu = u.dot(u);
        if (uu < 1e-9) {
            return null;
        }
        double ud = u.dot(d);
        double uw = u.dot(w);
        double dw = d.dot(w);
        // Closest approach of segment a + s*u (s in [0,1]) to ray eye + t*d (t >= 0): solve on the
        // segment, clamp, re-solve the ray parameter for that point, clamp, then settle s once more.
        double denom = uu - ud * ud;
        double s = denom > 1e-9 ? clamp01((ud * dw - uw) / denom) : 0;
        double t = Math.max(0, ud * s + dw);
        s = clamp01((ud * t - uw) / uu);
        t = Math.max(0, ud * s + dw);
        if (t <= 0 || t > maxDistance) {
            return null;
        }
        Vec3 onLaser = a.add(u.scale(s));
        double offset = onLaser.distanceTo(eye.add(d.scale(t)));
        if (offset > AIM_TOLERANCE + AIM_TOLERANCE_PER_BLOCK * t) {
            return null;
        }
        return new Aim(this, offset, t, onLaser);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /**
     * A segment the view ray passes near.
     *
     * @param offset how far the ray passes from the laser.
     * @param distance how far along the ray that closest pass lies.
     * @param point the point on the laser at that closest pass.
     */
    public record Aim(MarkerSegment segment, double offset, double distance, Vec3 point) {
        /** Lower is a better pick: mostly how close the ray passes, with nearness breaking near-ties. A single
         *  score keeps the pick independent of the order the lasers are tried in. */
        public double score() {
            return offset + DISTANCE_WEIGHT * distance;
        }
    }
}
