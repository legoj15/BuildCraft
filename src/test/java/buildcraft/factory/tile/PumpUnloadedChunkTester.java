/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import buildcraft.api.mj.MjAPI;
import buildcraft.factory.BCFactoryBlocks;

/**
 * The pump must never load a chunk: neither its flood fill nor the block update of a bucket it drains.
 * {@code Level.getBlockState} on a chunk that is not in memory loads (or generates) it synchronously on the server
 * thread. The fill probes every cell around every fluid cell out to {@code pumpMaxDistance} blocks, so a pool beside
 * the edge of the loaded area made each rebuild load the chunk next door; and draining a cell on a chunk's border
 * sets it to air, whose neighbour updates read the cell across the border. The pump now treats an absent chunk as out
 * of reach (a pool in it is not pumped until it is loaded) and leaves a border cell alone while the chunk across the
 * border is absent. An idle pump looks for the chunk to load, instead of waiting out its back-off.
 *
 * <p>Layout: one chunk, far from the arena and from every other test's far chunk, is force-loaded alone, so its four
 * neighbours stay absent. A pump stands in it over a row of fluid that runs east to the chunk's last column, whose
 * east neighbour is the absent chunk. The fluid is written straight into the chunk section: placing a fluid block
 * runs {@code LiquidBlock.onPlace}, which reads its horizontal neighbours and would load the chunk before the pump
 * ever looked. The pump is driven by calling its own {@code serverTick} with power, so no world tick runs (and no
 * tick loads the neighbour: the test ends before one does).
 */
public class PumpUnloadedChunkTester {

    /** Chunks east and south of the arena's: clear of {@code OilGenStructureTester}'s isolated chunk (777). */
    private static final int CHUNK_OFFSET = 888;
    /** The row runs from the pump's column east to local x 15, the chunk's last column, at local z 8. */
    private static final int EDGE_X = 15;
    private static final int LOCAL_Z = 8;

    /** Long enough for an idle pump's rebuilds to back off to their cap. */
    private static final int IDLE_TICKS = 1500;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    /** The isolated chunk, a row of fluid and the pump over its first cell; {@link #close} takes it all down again. */
    private static final class Rig implements AutoCloseable {
        final ServerLevel level;
        final int cx;
        final int cz;
        final int poolY;
        final int rowFromX;
        final BlockState seed;
        final BlockPos pumpPos;
        final BlockPos edge;
        LevelChunk chunk;
        TilePump pump;
        boolean eastForced;

        /** @param pumpX local x of the pump, over the row's first cell, which holds {@code seed}; the other cells of the
         *               row, out to the chunk's edge, are water */
        Rig(GameTestHelper helper, int pumpX, BlockState seed) {
            level = helper.getLevel();
            rowFromX = pumpX;
            this.seed = seed;
            BlockPos arena = helper.absolutePos(BlockPos.ZERO);
            cx = (arena.getX() >> 4) + CHUNK_OFFSET;
            cz = (arena.getZ() >> 4) + CHUNK_OFFSET;
            poolY = level.getMinY() + 40;
            pumpPos = new BlockPos(cx * 16 + pumpX, poolY + 4, cz * 16 + LOCAL_Z);
            edge = new BlockPos(cx * 16 + EDGE_X, poolY, cz * 16 + LOCAL_Z);

            level.setChunkForced(cx, cz, true);
        }

        /** Builds the row and the pump. Apart from the constructor, so a failed precondition still reaches
         *  {@link #close} with whatever was built. */
        Rig build() {
            chunk = level.getChunkSource().getChunkNow(cx, cz);
            check(chunk != null, "precondition: the isolated chunk must be force-generated to FULL");
            check(eastAbsent(), "precondition: its east neighbour must stay absent, or the chunk is not at the edge"
                    + " of anything");

            // The far chunk is real worldgen, so this height may be solid rock: open the pump's column down to the pool.
            for (int y = poolY + 1; y < pumpPos.getY(); y++) {
                setRaw(new BlockPos(pumpPos.getX(), y, pumpPos.getZ()), Blocks.AIR.defaultBlockState());
            }
            for (int x = rowFromX; x <= EDGE_X; x++) {
                setRaw(new BlockPos(cx * 16 + x, poolY, cz * 16 + LOCAL_Z),
                        x == rowFromX ? seed : Blocks.WATER.defaultBlockState());
            }
            level.setBlock(pumpPos, BCFactoryBlocks.PUMP.get().defaultBlockState(), 3);
            pump = (TilePump) level.getBlockEntity(pumpPos);
            check(pump != null, "the pump must have a block entity");
            check(eastAbsent(), "precondition: building the rig must not have loaded the east neighbour itself");
            return this;
        }

        boolean eastAbsent() {
            return level.getChunkSource().getChunkNow(cx + 1, cz) == null;
        }

        private void setRaw(BlockPos pos, BlockState state) {
            chunk.getSection(level.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15,
                    pos.getZ() & 15, state);
        }

