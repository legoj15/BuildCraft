/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.TileMarkerPath;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.net.MessageMarker;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.FakeLevels;
import buildcraft.lib.test.MarkerArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil.Session;
import buildcraft.lib.test.MarkerArenaUtil.Stray;

/**
 * Marker connection tests.
 *
 * <p><b>Why marker tests need more than a normal arena:</b> marker connections live in a process-wide singleton
 * cache keyed only by dimension ({@link VolumeCache}/{@link PathCache}), {@code runGameTestServer} packs every test
 * arena into one shared grid only 6x7 blocks apart, and the connection scan runs up to {@code markerMaxDistance}
 * (64) blocks in a straight line, counting <em>unloaded</em> markers too. So a marker anywhere on a row, column
 * or height shared with a test's markers — in a neighbour's arena, or left behind by an earlier test — is a
 * connection candidate or a line-of-sight obstacle, and corrupts the result (box collapses to one block, or is
 * pulled out to the stray).
 *
 * <p>Two layers of defence, both needed:
 * <ul>
 * <li><b>No concurrency.</b> The game-test framework runs every test of one {@code environment} concurrently and
 *     different batches strictly one after another, so each marker test declares its <em>own</em>
 *     {@code test_environment} (see {@code src/test/resources/data/buildcraftunofficial/test_environment/}).
 *     Keep one environment per marker test; {@code MarkerTestIsolationTester} enforces it. This does NOT clear
 *     leftovers: the runner never clears the grid between batches.</li>
 * <li><b>No leftovers.</b> Every test here runs through {@link MarkerArenaUtil}, which evicts any marker already
 *     in scan range when it opens, cleans up on every exit path (including failures, so one failing test does
 *     not fail its neighbours), keeps all writes inside the 6x7 cell, and gates on observed registration
 *     instead of fixed ticks. Any other game test that places a marker should remove it again before it
 *     succeeds; if one forgets, this class evicts it, and the WARN log line names the position.</li>
 * </ul>
 */
public class MarkerTester {

    /** How long a test waits for its markers to join the cache (they do on the first level tick after placement,
     *  so this is a generous upper bound, not an expected duration). Must stay well under the manifests' max_ticks. */
    private static final int REGISTER_TICKS = 40;
    /** Ticks a finished connection is watched before the test passes, to catch anything tearing it down. */
    private static final int SETTLE_TICKS = 5;

