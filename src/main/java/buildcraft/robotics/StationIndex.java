/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import buildcraft.api.robots.DockingStation;

/**
 * Immutable (position, side) key identifying a docking station within a {@link RobotRegistry}, ported from
 * 7.1.x {@code buildcraft.robotics.StationIndex}. {@code BlockIndex}/{@code ForgeDirection} become
 * {@link BlockPos}/{@link Direction}; the legacy {@code UNKNOWN} side and the NBT round-trip are dropped —
 * the registry rebuilds indices straight from each station's own persisted position and side.
 */
public final class StationIndex {

    private final BlockPos index;
    private final Direction side;

    public StationIndex(Direction side, BlockPos pos) {
        this.side = Objects.requireNonNull(side, "side");
        this.index = Objects.requireNonNull(pos, "pos");
    }

    public StationIndex(DockingStation station) {
        this(station.side(), station.index());
    }

    public BlockPos index() {
        return index;
    }

    public Direction side() {
        return side;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        StationIndex other = (StationIndex) obj;
        return index.equals(other.index) && side == other.side;
    }

    @Override
    public int hashCode() {
        return index.hashCode() * 37 + side.ordinal();
    }

    @Override
    public String toString() {
        return "StationIndex{" + index + ", " + side + "}";
    }
}
