/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.mj.MjAPI;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.FarChunk;

/**
 * The pump must never load a chunk: neither its flood fill nor the block update of a bucket it drains.
 * {@code Level.getBlockState} on a chunk that is not in memory loads (or generates) it synchronously on the server
 * thread. The fill probes every cell around every fluid cell out to {@code pumpMaxDistance} blocks, so a pool beside
 * the edge of the loaded area made each rebuild load the chunk next door; and draining a cell on a chunk's border
 * sets it to air, whose neighbour updates read the cell across the border. The pump now treats an absent chunk as out
 * of reach (a pool in it is not pumped until it is loaded) and leaves a border cell alone while the chunk across the
 * border is absent. An idle pump looks for the chunk to load, instead of waiting out its back-off.
 *
 * <p>Layout: one chunk, far from the arena and from every other test's far chunk, is loaded alone
 * ({@link FarChunk}: loaded by {@code getChunk}, whose ticket does not reach the chunks around it, where a forced
 * chunk's does), so its four neighbours are absent however the machine schedules chunk generation. A pump stands in
 * it over a row of fluid that runs east to the chunk's last column, whose east neighbour is the absent chunk. The
 * fluid is written straight into the chunk section: placing a fluid block runs {@code LiquidBlock.onPlace}, which
 * reads its horizontal neighbours and would load the chunk before the pump ever looked. The pump is driven by
 * calling its own {@code serverTick} with power, so no world tick runs (and no tick loads the neighbour: the test
 * ends before one does).
 *
 * <p>The block update of a drain reaches further than the cell beside it. A neighbour of the drained cell that reads
 * redstone power ({@code hasNeighborSignal}: a lamp, a piston, a door, a rail) reads the cells around itself, and for
 * a solid one the direct signal of the cells around that, so a read lands three blocks from the drained cell. The
 * pump therefore also leaves alone every cell within three blocks of a chunk border while a chunk in that reach is
 * absent. The two reach tests put a lamp where the drain's update gets it and let the read cross the border, in the
 * straight direction and through a corner into the diagonal chunk.
 *
 * <p>The check is made again when the cell is drained, not only when it is queued: a chunk beside a queued cell can
 * unload in between. That test is the one that spans ticks, so its chunk is held by a ticket of its own
 * ({@link FarChunk#hold}) while the chunk beside it, loaded for a single tick, is left to unload.
 */
public class PumpUnloadedChunkTester {

    /** Chunks east and south of the arena's: clear of {@code OilGenStructureTester}'s isolated chunk (777). Every test
     *  of this class has an offset of its own, at least 8 apart (see {@link FarChunk}). */
    private static final int CHUNK_OFFSET = 888;
    /** The row runs from the pump's column east to local x 15, the chunk's last column, at local z 8. */
    private static final int EDGE_X = 15;
    private static final int LOCAL_Z = 8;

    /** The other tests' offsets, apart from {@link #CHUNK_OFFSET} and from each other: a test ends the moment its rig
     *  is closed, so a chunk of the one before may not have unloaded yet, and it must not sit next to this test's
     *  absent chunk. */
    private static final int REACH_OFFSET = 999;
    private static final int CORNER_OFFSET = 1111;
    private static final int IDLE_OFFSET = 1222;
    private static final int UNLOAD_OFFSET = 1333;

    /** How long the test of a chunk that unloads waits for the unload, which the server does in a few ticks. */
    private static final int UNLOAD_TICKS = 100;

    /** Long enough for an idle pump's rebuilds to back off to their cap. */
    private static final int IDLE_TICKS = 1500;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    /** The isolated chunk, a row of fluid and the pump over its first cell; {@link #close} takes it all down again.
     *  The pump stands at least two blocks from a chunk border it must not read across (see
     *  {@link PumpUnloadedChunkTester#idlePumpFindsFluidOnceTheChunkBesideItLoads}). */
    private static final class Rig implements AutoCloseable {
        final ServerLevel level;
        final FarChunk chunk;
        final int cx;
        final int cz;
        final int poolY;
        final int localZ;
        final int rowFromX;
        final BlockState seed;
        final BlockPos pumpPos;
        final BlockPos edge;
        /** The last cell of the row that holds water: the chunk's edge unless {@link #rowEnds} says otherwise. */
        int rowToX = EDGE_X;
        /** The chunk, as an offset from the isolated one, that must stay absent: the east one unless
         *  {@link #watch} says otherwise. */
        int watchDx = 1;
        int watchDz = 0;
        /** Whether the watched chunk is absent when the rig is built; false for the test that loads it on purpose. */
        boolean watchedAbsent = true;
        final boolean held;
        final List<BlockPos> placed = new ArrayList<>();
        final List<BlockState> placedStates = new ArrayList<>();
        TilePump pump;

