/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import buildcraft.platform.Platform;

/**
 * Finding another block's or entity's storage, as a BuildCraft view. A BuildCraft storage found this way comes back as
 * itself (not wrapped), so {@code instanceof} checks on BuildCraft types keep working across the loader boundary.
 * Code that asks every tick should hold a {@link BCLookupCache} instead.
 */
public final class TransferAccess {
    private TransferAccess() {}

    /** The storage {@code capability} names at {@code pos} seen from {@code side}, or null if there is none. */
    public static <T> T find(BCCapability<T> capability, Level level, BlockPos pos, Direction side) {
        if (capability == BCCapability.ITEM) {
            return capability.type().cast(findItem(level, pos, side));
        }
        if (capability == BCCapability.FLUID) {
            return capability.type().cast(findFluid(level, pos, side));
        }
        throw new IllegalArgumentException("Unknown capability " + capability);
    }

    /** The item storage at {@code pos} seen from {@code side} (null: unsided), or null if there is none. */
    public static BCItemStorage findItem(Level level, BlockPos pos, Direction side) {
        return Platform.transfer().findItem(level, pos, side);
    }

    /** {@code entity}'s whole inventory, or null. */
    public static BCItemStorage findItem(Entity entity) {
        return Platform.transfer().findItem(entity);
    }

    /** The fluid storage at {@code pos} seen from {@code side} (null: unsided), or null if there is none. */
    public static BCFluidStorage findFluid(Level level, BlockPos pos, Direction side) {
        return Platform.transfer().findFluid(level, pos, side);
    }

    /** A cached fluid lookup at one position and side. */
    public static BCLookupCache<BCFluidStorage> fluidCache(ServerLevel level, BlockPos pos, Direction side) {
        return Platform.transfer().fluidCache(level, pos, side);
    }

    /** A cached item lookup at one position and side. */
    public static BCLookupCache<BCItemStorage> itemCache(ServerLevel level, BlockPos pos, Direction side) {
        return Platform.transfer().itemCache(level, pos, side);
    }
}