    public static void testMarkerOrientation(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        arena.guard(() -> {
            // Test placing markers on all 6 faces of a central block. Both groups share the cell: the volume
            // group's faces span z 1..3 and the path group's z 4..6, all inside the 6x7 cell.
            BlockPos centerVolume = new BlockPos(2, 2, 2);
            BlockPos centerPath = new BlockPos(2, 2, 5);
            arena.place(centerVolume, Blocks.STONE);
            arena.place(centerPath, Blocks.STONE);

            List<BlockPos> volumePositions = new ArrayList<>();
            List<BlockPos> pathPositions = new ArrayList<>();
            for (Direction dir : Direction.values()) {
                // Volume markers should float
                BlockPos volumePos = centerVolume.relative(dir);
                BlockState volumeState = BCCoreBlocks.MARKER_VOLUME.get().defaultBlockState()
                        .setValue(BuildCraftProperties.BLOCK_FACING_6, dir);
                arena.place(volumePos, volumeState);
                if (!helper.getBlockState(volumePos).is(BCCoreBlocks.MARKER_VOLUME.get())) {
                    throw new IllegalStateException("Failed to place volume marker on face: " + dir);
                }
                volumePositions.add(volumePos);

                // Path markers should pop
                BlockPos pathPos = centerPath.relative(dir);
                BlockState pathState = BCCoreBlocks.MARKER_PATH.get().defaultBlockState()
                        .setValue(BuildCraftProperties.BLOCK_FACING_6, dir);
                arena.place(pathPos, pathState);
                if (!helper.getBlockState(pathPos).is(BCCoreBlocks.MARKER_PATH.get())) {
                    throw new IllegalStateException("Failed to place path marker on face: " + dir);
                }
                pathPositions.add(pathPos);
            }

            // Drops are asserted by identity: only item entities that did not exist before the supports go away
            // count (a neighbouring test's leftover path-marker item must not satisfy this test).
            Set<Integer> preexisting = new HashSet<>();
            for (ItemEntity item : EntityArenaUtil.droppedItems(helper, centerPath, 4.0)) {
                preexisting.add(item.getId());
            }
            Supplier<List<ItemEntity>> freshItems = () -> EntityArenaUtil.droppedItems(helper, centerPath, 4.0).stream()
                    .filter(item -> !preexisting.contains(item.getId())).toList();
            Supplier<List<ItemEntity>> freshPathItems = () -> freshItems.get().stream()
                    .filter(item -> item.getItem().is(BCCoreBlocks.MARKER_PATH.get().asItem())).toList();
            Supplier<List<BlockPos>> stillStanding = () -> pathPositions.stream()
                    .filter(pos -> !helper.getBlockState(pos).isAir()).toList();

            // Remove the center blocks; the popping happens on scheduled block ticks, so wait for it to be observed.
            arena.remove(centerVolume);
            arena.remove(centerPath);

            arena.await(REGISTER_TICKS, () -> stillStanding.get().isEmpty() && !freshPathItems.get().isEmpty(), () -> {
                for (BlockPos volumePos : volumePositions) {
                    // Volume markers should float independently!
                    if (!helper.getBlockState(volumePos).is(BCCoreBlocks.MARKER_VOLUME.get())) {
                        throw new IllegalStateException("Volume Marker popped off when it should have freely suspended: "
                                + volumePos);
                    }
                }
                freshItems.get().forEach(ItemEntity::discard);
                arena.finish();
            }, () -> "Path markers did not all pop off with their supporting block removed and drop an item: still standing "
                    + stillStanding.get() + ", fresh path-marker items " + freshPathItems.get().size());
        });
    }

