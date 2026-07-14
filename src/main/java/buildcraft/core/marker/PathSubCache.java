/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.marker;

import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;


import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSubCache;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;

import buildcraft.core.BCCoreConfig;
import buildcraft.core.client.BuildCraftLaserManager;

@SuppressWarnings("this-escape")
public class PathSubCache extends MarkerSubCache<PathConnection> {

    @Override
    public LaserType getPossibleLaserType() {
        return BuildCraftLaserManager.MARKER_PATH_POSSIBLE;
    }

    public PathSubCache(Level world) {
        super(world, MarkerCache.CACHES.indexOf(PathCache.INSTANCE));
        initFromSavedData(PathSavedData.getOrCreate(world));
    }

    @Override
    protected PathConnection createConnection(List<BlockPos> positions) {
        return new PathConnection(this, positions);
    }

    @Override
    public boolean tryConnect(BlockPos from, BlockPos to) {
        PathConnection conFrom = getConnection(from);
        PathConnection conTo = getConnection(to);
        if (conFrom == null) {
            if (conTo == null) {
                return PathConnection.tryCreateConnection(this, from, to);
            } else {
                return conTo.addMarker(from, to);
            }
        } else {
            if (conTo == null) {
                return conFrom.addMarker(from, to);
            } else {
                return conFrom.mergeWith(conTo, from, to);
            }
        }
    }

    @Override
    public boolean canConnect(BlockPos from, BlockPos to) {
        PathConnection conFrom = getConnection(from);
        PathConnection conTo = getConnection(to);
        if (conFrom == null) {
            if (conTo == null) {
                return true;
            } else {
                return conTo.canAddMarker(from, to);
            }
        } else {
            if (conTo == null) {
                return conFrom.canAddMarker(from, to);
            } else {
                return conFrom.canMergeWith(conTo, from, to);
            }
        }
    }

    @Override
    public ImmutableList<BlockPos> getValidConnections(BlockPos from) {
        ImmutableList.Builder<BlockPos> list = ImmutableList.builder();
        final int maxLengthSquared = BCCoreConfig.markerMaxDistance.get() * BCCoreConfig.markerMaxDistance.get();
        for (BlockPos pos : getAllMarkers()) {
            if (pos.equals(from)) {
                continue;
            }
            if (pos.distSqr(from) > maxLengthSquared) {
                continue;
            }
            if (canConnect(from, pos) || canConnect(pos, from)) {
                list.add(pos);
            }
        }
        return list.build();
    }
}
