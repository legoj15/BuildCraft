/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.core.marker.VolumeCache;
import buildcraft.core.block.BlockMarkerVolume;
import buildcraft.core.marker.VolumeSubCache;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.block.ILocalBlockUpdateSubscriber;
import buildcraft.lib.block.LocalBlockUpdateNotifier;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.ai.AIRobotFetchItem;
import buildcraft.robotics.boards.BoardRobotPicker;
import buildcraft.robotics.entity.EntityRobot;

/**
 * Static / singleton tables that hold block entities, entities or AIs - and through them a whole {@code Level} -
 * must forget a level when it unloads (which is also what a server stop does, once per level), or a stopped
 * integrated server's world stays reachable from the mod for as long as the client sits on the title screen.
 * Three held one: the laser block-update notifier (a strong subscriber set per level), the marker caches
 * (loaded marker block entities, keyed by dimension and so also handed to the NEXT world that has the same
 * dimension), and the picker's fetch-claim table (claimant AI -> robot -> level).
 *
 * <p>The event goes through the REAL NeoForge bus, so the wiring is under test, not just the handlers - on a
 * secondary level (the nether), never the arena's own: posting an unload for the arena level would wipe state the
 * sibling tests are using. The only other listeners of {@code LevelEvent.Unload} are NeoForge's fake-player
 * cleanup and BuildCraft's own fake-player cache, both harmless for a level no test touches.
 */
public class StaticTablesLevelUnloadTester {

    private static ServerLevel secondaryLevel(GameTestHelper helper) {
        ServerLevel arena = helper.getLevel();
        for (ServerLevel level : arena.getServer().getAllLevels()) {
            if (level != arena) {
                return level;
            }
        }
        helper.fail("the game-test server has no level besides the arena's, so unloading one cannot be tested "
                + "without disturbing sibling tests");
        return arena;
    }

    private static EntityRobot robotIn(Level level) {
        return new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
    }

    /** A subscriber that does nothing; only its presence in a level's notifier matters. */
    private static final class NullSubscriber implements ILocalBlockUpdateSubscriber {
        @Override
        public BlockPos getSubscriberPos() {
            return BlockPos.ZERO;
        }

        @Override
        public int getUpdateRange() {
            return 0;
        }

        @Override
        public void setLevelUpdated(Level world, BlockPos eventPos, BlockState oldState, BlockState newState,
                int flags) {
        }
    }

