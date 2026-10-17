/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.marker.MarkerSubCache;

/**
 * A do-nothing {@link MarkerCache} for tests of the cache LIFECYCLE (which levels it holds a sub-cache for, and who
 * it is told about) as opposed to marker behaviour: its sub-caches read no saved data, so any level that answers
 * {@code isClientSide()} and {@code dimension()} can build one, and each records the players it was asked to
 * sync. Register it with {@link MarkerCache#registerCache}, which is what puts it on the event paths, and take it
 * out of {@link MarkerCache#CACHES} again when the test ends.
 */
public final class StubMarkerCache extends MarkerCache<StubMarkerCache.SubCache> {

    public StubMarkerCache() {
        super("stub");
    }

    @Override
    protected SubCache createSubCache(Level world) {
        return new SubCache(world, CACHES.indexOf(this));
    }

    /** Registers this cache and returns it. */
    public static StubMarkerCache register() {
        StubMarkerCache cache = new StubMarkerCache();
        registerCache(cache);
        return cache;
    }

    /** Takes this cache off the event paths. */
    public void unregister() {
        CACHES.remove(this);
    }

    public static final class SubCache extends MarkerSubCache<Connection> {
        /** Every player {@link #onPlayerJoinWorld} was called for, in order. */
        public final List<ServerPlayer> joined = new ArrayList<>();

        SubCache(Level world, int cacheId) {
            super(world, cacheId);
        }

        @Override
        public void onPlayerJoinWorld(ServerPlayer player) {
            joined.add(player);
        }

        @Override
        protected Connection createConnection(List<BlockPos> positions) {
            return new Connection(this);
        }

        @Override
        public boolean tryConnect(BlockPos from, BlockPos to) {
            return false;
        }

        @Override
        public boolean canConnect(BlockPos a, BlockPos b) {
            return false;
        }

        @Override
        public ImmutableList<BlockPos> getValidConnections(BlockPos from) {
            return ImmutableList.of();
        }

        @Override
        public LaserType getPossibleLaserType() {
            return null;
        }
    }

    public static final class Connection extends MarkerConnection<Connection> {
        Connection(MarkerSubCache<Connection> subCache) {
            super(subCache);
        }

        @Override
        public void removeMarker(BlockPos pos) {
        }

        @Override
        public Collection<BlockPos> getMarkerPositions() {
            return List.of();
        }

        @Override
        public void renderInWorld() {
        }
    }
}