        /** @param pumpX local x of the pump, over the row's first cell, which holds {@code seed}; the other cells of the
         *               row, out to the chunk's edge, are water */
        Rig(GameTestHelper helper, int pumpX, BlockState seed) {
            this(helper, pumpX, seed, CHUNK_OFFSET, LOCAL_Z);
        }

        /** @param chunkOffset chunks east and south of the arena's, where the isolated chunk is
         *  @param localZ the row's z within the chunk */
        Rig(GameTestHelper helper, int pumpX, BlockState seed, int chunkOffset, int localZ) {
            this(helper, pumpX, seed, chunkOffset, localZ, false);
        }

        /** @param held keep the isolated chunk loaded across ticks ({@link FarChunk#hold}) instead of for this tick
         *             alone; {@link #close} lets it go */
        Rig(GameTestHelper helper, int pumpX, BlockState seed, int chunkOffset, int localZ, boolean held) {
            level = helper.getLevel();
            rowFromX = pumpX;
            this.seed = seed;
            this.localZ = localZ;
            this.held = held;
            chunk = held ? FarChunk.hold(helper, chunkOffset) : FarChunk.load(helper, chunkOffset);
            cx = chunk.chunkX();
            cz = chunk.chunkZ();
            poolY = level.getMinY() + 40;
            pumpPos = chunk.pos(pumpX, poolY + 4, localZ);
            edge = cell(EDGE_X);
        }

        /** The cell of the row at this local x. */
        BlockPos cell(int localX) {
            return chunk.pos(localX, poolY, localZ);
        }

        /** Ends the water at this local x, short of the chunk's edge. */
        void rowEnds(int toX) {
            rowToX = toX;
        }

        /** Puts {@code state} on the row's level at this x and z, counted from the isolated chunk's corner (so 16 is
         *  the east chunk's first column), when the rig is built. */
        void place(int relX, int relZ, BlockState state) {
            placed.add(chunk.pos(relX, poolY, relZ));
            placedStates.add(state);
        }

        /** Loads the chunk beside the isolated one at this offset, at once (and only it: see {@link FarChunk}). */
        void alsoLoad(int dx, int dz) {
            chunk.loadBeside(dx, dz);
        }

        /** Makes this chunk, beside the isolated one, the one that must stay absent. */
        void watch(int dx, int dz) {
            watchDx = dx;
            watchDz = dz;
        }

        /** The east chunk is loaded when the rig is built, and only the pump's drain of a cell near it, once it has
         *  unloaded, is under test. */
        void watchLoadedEastChunk() {
            watchedAbsent = false;
        }

        /** Builds the row and the pump. Apart from the constructor, so a failed precondition still reaches
         *  {@link #close} with whatever was built. */
        Rig build() {
            check(!watchedAbsent || absent(watchDx, watchDz), "precondition: the chunk at offset (" + watchDx + ", "
                    + watchDz + ") must stay absent, or the chunk is not at the edge of anything");

            // The far chunk is real worldgen, so this height may be solid rock: open the pump's column down to the pool.
            for (int y = poolY + 1; y < pumpPos.getY(); y++) {
                setRaw(new BlockPos(pumpPos.getX(), y, pumpPos.getZ()), Blocks.AIR.defaultBlockState());
            }
            for (int x = rowFromX; x <= rowToX; x++) {
                setRaw(cell(x), x == rowFromX ? seed : Blocks.WATER.defaultBlockState());
            }
            for (int i = 0; i < placed.size(); i++) {
                setRaw(placed.get(i), placedStates.get(i));
            }
            level.setBlock(pumpPos, BCFactoryBlocks.PUMP.get().defaultBlockState(), 3);
            pump = (TilePump) level.getBlockEntity(pumpPos);
            check(pump != null, "the pump must have a block entity");
            check(!watchedAbsent || absent(watchDx, watchDz),
                    "precondition: building the rig must not have loaded the watched chunk itself");
            return this;
        }

