/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BCLog;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSubCache;

/**
 * Hermetic marker arenas for game tests.
 *
 * <p>Marker connections live in {@link VolumeCache}/{@link PathCache}: process-wide singletons keyed only by
 * dimension, shared by every game-test arena, scanned up to {@code markerMaxDistance} (64) blocks in a straight
 * line, and — crucially — counting <em>unloaded</em> markers too. A marker a test leaves behind therefore stays
 * a connection candidate and a line-of-sight obstacle for every later test whose markers share a row, column or
 * height with it, long after its own arena has stopped ticking and even after its chunk unloaded. The
 * {@code test_environment} trick (one environment per marker test) only stops two marker tests running
 * <em>concurrently</em>; the game-test runner never clears the grid between batches, so it does nothing about
 * leftovers. This class is the other half:
 * <ul>
 * <li><b>Eviction on open.</b> {@link #open} removes every marker already in the caches within scan range of the
 *     cell (blocks and cache-only "ghost" entries alike) before the test places its own. Safe because a marker
 *     test runs alone in its batch; any marker that exists then is somebody's leftover. Each eviction is logged
 *     at WARN so the polluter can be tracked down.</li>
 * <li><b>Cleanup on every exit.</b> {@link Session#finish} removes everything the test placed, and
 *     {@link Session#guard}/{@link Session#await} remove it again when the test body throws or a wait times out,
 *     so a failing marker test does not turn its neighbours into collateral failures.</li>
 * <li><b>Cell bounds.</b> {@link Session#place} refuses coordinates outside the 6x7 grid cell
 *     ({@code x 0..5}, {@code z 0..6}); a write past it lands in the neighbouring test's arena.</li>
 * <li><b>State gating.</b> A marker joins its cache on the first level tick after it is placed (block-entity
 *     {@code onLoad} is deferred to {@code tickBlockEntities}), so {@link Session#isRegistered} +
 *     {@link Session#awaitThen} replace the old {@code runAfterDelay(2)} guesses. The cell's chunks are
 *     force-loaded (5x5 around each corner) because the framework forces only the one chunk under the
 *     arena's north-west corner.</li>
 * </ul>
 *
 * <p>Scheduling note: everything is scheduled UP FRONT from the test body, never from inside a scheduled
 * runnable. {@code GameTestInfo} runs its runnables while iterating an open-addressing fastutil map, and an
 * insertion during that iteration can rehash it and skip or double-run entries.
 *
 * <p>NB: the {@link GameTestHelper} parameter is named {@code gth}, never {@code helper} (see
 * {@link EntityArenaUtil}: the pre-1.21.11 assertion rewrite is a textual regex on {@code helper} receivers).
 */
public final class MarkerArenaUtil {

    /** Highest in-cell x: the framework spaces {@code minecraft:empty} arenas 6 apart in X. */
    public static final int CELL_MAX_X = 5;
    /** Highest in-cell z: arenas are 7 apart in Z (z=7 is the NEXT ROW). */
    public static final int CELL_MAX_Z = 6;

    /** Slack added to {@code markerMaxDistance} when deciding which cache entries can still see the cell. */
    private static final int EVICT_MARGIN = 16;

    /** Positions deliberately planted by the hermeticity tests ({@link #plantStray}/{@link #plantGhost}); their
     *  eviction is expected and logged at INFO, so a WARN always means a real polluter. */
    private static final Set<BlockPos> PLANTED = ConcurrentHashMap.newKeySet();

    private MarkerArenaUtil() {}

    /** True if the arena-relative position lies in the test's own grid cell (height is unconstrained). */
    public static boolean isInsideCell(BlockPos rel) {
        return rel.getX() >= 0 && rel.getX() <= CELL_MAX_X && rel.getZ() >= 0 && rel.getZ() <= CELL_MAX_Z;
    }

    /** Opens a session on the test's cell: force-loads it and evicts every leftover marker in scan range. */
    public static Session open(GameTestHelper gth) {
        Session session = openWithoutEviction(gth);
        session.evictStrays();
        return session;
    }

    /** As {@link #open} but leaves existing markers alone. Only for tests that plant their own leftovers and
     *  then call {@link Session#evictStrays} at a moment of their choosing. */
    public static Session openWithoutEviction(GameTestHelper gth) {
        forceLoadCell(gth);
        return new Session(gth);
    }