    public static void unloadingALevelClearsTheTablesThatPinIt(GameTestHelper helper) {
        ServerLevel arena = helper.getLevel();
        ServerLevel other = secondaryLevel(helper);

        // The laser notifier: a subscriber registered on each level.
        LocalBlockUpdateNotifier otherNotifier = LocalBlockUpdateNotifier.instance(other);
        LocalBlockUpdateNotifier arenaNotifier = LocalBlockUpdateNotifier.instance(arena);
        NullSubscriber subscriber = new NullSubscriber();
        otherNotifier.registerSubscriberForUpdateNotifications(subscriber);

        // The marker caches: a built sub-cache per level.
        VolumeSubCache otherMarkers = VolumeCache.INSTANCE.getSubCache(other);
        VolumeSubCache arenaMarkers = VolumeCache.INSTANCE.getSubCache(arena);

        // The picker's claim table: one claim by a robot of each level.
        UUID otherItem = UUID.randomUUID();
        UUID arenaItem = UUID.randomUUID();
        AIRobotFetchItem otherFetch = new AIRobotFetchItem(robotIn(other));
        AIRobotFetchItem arenaFetch = new AIRobotFetchItem(robotIn(arena));
        BoardRobotPicker.claimTarget(otherItem, otherFetch);
        BoardRobotPicker.claimTarget(arenaItem, arenaFetch);

        try {
            helper.assertTrue(BoardRobotPicker.isTargetted(otherItem) && BoardRobotPicker.isTargetted(arenaItem),
                    "precondition: both claims are live");

            NeoForge.EVENT_BUS.post(new LevelEvent.Unload(other));

            helper.assertTrue(LocalBlockUpdateNotifier.instance(other) != otherNotifier,
                    "the unloaded level's block-update notifier (and the lasers it holds) must be dropped");
            helper.assertTrue(LocalBlockUpdateNotifier.instance(arena) == arenaNotifier,
                    "...but another level's notifier must be left alone");

            helper.assertTrue(VolumeCache.INSTANCE.getSubCache(other) != otherMarkers,
                    "the unloaded level's marker sub-cache (and the marker block entities it holds) must be "
                            + "dropped, so the next world with this dimension does not inherit it");
            helper.assertTrue(VolumeCache.INSTANCE.getSubCache(arena) == arenaMarkers,
                    "...but another level's marker sub-cache must be left alone");

            helper.assertFalse(BoardRobotPicker.isTargetted(otherItem),
                    "a fetch claim held by a robot of the unloaded level must be dropped");
            helper.assertTrue(BoardRobotPicker.isTargetted(arenaItem),
                    "...but a claim held by a robot of another level must be kept");
            helper.succeed();
        } finally {
            otherNotifier.removeSubscriberFromUpdateNotifications(subscriber);
            BoardRobotPicker.releaseTarget(arenaItem, arenaFetch);
            BoardRobotPicker.releaseTarget(otherItem, otherFetch);
        }
    }
    /** The unload event dropped a level's marker sub-cache, but the level's chunks can still unload after it (a
     *  server stop posts the event before it closes the level), and each marker block entity in them reports its
     *  chunk unload to the cache. That report must not build the dimension's sub-cache again: a rebuilt one would
     *  re-pin the stopped level (and hand its markers to the next world with that dimension) - the very leak the
     *  unload hook exists to stop. A sub-cache rebuilt later from saved data holds the same "marker without a
     *  loaded tile" entry the report would have written, so skipping it loses nothing.
     *
     *  <p>The marker block entity is never placed in a level or registered in any cache (only its chunk-unload hook
     *  runs, against the secondary level), so there is nothing for the shared marker caches to leak. */
    public static void aMarkerUnloadingAfterItsLevelDoesNotRebuildTheCache(GameTestHelper helper) {
        ServerLevel other = secondaryLevel(helper);
        Block markerBlock = BuiltInRegistries.BLOCK.stream().filter(b -> b instanceof BlockMarkerVolume).findFirst()
                .orElse(null);
        helper.assertTrue(markerBlock != null, "precondition: the volume marker block is registered");
        TileMarkerVolume marker = new TileMarkerVolume(new BlockPos(0, 64, 0), markerBlock.defaultBlockState());
        marker.setLevel(other);

        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(other));
        helper.assertTrue(VolumeCache.INSTANCE.getSubCacheIfPresent(other) == null,
                "precondition: the unload dropped the level's marker sub-cache");

        marker.onChunkUnloaded();

        helper.assertTrue(VolumeCache.INSTANCE.getSubCacheIfPresent(other) == null,
                "a marker's chunk unload after its level unloaded must not rebuild the sub-cache - it would pin the "
                        + "stopped level again");
        helper.succeed();
    }

    /** The other way a marker block entity reports in to its cache after the level's unload dropped the sub-cache:
     *  being removed. The {@code TileMarker.setRemoved} guard is defence in depth: no known production path calls
     *  {@code setRemoved} after the sub-cache is dropped (as a level closes, vanilla's
     *  {@code LevelChunk.clearAllBlockEntities} reports the chunk unload before it removes the block entity). Removing
     *  the position from a cache that is not there loses nothing - a sub-cache rebuilt later from saved data starts
     *  from the saved markers - but looking it up with the building accessor would pin the stopped level again,
     *  exactly like the chunk-unload report. */
    public static void aMarkerRemovedAfterItsLevelDoesNotRebuildTheCache(GameTestHelper helper) {
        ServerLevel other = secondaryLevel(helper);
        Block markerBlock = BuiltInRegistries.BLOCK.stream().filter(b -> b instanceof BlockMarkerVolume).findFirst()
                .orElse(null);
        helper.assertTrue(markerBlock != null, "precondition: the volume marker block is registered");
        TileMarkerVolume marker = new TileMarkerVolume(new BlockPos(0, 64, 0), markerBlock.defaultBlockState());
        marker.setLevel(other);

        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(other));
        helper.assertTrue(VolumeCache.INSTANCE.getSubCacheIfPresent(other) == null,
                "precondition: the unload dropped the level's marker sub-cache");

        marker.setRemoved();

        helper.assertTrue(VolumeCache.INSTANCE.getSubCacheIfPresent(other) == null,
                "a marker's removal after its level unloaded must not rebuild the sub-cache - it would pin the "
                        + "stopped level again");
        helper.succeed();
    }
}