        boolean eastAbsent() {
            return absent(1, 0);
        }

        /** Whether the chunk at this offset from the isolated one is out of memory. */
        boolean absent(int dx, int dz) {
            return chunk.isAbsent(dx, dz);
        }

        private void setRaw(BlockPos pos, BlockState state) {
            chunk.setRaw(pos, state);
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
            for (int x = rowFromX; x <= EDGE_X; x++) {
                chunk.clearRaw(cell(x));
            }
            for (BlockPos pos : placed) {
                chunk.clearRaw(pos);
            }
            if (held) {
                chunk.release();
            }
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
     * so the fill starts, but a bucket cannot take it) with two water cells beside it, the last two before the chunk's
     * border, which are not queued while the chunk across the border is absent: the pump is idle and its rebuilds back
     * off to their cap. The chunk then loads, with no neighbour update or column change to wake the pump: it must
     * notice within a look period ({@link TilePump#REBUILD_DELAY} ticks), not wait out the back-off of up to
     * {@link TilePump#MAX_IDLE_REBUILD_DELAY}.
     *
     * <p>The pump stands two blocks in from the border, not one: a block entity's {@code setChanged} (this one's runs
     * every ten ticks, on a tick of its own random choosing, so in about one run in ten) lets vanilla tell the
     * comparators around it, and past a solid neighbour that reads the cell beyond with no chunk check. A pump
     * against the border loaded the chunk beside it that way and then took the border cell.
     */
    public static void idlePumpFindsFluidOnceTheChunkBesideItLoads(GameTestHelper helper) {
        Rig rig = null;
        try {
            rig = new Rig(helper, EDGE_X - 2, Blocks.SEAGRASS.defaultBlockState(), IDLE_OFFSET, LOCAL_Z);
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
            rig.alsoLoad(1, 0); // loads it at once
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

    /**
     * A cell one block in from the border still reaches across it. The row ends at local x 13 with a redstone lamp
     * at 14 and stone at 15: draining the cell at 13 sends the lamp a block update, the lamp asks for the signal of
     * the stone beside it, and a solid block's signal is the direct signal of the cells around it, which reads the
     * east chunk's first column. The border column alone is not enough of a guard: the pump must leave the cell at 13
     * (three blocks from the border) while the east chunk is absent, and take every cell before it.
     */
    public static void drainThreeBlocksFromAnAbsentChunkNeverReadsIntoIt(GameTestHelper helper) {
        Rig rig = null;
        try {
            rig = new Rig(helper, 8, Blocks.WATER.defaultBlockState(), REACH_OFFSET, LOCAL_Z);
            rig.rowEnds(EDGE_X - 2);
            rig.place(EDGE_X - 1, LOCAL_Z, Blocks.REDSTONE_LAMP.defaultBlockState());
            rig.place(EDGE_X, LOCAL_Z, Blocks.STONE.defaultBlockState());
            rig.build();
            rig.pump.serverTick(); // the first tick builds the queue: only it sees the whole row, drained or not
            boolean noted = rig.pump.foundAbsent(rig.cx + 1, rig.cz);

            rig.runPowered(300);

            check(rig.pump.getTank().getAmountMb(0) > 0, "liveness: the powered pump must have drained the row");
            check(rig.level.getFluidState(rig.cell(EDGE_X - 3)).isEmpty(),
                    "liveness: the cell four blocks from the border must be drained: it is out of every reach");
            check(rig.eastAbsent(),
                    "draining a cell must not load the chunk beside it: the lamp the drain updates reads the signal"
                            + " of the solid block beside it, which reads the cell across the border");
            check(rig.level.getFluidState(rig.cell(EDGE_X - 2)).isSource(),
                    "the cell three blocks from the border must be left alone while the chunk across it is absent");
            check(noted, "liveness: the fill must have met the absent chunk beside the row (what the pump waits on)");
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
     * The chunk across a corner is in reach too. The row runs along the chunk's south edge, with the east and south
     * chunks loaded and the one between them, diagonally across the corner, absent. A lamp stands in the east
     * chunk's first column on the row: draining the cell at the corner updates it, and the lamp reads the cell south
     * of itself, which sits in the diagonal chunk. The two chunks beside the corner cell are present, so a guard that
     * asks only about those lets the drain through; the pump must leave every cell within three blocks of the
     * corner alone.
     */
    public static void drainBesideACornerNeverReadsIntoTheDiagonalChunk(GameTestHelper helper) {
        Rig rig = null;
        try {
            rig = new Rig(helper, 8, Blocks.WATER.defaultBlockState(), CORNER_OFFSET, EDGE_X);
            rig.alsoLoad(1, 0);
            rig.alsoLoad(0, 1);
            rig.watch(1, 1);
            check(!rig.absent(1, 0) && !rig.absent(0, 1),
                    "precondition: the chunks east and south of the corner must be loaded");
            rig.place(16, EDGE_X, Blocks.REDSTONE_LAMP.defaultBlockState());
            rig.build();
            rig.pump.serverTick(); // the first tick builds the queue: only it sees the whole row, drained or not
            boolean noted = rig.pump.foundAbsent(rig.cx + 1, rig.cz + 1);

            rig.runPowered(300);

            check(rig.pump.getTank().getAmountMb(0) > 0, "liveness: the powered pump must have drained the row");
            check(rig.level.getFluidState(rig.cell(EDGE_X - 3)).isEmpty(),
                    "liveness: the cell four blocks from the corner must be drained: it is out of every reach");
            check(rig.absent(1, 1),
                    "draining a cell must not load the chunk across the corner: the lamp the drain updates reads"
                            + " the cell beside it that lies in the diagonal chunk");
            check(rig.level.getFluidState(rig.cell(EDGE_X)).isSource(),
                    "the cell at the corner must be left alone while the chunk across the corner is absent");
            check(noted, "liveness: the fill must have met the diagonal chunk absent (what the pump waits on)");
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
     * The check is made again at the drain. The fill is built while the chunk east of the pool is loaded, so it queues
     * the border cell, and the pump picks it (the last cell queued, so the first it works on). Then the chunk beside
     * the pool unloads: nothing holds it, while the pool's own chunk is held by a ticket of its own. The pump is driven
     * on, and its next bucket is the border cell, whose own chunk is still there but whose drain would update the
     * cell across the border: it must notice the chunk is gone, rebuild without the cells in reach of it, and drain
     * the rest of the row. The chunk stays absent, and the three cells within reach of it (the last three of the row)
     * are left alone.
     */
    public static void drainRechecksTheChunksBesideItWhenOneUnloads(GameTestHelper helper) {
        Rig rig = null;
        boolean polling = false;
        try {
            rig = new Rig(helper, 8, Blocks.WATER.defaultBlockState(), UNLOAD_OFFSET, LOCAL_Z, true);
            rig.watchLoadedEastChunk();
            rig.alsoLoad(1, 0); // for this tick only: the ticket getChunk takes expires, and nothing else holds it
            rig.build();
            check(!rig.eastAbsent(), "precondition: the chunk beside the pool is loaded while the fill is built");
            rig.pump.serverTick(); // builds the queue, border cell included, and picks that last cell to work on
            check(rig.edge.equals(rig.pump.workingOn()),
                    "precondition: the pump must have queued the border cell and picked it, got " + rig.pump.workingOn());

            Rig held = rig;
            EntityArenaUtil.tickUntil(helper, UNLOAD_TICKS, held::eastAbsent, () -> {
                held.runPowered(300);

                helper.assertTrue(held.pump.getTank().getAmountMb(0) > 0,
                        "liveness: the pump must have drained the cells clear of the unloaded chunk");
                helper.assertTrue(held.level.getFluidState(held.cell(12)).isEmpty(),
                        "liveness: the cell four blocks from the border is out of every reach and must be drained");
                helper.assertTrue(held.eastAbsent(),
                        "draining must not load the chunk that unloaded after the border cell was queued: the pump"
                                + " checked the cell's own chunk only, and its block update read across the border");
                for (int x = EDGE_X - 2; x <= EDGE_X; x++) {
                    helper.assertTrue(held.level.getFluidState(held.cell(x)).isSource(),
                            "the cell at local x " + x + " is within reach of the absent chunk and must be left alone");
                }
                helper.succeed();
            }, "the chunk beside the pool never unloaded once nothing held it (the pump was never tested: the"
                    + " server's chunk unloading is slow, not the pump)", held::close);
            polling = true;
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            if (rig != null && !polling) {
                rig.close();
            }
        }
    }
}