    /** Force-loads the chunks under all four corners of the cell (5x5 each, see
     *  {@link EntityArenaUtil#forceLoadEntityArena}). The framework forces only the arena's north-west chunk,
     *  and the grid drops at a random world position, so a cell often straddles a chunk border. */
    private static void forceLoadCell(GameTestHelper gth) {
        Set<Long> seen = new HashSet<>();
        for (int x : new int[] { 0, CELL_MAX_X }) {
            for (int z : new int[] { 0, CELL_MAX_Z }) {
                BlockPos rel = new BlockPos(x, 0, z);
                BlockPos abs = gth.absolutePos(rel);
                // Own chunk key: ChunkPos.asLong/pack is renamed across the node lines.
                long chunkKey = ((long) (abs.getX() >> 4) << 32) ^ ((abs.getZ() >> 4) & 0xFFFFFFFFL);
                if (seen.add(chunkKey)) {
                    EntityArenaUtil.forceLoadEntityArena(gth, rel);
                }
            }
        }
    }

    /** Removes any marker block at the given arena-relative positions. For game tests that are NOT marker tests
     *  but place a marker anyway (waterlogging, pumps, schematics): a marker left in the shared grid stays a
     *  connection candidate for every later marker test in scan range, so remove it before the test ends — in a
     *  {@code finally} for synchronous tests, as the last step of the assertion for {@code succeedWhen} ones.
     *  Harmless at positions that never held one. */
    public static void clearMarkers(GameTestHelper gth, BlockPos... rels) {
        for (BlockPos rel : rels) {
            Block block = gth.getBlockState(rel).getBlock();
            if (block == BCCoreBlocks.MARKER_VOLUME.get() || block == BCCoreBlocks.MARKER_PATH.get()) {
                gth.setBlock(rel, Blocks.AIR);
            }
        }
    }

    private static List<MarkerCache<?>> caches() {
        return List.of(VolumeCache.INSTANCE, PathCache.INSTANCE);
    }

    private static MarkerCache<?> cacheFor(Block marker) {
        if (marker == BCCoreBlocks.MARKER_VOLUME.get()) {
            return VolumeCache.INSTANCE;
        }
        if (marker == BCCoreBlocks.MARKER_PATH.get()) {
            return PathCache.INSTANCE;
        }
        throw new IllegalArgumentException(marker + " is not a connecting marker block");
    }

    /** A marker left outside a test's cell, standing in for what an earlier batch's test leaves behind. */
    public static final class Stray {
        private final MarkerCache<?> cache;
        private final BlockPos abs;
        private final boolean ghost;
        /** Absolute position of the block this marker stands on (a path marker pops without one), or null. */
        private final BlockPos support;

        private Stray(MarkerCache<?> cache, BlockPos abs, boolean ghost, BlockPos support) {
            this.cache = cache;
            this.abs = abs;
            this.ghost = ghost;
            this.support = support;
        }

        public BlockPos abs() {
            return abs;
        }

        /** True once the marker is in its cache with a live tile (a block stray needs a level tick for that; a
         *  ghost is cache-only and registers at once). */
        public boolean isRegistered(ServerLevel level) {
            MarkerSubCache<?> sub = cache.getSubCache(level);
            return ghost ? sub.hasLoadedOrUnloadedMarker(abs) : sub.getMarker(abs) != null;
        }

        /** True once neither the cache nor the world remembers this marker. */
        public boolean isGone(ServerLevel level) {
            return !cache.getSubCache(level).hasLoadedOrUnloadedMarker(abs)
                && (ghost || level.getBlockState(abs).isAir());
        }