        /** Runs the pump for {@code ticks} of its own ticks, each with power for a bucket. */
        void runPowered(int ticks) {
            for (int i = 0; i < ticks; i++) {
                pump.getBattery().addPowerChecking(10 * MjAPI.MJ, false);
                pump.serverTick();
            }
        }

        @Override
        public void close() {
            if (pump != null) {
                pump.onRemove(); // the tube column
                level.setBlock(pumpPos, Blocks.AIR.defaultBlockState(), 3);
            }
            if (chunk != null) {
                for (int x = rowFromX; x <= EDGE_X; x++) {
                    setRaw(new BlockPos(cx * 16 + x, poolY, cz * 16 + LOCAL_Z), Blocks.AIR.defaultBlockState());
                }
            }
            if (eastForced) {
                level.setChunkForced(cx + 1, cz, false);
            }
            level.setChunkForced(cx, cz, false);
        }
    }

    /**
     * Fill and drain beside an absent chunk. The first tick builds the fill: it walks the whole row to the chunk's
     * edge, finds the chunk across it absent and leaves it alone. Then the pump is powered and drains the row: the
     * cell on the border stays (draining it would update the absent chunk's side of the border), and the absent chunk
     * is still absent when the pump has done all it will do.
     */
    public static void fillStopsAtAnUnloadedChunkWithoutLoadingIt(GameTestHelper helper) {
        Rig rig = null;
        try {
            rig = new Rig(helper, 8, Blocks.WATER.defaultBlockState());
            rig.build();
            rig.pump.serverTick(); // the first tick builds the queue, then picks its first cell

            check(rig.pump.foundAbsent(rig.cx + 1, rig.cz),
                    "liveness: the fill must have walked the row to the chunk's edge and met the absent chunk beyond it");
            check(rig.eastAbsent(),
                    "the flood fill must not load the chunk beside the pool: a cell in an absent chunk is out of"
                            + " reach, not something to read");

            rig.runPowered(300);

            check(rig.pump.getTank().getAmountMb(0) > 0, "liveness: the powered pump must have drained the row");
            check(rig.level.getFluidState(rig.edge).isSource(),
                    "the cell on the border must be left alone while the chunk across it is absent");
            check(rig.eastAbsent(),
                    "draining a cell must not load the chunk beside it: the block update of a cell on a chunk's"
                            + " border reads the cell across the border");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            if (rig != null) {
                rig.close();
            }
        }
    }

    /**
     * An idle pump finds fluid in the chunk it left out once that chunk loads. The pump sits over a seagrass cell (wet,
     * so the fill starts, but a bucket cannot take it) with one water cell beside it on the chunk's border, which is
     * not queued while the chunk across the border is absent: the pump is idle and its rebuilds back off to their cap.
     * The chunk then loads, with no neighbour update or column change to wake the pump: it must notice within a look
     * period ({@link TilePump#REBUILD_DELAY} ticks), not wait out the back-off of up to
     * {@link TilePump#MAX_IDLE_REBUILD_DELAY}.
     */
    public static void idlePumpFindsFluidOnceTheChunkBesideItLoads(GameTestHelper helper) {
        Rig rig = null;
        try {
            rig = new Rig(helper, EDGE_X - 1, Blocks.SEAGRASS.defaultBlockState());
            rig.build();
            rig.runPowered(IDLE_TICKS);
            check(rig.pump.getTank().getAmountMb(0) == 0,
                    "precondition: nothing is takeable while the chunk beside the pool is absent");
            check(rig.level.getFluidState(rig.edge).isSource(), "precondition: the border cell is still water");
            check(rig.pump.foundAbsent(rig.cx + 1, rig.cz), "precondition: the fill must have met the absent chunk");
            check(rig.eastAbsent(), "precondition: the chunk beside the pool must still be absent");

            // Load the chunk just after a rebuild, so the next scheduled one is the whole capped wait away.
            int seen = rig.pump.rebuildCount;
            for (int i = 0; i < TilePump.MAX_IDLE_REBUILD_DELAY + 50 && rig.pump.rebuildCount == seen; i++) {
                rig.runPowered(1);
            }
            check(rig.pump.rebuildCount > seen,
                    "precondition: an idle pump must rebuild again within the longest back-off");
            rig.level.setChunkForced(rig.cx + 1, rig.cz, true); // loads it at once
            rig.eastForced = true;
            check(!rig.eastAbsent(), "precondition: the chunk beside the pool is loaded now");

            int limit = TilePump.REBUILD_DELAY + 20;
            int ticks = 0;
            while (ticks < limit && rig.pump.getTank().getAmountMb(0) == 0) {
                rig.runPowered(1);
                ticks++;
            }
            check(rig.pump.getTank().getAmountMb(0) > 0,
                    "an idle pump took more than " + limit + " ticks to take the border cell once the chunk beside it"
                            + " loaded (its back-off waits up to " + TilePump.MAX_IDLE_REBUILD_DELAY + ")");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            if (rig != null) {
                rig.close();
            }
        }
    }
}
