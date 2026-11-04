/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.marker;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeSubCache;
import buildcraft.lib.net.MessageMarker;
import buildcraft.lib.test.FakeLevels;
import buildcraft.lib.test.StubMarkerCache;
import buildcraft.platform.RecordingHooks;
import buildcraft.platform.client.ClientHooks;
import buildcraft.platform.event.GameHooks;

/**
 * The CLIENT half of the marker caches. A {@link MarkerCache} keeps two tables keyed by dimension, one for the
 * server's levels and one for the client's. The client one is filled by {@link MessageMarker} packets and is
 * what the in-world marker renderer draws from, so it must not outlive the client level it describes: a
 * dimension key such as {@code minecraft:overworld} is the same key in every world a client ever joins, and
 * without a reset the next world was handed the previous world's markers and connections (drawn as ghost laser
 * boxes) until something happened to overwrite them. The reset has to run on the real event bus, so the tests
 * post {@link LevelEvent.Unload} for a client-flagged level the way {@code Minecraft.setLevel} and
 * {@code Minecraft.disconnect} do, and check what the next level to use the same key is handed.
 *
 * <p>The join resync that refills the cache afterwards is exercised against a real server level by the
 * {@code marker_join_resync_*} game tests; here only its subscription is pinned.
 */
public class MarkerCacheClientResetTester {

    private static final int VOLUME = MarkerCache.CACHES.indexOf(VolumeCache.INSTANCE);

    private Level clientA;
    private Level clientB;

    @BeforeEach
    void freshClientLevels() {
        clientA = FakeLevels.fake(true, Level.OVERWORLD);
        clientB = FakeLevels.fake(true, Level.NETHER);
    }

    @AfterEach
    void dropWhatTheTestBuilt() {
        MarkerCache.onWorldUnload(clientA);
        MarkerCache.onWorldUnload(clientB);
    }

    @Test
    void theRealCacheIsRegistered() {
        Assertions.assertTrue(VOLUME >= 0, "VolumeCache must be registered for the client packets to find it");
    }

    @Test
    void unloadingAClientLevelDropsItsClientSubCacheOnly() {
        VolumeSubCache overworld = VolumeCache.INSTANCE.getSubCache(clientA);
        VolumeSubCache nether = VolumeCache.INSTANCE.getSubCache(clientB);

        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(clientA));

        Assertions.assertNotSame(overworld, VolumeCache.INSTANCE.getSubCache(clientA),
                "the unloaded client level's marker sub-cache must be dropped, so the next world (same "
                        + "dimension key) is not handed this world's markers and connections");
        Assertions.assertSame(nether, VolumeCache.INSTANCE.getSubCache(clientB),
                "...but another dimension's client sub-cache is left alone");
    }

    @Test
    void connectionsFromTheOldWorldAreGoneInTheNextWorld() {
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(8, 64, 0);
        VolumeSubCache oldWorld = VolumeCache.INSTANCE.getSubCache(clientA);
        oldWorld.handleMessageMain(new MessageMarker(true, false, VOLUME, List.of(a, b)));
        oldWorld.handleMessageMain(new MessageMarker(true, true, VOLUME, List.of(a, b)));
        Assertions.assertNotNull(oldWorld.getConnection(a), "precondition: the old world has a connection");
        Assertions.assertTrue(oldWorld.hasLoadedOrUnloadedMarker(b), "precondition: and both markers");

        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(clientA));
        Level nextWorld = FakeLevels.fake(true, Level.OVERWORLD);
        try {
            VolumeSubCache fresh = VolumeCache.INSTANCE.getSubCache(nextWorld);
            Assertions.assertNull(fresh.getConnection(a), "a connection of the previous world must not be shown");
            Assertions.assertFalse(fresh.hasLoadedOrUnloadedMarker(a), "nor its markers");
            Assertions.assertTrue(fresh.getConnections().isEmpty());
        } finally {
            MarkerCache.onWorldUnload(nextWorld);
        }
    }

    @Test
    void aClientUnloadLeavesTheSameDimensionsServerSubCacheAlone() {
        StubMarkerCache cache = StubMarkerCache.register();
        Level server = FakeLevels.fake(false, Level.OVERWORLD);
        try {
            StubMarkerCache.SubCache serverSub = cache.getSubCache(server);
            StubMarkerCache.SubCache clientSub = cache.getSubCache(clientA);
            Assertions.assertNotSame(serverSub, clientSub, "precondition: the two sides have separate tables");

            NeoForge.EVENT_BUS.post(new LevelEvent.Unload(clientA));

            Assertions.assertNotSame(clientSub, cache.getSubCache(clientA), "the client table entry is dropped");
            Assertions.assertSame(serverSub, cache.getSubCache(server),
                    "...and the server table's entry for the SAME dimension key survives (integrated server)");

            NeoForge.EVENT_BUS.post(new LevelEvent.Unload(server));
            Assertions.assertNotSame(serverSub, cache.getSubCache(server), "a server unload still drops its own");
        } finally {
            cache.unregister();
        }
    }

    /** The client level-unload hook (Fabric's only client reset: its level change and disconnect name no level)
     *  drops every client table and leaves the server's alone. */
    @Test
    void theClientLevelCloseHookDropsEveryClientSubCacheOnly() {
        StubMarkerCache cache = StubMarkerCache.register();
        Level server = FakeLevels.fake(false, Level.OVERWORLD);
        try {
            StubMarkerCache.SubCache serverSub = cache.getSubCache(server);
            StubMarkerCache.SubCache overworld = cache.getSubCache(clientA);
            StubMarkerCache.SubCache nether = cache.getSubCache(clientB);

            RecordingHooks<ClientHooks> recorder = RecordingHooks.of(ClientHooks.class);
            MarkerCacheEvents.registerClientHooks(recorder.hooks());
            recorder.listener("onClientLevelUnload", Runnable.class).run();

            Assertions.assertNotSame(overworld, cache.getSubCache(clientA), "the closed level's client table is dropped");
            Assertions.assertNotSame(nether, cache.getSubCache(clientB), "...and every other client table with it");
            Assertions.assertSame(serverSub, cache.getSubCache(server),
                    "...but the server table of the same dimension key survives (integrated server)");
        } finally {
            cache.unregister();
        }
    }

    @Test
    void theJoinResyncIsRegistered() {
        // The entity-join hook is when a player joining a level (login, dimension change, respawn) needs the
        // markers sent.
        RecordingHooks<GameHooks> recorder = RecordingHooks.of(GameHooks.class);
        MarkerCacheEvents.registerHooks(recorder.hooks());
        Assertions.assertNotNull(recorder.listener("onEntityJoinLevel", GameHooks.EntityJoin.class));
    }
}