        void remove(ServerLevel level) {
            if (!ghost) {
                level.setBlock(abs, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            MarkerSubCache<?> sub = cache.getSubCache(level);
            if (sub.hasLoadedOrUnloadedMarker(abs)) {
                sub.removeMarker(abs);
            }
            if (support != null) {
                level.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        @Override
        public String toString() {
            return (ghost ? "ghost " : "") + cache.name + " marker at " + abs;
        }
    }

    /** Plants a real marker block at an arena-relative position (normally outside the cell) and force-loads its
     *  chunks so it registers and stays registered. */
    public static Stray plantStray(GameTestHelper gth, BlockPos rel, Block marker) {
        MarkerCache<?> cache = cacheFor(marker);
        EntityArenaUtil.forceLoadEntityArena(gth, rel);
        BlockPos support = null;
        if (marker == BCCoreBlocks.MARKER_PATH.get()) {
            // A path marker pops (and leaves the cache) without a block to stand on; volume markers float.
            gth.setBlock(rel.below(), Blocks.STONE);
            support = gth.absolutePos(rel.below());
        }
        gth.setBlock(rel, marker);
        BlockPos abs = gth.absolutePos(rel).immutable();
        PLANTED.add(abs);
        return new Stray(cache, abs, false, support);
    }

    /** Plants a cache-only entry: a marker the cache remembers as unloaded with no block behind it, which is
     *  what a leftover looks like once its chunk has unloaded. */
    public static Stray plantGhost(GameTestHelper gth, Block marker, BlockPos rel) {
        MarkerCache<?> cache = cacheFor(marker);
        BlockPos abs = gth.absolutePos(rel).immutable();
        PLANTED.add(abs);
        cache.getSubCache(gth.getLevel()).unloadMarker(abs);
        return new Stray(cache, abs, true, null);
    }

    /** One test's claim on its cell. See the class javadoc for the guarantees. */
    public static final class Session {
        private final GameTestHelper gth;
        /** Everything this session placed, in arena-relative coordinates. */
        private final List<BlockPos> placed = new ArrayList<>();
        private final List<Stray> strays = new ArrayList<>();

        private Session(GameTestHelper gth) {
            this.gth = gth;
        }

        public BlockPos abs(BlockPos rel) {
            return gth.absolutePos(rel);
        }

        /** Cell-relative form of an absolute position. Not {@code GameTestHelper.relativePos}: vanilla inverts the
         *  rotation with {@code getRotated(CLOCKWISE_180)}, which turns an unrotated arena's (1,2,1) into (-1,2,-1). */
        public BlockPos rel(BlockPos abs) {
            return abs.subtract(gth.absolutePos(BlockPos.ZERO));
        }

        /** Places a block inside the cell and remembers it for cleanup. */
        public void place(BlockPos rel, BlockState state) {
            if (!isInsideCell(rel)) {
                throw new IllegalArgumentException("Marker test wrote to " + rel + ", outside its own grid cell (x 0.."
                    + CELL_MAX_X + ", z 0.." + CELL_MAX_Z + "): that is the next test's arena");
            }
            gth.setBlock(rel, state);
            placed.add(rel.immutable());
        }

        public void place(BlockPos rel, Block block) {
            place(rel, block.defaultBlockState());
        }

        /** Clears a cell block (the cleanup list keeps it, which is harmless). */
        public void remove(BlockPos rel) {
            gth.setBlock(rel, Blocks.AIR);
        }

        /** Takes responsibility for removing a planted stray when the session cleans up. */
        public Stray adopt(Stray stray) {
            strays.add(stray);
            return stray;
        }

        /** True once the marker at {@code rel} is registered in {@code cache} with a live tile. */
        public boolean isRegistered(MarkerCache<?> cache, BlockPos rel) {
            return cache.getSubCache(gth.getLevel()).getMarker(abs(rel)) != null;
        }

        /** Removes every marker in either cache within scan range of the cell that this session did not place,
         *  and returns how many it removed. */
        public int evictStrays() {
            ServerLevel level = gth.getLevel();
            Set<BlockPos> own = new HashSet<>();
            for (BlockPos rel : placed) {
                own.add(abs(rel));
            }
            BlockPos centre = abs(new BlockPos(CELL_MAX_X / 2, 2, CELL_MAX_Z / 2));
            int reach = BCCoreConfig.markerMaxDistance.get() + EVICT_MARGIN;
            int evicted = 0;
            for (MarkerCache<?> cache : caches()) {
                MarkerSubCache<?> sub = cache.getSubCache(level);
                for (BlockPos pos : sub.getAllMarkers()) {
                    if (own.contains(pos) || Math.abs(pos.getX() - centre.getX()) > reach
                        || Math.abs(pos.getY() - centre.getY()) > reach || Math.abs(pos.getZ() - centre.getZ()) > reach) {
                        continue;
                    }
                    if (PLANTED.remove(pos)) {
                        BCLog.logger.info("[marker-arena] evicting the planted {} marker at {} (cell-relative {})",
                            cache.name, pos, rel(pos));
                    } else {
                        BCLog.logger.warn("[marker-arena] evicting a leftover {} marker at {} (cell-relative {}); some"
                            + " earlier test did not clean up", cache.name, pos, rel(pos));
                    }
                    if (sub.getMarker(pos) != null) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    if (sub.hasLoadedOrUnloadedMarker(pos)) {
                        sub.removeMarker(pos);
                    }
                    evicted++;
                }
            }
            return evicted;
        }

        /** Removes everything the session placed or adopted. Idempotent and never throws. */
        public void cleanup() {
            ServerLevel level = gth.getLevel();
            for (BlockPos rel : placed) {
                try {
                    gth.setBlock(rel, Blocks.AIR);
                } catch (RuntimeException e) {
                    BCLog.logger.warn("[marker-arena] cleanup could not clear {}", rel, e);
                }
            }
            placed.clear();
            for (Stray stray : strays) {
                try {
                    stray.remove(level);
                } catch (RuntimeException e) {
                    BCLog.logger.warn("[marker-arena] cleanup could not remove {}", stray, e);
                }
            }
            strays.clear();
        }

        /** Cleans up, then passes the test. */
        public void finish() {
            cleanup();
            gth.succeed();
        }

        /** Runs {@code body}; if it throws, cleans up and rethrows so the framework still records the failure. */
        public void guard(Runnable body) {
            try {
                body.run();
            } catch (RuntimeException e) {
                cleanup();
                throw e;
            }
        }

        /** Polls once per tick for up to {@code maxTicks} for {@code condition}, then runs {@code then}. Fails
         *  (after cleaning up) with {@code failureMessage} plus a dump of the nearby marker caches if the
         *  condition never comes true. */
        public void await(int maxTicks, BooleanSupplier condition, Runnable then, Supplier<String> failureMessage) {
            awaitThen(maxTicks, condition, then, 0, null, failureMessage);
        }

        public void await(int maxTicks, BooleanSupplier condition, Runnable then, String failureMessage) {
            await(maxTicks, condition, then, () -> failureMessage);
        }

        /** Two-phase {@link #await}: runs {@code first} on the tick {@code condition} comes true and
         *  {@code second} {@code gapTicks} ticks later. {@code second} may be null. Every poll is scheduled up
         *  front (see the class javadoc), and every exit path — an exception from either phase, or a timeout —
         *  cleans up. */
        public void awaitThen(int maxTicks, BooleanSupplier condition, Runnable first, int gapTicks, Runnable second,
                              Supplier<String> failureMessage) {
            int[] firstFiredAt = { -1 };
            boolean[] done = { false };
            for (int t = 1; t <= maxTicks; t++) {
                final int tick = t;
                gth.runAfterDelay(tick, () -> {
                    if (done[0]) {
                        return;
                    }
                    if (firstFiredAt[0] < 0) {
                        boolean ready;
                        try {
                            ready = condition.getAsBoolean();
                        } catch (RuntimeException e) {
                            done[0] = true;
                            cleanup();
                            throw e;
                        }
                        if (ready) {
                            firstFiredAt[0] = tick;
                            done[0] = second == null;
                            guard(first);
                        } else if (tick + gapTicks >= maxTicks) {
                            done[0] = true;
                            String message = failureMessage.get() + " (waited " + (maxTicks - gapTicks) + " ticks). "
                                + describeCaches();
                            cleanup();
                            gth.fail(message);
                        }
                    } else if (tick >= firstFiredAt[0] + gapTicks) {
                        done[0] = true;
                        guard(second);
                    }
                });
            }
        }

        public void awaitThen(int maxTicks, BooleanSupplier condition, Runnable first, int gapTicks, Runnable second,
                              String failureMessage) {
            awaitThen(maxTicks, condition, first, gapTicks, second, () -> failureMessage);
        }

        private static String shortPos(BlockPos pos) {
            return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
        }

        /** Every marker either cache holds within scan range, in cell-relative coordinates: the first thing to
         *  read when a marker test reports a box that is bigger or smaller than it placed. */
        public String describeCaches() {
            ServerLevel level = gth.getLevel();
            Set<BlockPos> own = new HashSet<>();
            for (BlockPos rel : placed) {
                own.add(abs(rel));
            }
            BlockPos centre = abs(new BlockPos(CELL_MAX_X / 2, 2, CELL_MAX_Z / 2));
            int reach = BCCoreConfig.markerMaxDistance.get() + EVICT_MARGIN;
            StringBuilder sb = new StringBuilder("Marker caches near the cell (cell-relative; * = placed by this test):");
            for (MarkerCache<?> cache : caches()) {
                MarkerSubCache<?> sub = cache.getSubCache(level);
                sb.append(' ').append(cache.name).append('[');
                boolean any = false;
                for (BlockPos pos : sub.getAllMarkers()) {
                    if (Math.abs(pos.getX() - centre.getX()) > reach || Math.abs(pos.getY() - centre.getY()) > reach
                        || Math.abs(pos.getZ() - centre.getZ()) > reach) {
                        continue;
                    }
                    sb.append(any ? ", " : "").append(own.contains(pos) ? "*" : "").append(shortPos(rel(pos)))
                        .append(sub.getMarker(pos) == null ? " unloaded" : "");
                    any = true;
                }
                sb.append(']');
            }
            return sb.toString();
        }
    }
}
