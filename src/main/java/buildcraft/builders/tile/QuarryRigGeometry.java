/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.List;

import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.AABB;

/**
 * Pure geometry for the quarry's moving collision rig — no block-entity, registry, or NeoForge state, so
 * it can be unit-tested directly (loading {@link TileQuarry} drags in NeoForge's attachment/FML statics,
 * which aren't booted under plain JUnit).
 *
 * <p>The quarry's collision is three invisible entities (two horizontal boom arms + the vertical drill
 * column). MC files each entity in the SINGLE entity-storage section at its position and a collision query
 * only scans sections within the query box, so a long entity is only found near its own centre — which is
 * why a boom-arm end, or a deep column, had no collision even though its box rendered there. The fix is to
 * cut each arm into 16-block, section-aligned segment entities so a player anywhere along it lands in a
 * section that holds a collidable piece. {@link #splitBySection} is that cut.
 */
public final class QuarryRigGeometry {

    private QuarryRigGeometry() {}

    /** Appends {@code box} to {@code out}, cut into pieces aligned to 16-block entity-storage sections
     *  along {@code axis} (so each piece's centre — where its entity is filed — sits in its own section).
     *  The non-split axes are copied verbatim; a box already within one section is appended whole. */
    public static void splitBySection(List<AABB> out, AABB box, Axis axis) {
        double min = axis == Axis.X ? box.minX : axis == Axis.Y ? box.minY : box.minZ;
        double max = axis == Axis.X ? box.maxX : axis == Axis.Y ? box.maxY : box.maxZ;
        int sectionMin = (int) Math.floor(min) >> 4;
        int sectionMax = (int) Math.floor(max - 1.0e-7) >> 4;
        for (int s = sectionMin; s <= sectionMax; s++) {
            double lo = Math.max(min, (double) (s << 4));
            double hi = Math.min(max, (double) ((s + 1) << 4));
            if (hi - lo < 1.0e-4) {
                continue;
            }
            out.add(switch (axis) {
                case X -> new AABB(lo, box.minY, box.minZ, hi, box.maxY, box.maxZ);
                case Y -> new AABB(box.minX, lo, box.minZ, box.maxX, hi, box.maxZ);
                case Z -> new AABB(box.minX, box.minY, lo, box.maxX, box.maxY, hi);
            });
        }
    }
}