    public static void testVolumeLineOfSight(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        arena.guard(() -> {
            BlockPos pos1 = new BlockPos(1, 2, 1);
            BlockPos pos2 = new BlockPos(5, 2, 1);
            BlockPos wallPos = new BlockPos(3, 2, 1);
            arena.place(pos1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(pos2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(wallPos, Blocks.STONE); // Solid block obstructing

            arena.awaitThen(REGISTER_TICKS, () -> volumeRegistered(arena, pos1, pos2), () -> {
                TileMarkerVolume volume = volumeAt(helper, pos1);
                volume.onManualConnectionAttempt(null);
                assertBox(helper, volume, pos1, pos2, "Line of sight failed through solid block!");
            }, SETTLE_TICKS, () -> {
                assertBox(helper, volumeAt(helper, pos1), pos1, pos2, "Line of sight connection did not hold!");
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }

    public static void testPathLineOfSight(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        arena.guard(() -> {
            BlockPos pos1 = new BlockPos(1, 2, 1);
            BlockPos pos2 = new BlockPos(5, 2, 5); // Diagonal
            BlockPos wallPos = new BlockPos(3, 2, 3);
            arena.place(pos1, BCCoreBlocks.MARKER_PATH.get());
            arena.place(pos2, BCCoreBlocks.MARKER_PATH.get());
            arena.place(wallPos, Blocks.STONE); // Solid block obstructing

            arena.awaitThen(REGISTER_TICKS,
                    () -> arena.isRegistered(PathCache.INSTANCE, pos1) && arena.isRegistered(PathCache.INSTANCE, pos2), () -> {
                TileMarkerPath path = pathAt(helper, pos1);
                path.getCache().getSubCache(helper.getLevel()).tryConnect(arena.abs(pos1), arena.abs(pos2));
                assertPathConnected(path, arena.abs(pos2), "Diagonal Path line of sight failed through solid block!");
            }, SETTLE_TICKS, () -> {
                assertPathConnected(pathAt(helper, pos1), arena.abs(pos2), "Diagonal Path connection did not hold!");
                arena.finish();
            }, "Path markers never registered in the cache");
        });
    }

    public static void testVolumeTriangulation2D(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        arena.guard(() -> {
            BlockPos pos1 = new BlockPos(1, 2, 1);
            BlockPos pos2 = new BlockPos(5, 2, 1);
            BlockPos pos3 = new BlockPos(1, 2, 5);
            BlockPos corner = new BlockPos(5, 2, 5);
            arena.place(pos1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(pos2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(pos3, BCCoreBlocks.MARKER_VOLUME.get());

            arena.awaitThen(REGISTER_TICKS, () -> volumeRegistered(arena, pos1, pos2, pos3), () -> {
                TileMarkerVolume volume = volumeAt(helper, pos1);
                volume.onManualConnectionAttempt(null);
                assertBox(helper, volume, pos1, corner, "2D Triangulation failed!");
            }, SETTLE_TICKS, () -> {
                assertBox(helper, volumeAt(helper, pos1), pos1, corner, "2D Triangulation did not hold!");
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }

    public static void testVolumeTriangulation3D(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        arena.guard(() -> {
            BlockPos pos1 = new BlockPos(2, 2, 2);
            BlockPos pos2 = new BlockPos(5, 2, 2);
            BlockPos pos3 = new BlockPos(2, 5, 2);
            BlockPos pos4 = new BlockPos(2, 2, 5);
            BlockPos corner = new BlockPos(5, 5, 5);
            arena.place(pos1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(pos2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(pos3, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(pos4, BCCoreBlocks.MARKER_VOLUME.get());

            arena.awaitThen(REGISTER_TICKS, () -> volumeRegistered(arena, pos1, pos2, pos3, pos4), () -> {
                TileMarkerVolume volume = volumeAt(helper, pos1);
                volume.onManualConnectionAttempt(null);
                assertBox(helper, volume, pos1, corner, "3D Triangulation failed!");
            }, 2 * SETTLE_TICKS, () -> {
                assertBox(helper, volumeAt(helper, pos1), pos1, corner, "3D Triangulation did not hold!");
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }

    // ---------- Join resync: what a client that has just entered the level is sent ----------

    /** A client resets its marker cache whenever its level unloads, so the sync it is sent on joining has to carry
     *  everything: replaying it into the client table of a level the client built fresh must reproduce the server's
     *  markers and connection, and a later unload of that client level must leave nothing for the next world. */
    public static void testJoinSyncReplaysMarkersAndConnections(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        arena.guard(() -> {
            BlockPos pos1 = new BlockPos(1, 2, 1);
            BlockPos pos2 = new BlockPos(5, 2, 1);
            arena.place(pos1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(pos2, BCCoreBlocks.MARKER_VOLUME.get());

            arena.awaitThen(REGISTER_TICKS, () -> volumeRegistered(arena, pos1, pos2), () -> {
                volumeAt(helper, pos1).onManualConnectionAttempt(null);
                assertBox(helper, volumeAt(helper, pos1), pos1, pos2, "Precondition: the server did not connect the markers!");
            }, SETTLE_TICKS, () -> {
                BlockPos abs1 = arena.abs(pos1);
                BlockPos abs2 = arena.abs(pos2);
                List<MessageMarker> messages = VolumeCache.INSTANCE.getSubCache(helper.getLevel()).createJoinSyncMessages();

                // The joining client's level: same dimension key as this one, its own (client-side) table.
                Level client = FakeLevels.fake(true, helper.getLevel().dimension());
                MarkerCache.onWorldUnload(client);
                try {
                    VolumeSubCache joined = VolumeCache.INSTANCE.getSubCache(client);
                    for (MessageMarker message : messages) {
                        joined.handleMessageMain(message); // what MessageMarker.handle does for each packet
                    }
                    if (!joined.hasLoadedOrUnloadedMarker(abs1) || !joined.hasLoadedOrUnloadedMarker(abs2)) {
                        throw new IllegalStateException("The join sync did not carry both markers: " + messages);
                    }
                    VolumeConnection connection = joined.getConnection(abs1);
                    if (connection == null || !connection.getMarkerPositions().containsAll(List.of(abs1, abs2))) {
                        throw new IllegalStateException("The join sync did not carry the connection: " + messages);
                    }

                    // Leaving the world: nothing of it may be shown in the next one.
                    NeoForge.EVENT_BUS.post(new LevelEvent.Unload(client));
                    VolumeSubCache nextWorld = VolumeCache.INSTANCE.getSubCache(client);
                    if (nextWorld == joined || nextWorld.getConnection(abs1) != null || nextWorld.hasLoadedOrUnloadedMarker(abs1)) {
                        throw new IllegalStateException("The client cache outlived its level's unload");
                    }
                } finally {
                    MarkerCache.onWorldUnload(client);
                }
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }

    // ---------- Hermeticity: leftovers must not matter ----------

    /** Reproduces the 2026-09-26 flake on purpose: markers left behind in line with a test's own, exactly where
     *  neighbouring cells' tests keep theirs, one per scan direction (plus a cache-only "ghost" for an unloaded
     *  leftover and a path leftover). Without eviction the line-of-sight connection below pulls its box out to
     *  the nearest stray. */
    public static void testLeftoverMarkersDoNotCorruptConnections(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.openWithoutEviction(helper);
        arena.guard(() -> {
            BlockPos pos1 = new BlockPos(1, 2, 1);
            BlockPos pos2 = new BlockPos(5, 2, 1);
            BlockPos wallPos = new BlockPos(3, 2, 1);
            arena.place(pos1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(pos2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(wallPos, Blocks.STONE);

            Block volume = BCCoreBlocks.MARKER_VOLUME.get();
            List<Stray> strays = List.of(
                    arena.adopt(MarkerArenaUtil.plantStray(helper, new BlockPos(-5, 2, 1), volume)), // west: cell 6 over, its (1,2,1)
                    arena.adopt(MarkerArenaUtil.plantStray(helper, new BlockPos(1, 2, -6), volume)), // north: row 7 up, its (1,2,1)
                    arena.adopt(MarkerArenaUtil.plantStray(helper, new BlockPos(11, 2, 1), volume)), // east of pos2: its (5,2,1)
                    arena.adopt(MarkerArenaUtil.plantGhost(helper, volume, new BlockPos(1, 2, 8))), // south: unloaded, row 7 down
                    arena.adopt(MarkerArenaUtil.plantStray(helper, new BlockPos(2, 2, -6), BCCoreBlocks.MARKER_PATH.get())));

            arena.awaitThen(REGISTER_TICKS,
                    () -> volumeRegistered(arena, pos1, pos2)
                        && strays.stream().allMatch(stray -> stray.isRegistered(helper.getLevel())),
                    () -> {
                // The eviction open() performs, at the moment the leftovers are known to be in the caches.
                arena.evictStrays();
                TileMarkerVolume tile = volumeAt(helper, pos1);
                tile.onManualConnectionAttempt(null);
                assertBox(helper, tile, pos1, pos2, "A leftover marker corrupted the line-of-sight connection!");
                for (Stray stray : strays) {
                    if (!stray.isGone(helper.getLevel())) {
                        throw new IllegalStateException("evictStrays() left " + stray + " behind");
                    }
                }
            }, SETTLE_TICKS, () -> {
                assertBox(helper, volumeAt(helper, pos1), pos1, pos2, "Connection did not hold after the eviction!");
                arena.finish();
            }, "Leftover and own markers never all registered in the caches");
        });
    }

    /** {@link MarkerArenaUtil#open} is what the five marker tests rely on, so pin that it really evicts: the
     *  leftovers exist BEFORE the arena opens, as they do after an earlier batch. */
    public static void testOpenEvictsLeftoverMarkers(GameTestHelper helper) {
        Block volume = BCCoreBlocks.MARKER_VOLUME.get();
        Block path = BCCoreBlocks.MARKER_PATH.get();
        List<Stray> strays = List.of(
                MarkerArenaUtil.plantStray(helper, new BlockPos(-5, 2, 2), volume),
                MarkerArenaUtil.plantStray(helper, new BlockPos(12, 2, 3), path),
                MarkerArenaUtil.plantGhost(helper, volume, new BlockPos(2, 2, 15)),
                MarkerArenaUtil.plantGhost(helper, path, new BlockPos(3, 2, -9)));
        EntityArenaUtil.tickUntil(helper, REGISTER_TICKS,
                () -> strays.stream().allMatch(stray -> stray.isRegistered(helper.getLevel())), () -> {
            Session arena = MarkerArenaUtil.open(helper);
            strays.forEach(arena::adopt);
            arena.guard(() -> {
                for (Stray stray : strays) {
                    if (!stray.isGone(helper.getLevel())) {
                        throw new IllegalStateException("MarkerArenaUtil.open() left " + stray + " behind");
                    }
                }
                arena.finish();
            });
        }, "Leftover markers never registered in the caches");
    }

    /** A failing marker test must not leave its markers behind to fail its neighbours: {@code guard} cleans up
     *  and still rethrows. The marker is removed before it ever joined the cache (it would on the next level
     *  tick), so the witness placed afterwards pins that the removed one does not register late either. */
    public static void testSessionCleansUpWhenBodyThrows(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        BlockPos thrown = new BlockPos(2, 2, 2);
        BlockPos witness = new BlockPos(4, 2, 2);
        try {
            arena.guard(() -> {
                arena.place(thrown, BCCoreBlocks.MARKER_VOLUME.get());
                throw new IllegalStateException("simulated assertion failure");
            });
            throw new IllegalStateException("Session.guard swallowed the failure instead of rethrowing it");
        } catch (IllegalStateException e) {
            if (!"simulated assertion failure".equals(e.getMessage())) {
                arena.cleanup();
                throw e;
            }
        }
        arena.guard(() -> {
            if (!helper.getBlockState(thrown).isAir()) {
                throw new IllegalStateException("Session.guard did not remove the marker the failed body had placed");
            }
            arena.place(witness, BCCoreBlocks.MARKER_VOLUME.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(VolumeCache.INSTANCE, witness), () -> {
                // The witness joined the cache, so the level tick that would register a stale entry has run.
                if (VolumeCache.INSTANCE.getSubCache(helper.getLevel()).hasLoadedOrUnloadedMarker(arena.abs(thrown))) {
                    throw new IllegalStateException("A marker removed by Session.guard's cleanup still ended up in the cache");
                }
                arena.finish();
            }, "The witness marker never registered in the cache");
        });
    }

    // ---------- helpers ----------

    private static boolean volumeRegistered(Session arena, BlockPos... rels) {
        for (BlockPos rel : rels) {
            if (!arena.isRegistered(VolumeCache.INSTANCE, rel)) {
                return false;
            }
        }
        return true;
    }

    private static TileMarkerVolume volumeAt(GameTestHelper helper, BlockPos rel) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof TileMarkerVolume volume) {
            return volume;
        }
        throw new IllegalStateException("TileEntity at " + rel + " is not a TileMarkerVolume!");
    }

    private static TileMarkerPath pathAt(GameTestHelper helper, BlockPos rel) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof TileMarkerPath path) {
            return path;
        }
        throw new IllegalStateException("TileEntity at " + rel + " is not a TileMarkerPath!");
    }

    private static void assertBox(GameTestHelper helper, TileMarkerVolume volume, BlockPos expectedMin, BlockPos expectedMax,
                                  String what) {
        BlockPos min = helper.absolutePos(expectedMin);
        BlockPos max = helper.absolutePos(expectedMax);
        if (!volume.min().equals(min) || !volume.max().equals(max)) {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            throw new IllegalStateException(what + " Expected " + expectedMin.toShortString() + " to "
                    + expectedMax.toShortString() + ". Got: " + volume.min().subtract(origin).toShortString() + " to "
                    + volume.max().subtract(origin).toShortString() + " (all cell-relative)");
        }
    }

    private static void assertPathConnected(TileMarkerPath path, BlockPos expectedMember, String what) {
        if (path.getCurrentConnection() == null || !path.getCurrentConnection().getMarkerPositions().contains(expectedMember)) {
            throw new IllegalStateException(what);
        }
    }
}
