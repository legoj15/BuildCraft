/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import net.minecraft.core.BlockPos;

import buildcraft.api.core.IZone;

/**
 * Predicate identifying which blocks a {@link PathFindingSearch} is hunting for (e.g. "an ore", "a
 * harvestable crop"). Ported from 7.1.x {@code buildcraft.core.lib.utils.IBlockFilter}, with the
 * {@code (World, x, y, z)} signature collapsed to a single {@link BlockPos} and decoupled from a live
 * level — in production the filter closes over its level, in tests it reads an in-memory grid.
 */
@FunctionalInterface
public interface IBlockFilter {

    boolean matches(BlockPos pos);

    /** A filter narrowed to a zone: matches only when {@code delegate} matches and the zone contains the block. */
    default IBlockFilter within(IZone zone) {
        if (zone == null) {
            return this;
        }
        return pos -> matches(pos)
                && zone.contains(new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }
}
