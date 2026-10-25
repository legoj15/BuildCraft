/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
//? if <1.21.10 {
/*import net.minecraft.util.Unit;*/
//?}
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * One chunk, far from every arena, loaded on its own so that the chunks around it are known to be absent: the stage
 * for a test of code that must never load a chunk (a machine that reads or changes a cell beside a chunk border).
 *
 * <p><b>Loaded by a plain {@code getChunk}, never by {@code setChunkForced}.</b> A forced chunk carries a ticket that
 * reaches two chunks out, so the ring around it is queued to load too. Generating those chunks runs on other threads,
 * and the moment one of them turns present depends on how the machine schedules them: a test that asserted "the chunk
 * beside it is absent" passed or failed with what ran before it (measured: a rig loaded two chunks from the one before
 * it found its neighbour present in 33 of 60 builds; {@code pump_fill_skips_unloaded_chunk} failed two full runs
 * in six that way). {@code getChunk} takes a ticket at the chunk alone, so the chunks around it are
 * only ever asked for the partial statuses its own generation needs and none of them can become present. The ticket
 * lasts for the rest of the tick, so <b>everything built on a {@code FarChunk} must run inside one tick</b> (no polls,
 * no waits): the chunk may unload on the next. A test that has to wait for the chunk beside it to <em>unload</em> pins
 * the chunk itself across ticks with {@link #hold} instead, which keeps it (and only it) loaded until {@link #release}.
 *
 * <p><b>Every test gets its own offset.</b> The arenas of a run sit on one grid some fifty chunks across, so two tests
 * that use the same offset can land on neighbouring chunks, and a chunk the first one loaded is still present when the
 * second asserts that its neighbour is absent. Offsets in use, which must stay at least 8 apart: 777
 * ({@code OilGenStructureTester}), 888, 999, 1111, 1222, 1333 ({@code PumpUnloadedChunkTester}), 1444
 * ({@code RobotPumpUnloadedChunkTester}), 1555 ({@code UnloadedChunkSearchTester}), 1666
 * ({@code GroundSearchTester}), 1777 ({@code RobotStationRigTester}).
 *
 * <p>The chunk is real worldgen, so what stands in it is not controlled: a test writes every cell it depends on
 * ({@link #setRaw}) and puts them back ({@link #clearRaw}) when it is done. {@code setRaw} writes straight into the chunk
 * section, which never runs a block update (a block placed the normal way would read its neighbours, which is what the
 * test is guarding against).
 */
public final class FarChunk {
    /** How many polls {@link #queue} spends waiting for the holder before it reports the precondition as failed. */
    private static final int QUEUE_POLL_CAP = 8;

    private final ServerLevel level;
    private final int chunkX;
    private final int chunkZ;
    private boolean held;

    private FarChunk(ServerLevel level, int chunkX, int chunkZ) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    /** Loads the chunk {@code offset} chunks east and south of the test arena's, and fails the test when it is not
     *  there. */
    public static FarChunk load(GameTestHelper helper, int offset) {
        BlockPos arena = helper.absolutePos(BlockPos.ZERO);
        return load(helper.getLevel(), (arena.getX() >> 4) + offset, (arena.getZ() >> 4) + offset);
    }

    /** Loads the chunk at these chunk coordinates (see the class javadoc for the offset to pick). */
    public static FarChunk load(ServerLevel level, int chunkX, int chunkZ) {
        FarChunk chunk = new FarChunk(level, chunkX, chunkZ);
        chunk.loadAt(0, 0);
        chunk.requireLoaded();
        return chunk;
    }

    /**
     * Loads the chunk {@code offset} chunks east and south of the test arena's and keeps it loaded, across ticks, until
     * {@link #release}: a ticket at the chunk alone (no radius: the chunks around it are not asked to load, so one a
     * test loaded with {@link #loadBeside} unloads again a tick after it was loaded, with this one still there). The
     * chunk is loaded to {@code FULL} but does not tick, so a block entity in it is driven by hand. The caller must
     * release it on every exit, or the ticket stays for the rest of the run.
     */
    public static FarChunk hold(GameTestHelper helper, int offset) {
        BlockPos arena = helper.absolutePos(BlockPos.ZERO);
        FarChunk chunk = new FarChunk(helper.getLevel(), (arena.getX() >> 4) + offset, (arena.getZ() >> 4) + offset);
        chunk.setHeld(true);
        try {
            chunk.loadAt(0, 0);
            chunk.requireLoaded();
        } catch (RuntimeException e) {
            chunk.release();
            throw e;
        }
        return chunk;
    }

    /**
     * Queues the chunk {@code offset} chunks east and south of the test arena's to load, without loading it: a ticket at
     * the chunk alone, and the ticket and holder updates polled ({@code ServerChunkCache.pollTask}) until the holder
     * exists and not one poll longer, which is as far as {@code getChunk} gets before it blocks on the generation. The
     * chunk then has a holder at the ticket level of a {@code FULL} chunk, which is the whole of what
     * {@code Level.hasChunk} and {@code Level.isLoaded} test, and is not in memory, which is what {@code getChunkNow}
     * (and {@code BlockUtil.isChunkPresent}) test: the state a read would answer by loading the chunk on the server
     * thread. Its promotion to {@code FULL} runs on the server thread, so it stays queued for as long as the caller
     * runs a single test method without polling; <b>build everything on it inside one tick</b>. The caller must
     * {@link #release} the ticket on every exit.
     *
     * @throws IllegalStateException when the chunk is not queued (already in memory, or no holder yet), a precondition
     *                               failure
     */
    public static FarChunk queue(GameTestHelper helper, int offset) {
        BlockPos arena = helper.absolutePos(BlockPos.ZERO);
        FarChunk chunk = new FarChunk(helper.getLevel(), (arena.getX() >> 4) + offset, (arena.getZ() >> 4) + offset);
        chunk.setHeld(true);
        try {
            // One poll runs the distance-manager update that creates the holder, but whether that is the first task the
            // processor hands out is not guaranteed; poll until the holder is there, and stop at once (a further poll
            // could run the promotion to FULL, which would leave the chunk in memory and the test with no queued chunk).
            for (int polls = 0; polls < QUEUE_POLL_CAP && !chunk.level.hasChunk(chunk.chunkX, chunk.chunkZ); polls++) {
                chunk.level.getChunkSource().pollTask();
            }
            GameTestUtil.check(chunk.level.hasChunk(chunk.chunkX, chunk.chunkZ),
                    "precondition: a queued chunk has a holder at the ticket level of a FULL chunk (hasChunk)");
            GameTestUtil.check(chunk.isAbsent(0, 0),
                    "precondition: a queued chunk is not in memory yet (getChunkNow)");
        } catch (RuntimeException e) {
            chunk.release();
            throw e;
        }
        return chunk;
    }

    /** Lets a {@link #hold}ing (or {@link #queue}d) ticket go; the chunk unloads some ticks later. Does nothing for a
     *  chunk that is not held, or that was released already. */
    public void release() {
        setHeld(false);
    }

    private void setHeld(boolean hold) {
        if (hold == held) {
            return;
        }
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        //? if >=1.21.10 {
        if (hold) {
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0);
        } else {
            level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0);
        }
        //?} else {
        /*if (hold) {
            level.getChunkSource().addRegionTicket(TicketType.START, pos, 0, Unit.INSTANCE);
        } else {
            level.getChunkSource().removeRegionTicket(TicketType.START, pos, 0, Unit.INSTANCE);
        }*/
        //?}
        held = hold;
    }

    private void requireLoaded() {
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
            throw new IllegalStateException("Assertion failed: precondition: the far chunk must be loaded to FULL");
        }
    }

    /** Loads the chunk beside this one, at this offset (a chunk the test means to be present). It is loaded alone,
     *  like this one. */
    public void loadBeside(int dx, int dz) {
        loadAt(dx, dz);
    }

    private void loadAt(int dx, int dz) {
        level.getChunk(chunkX + dx, chunkZ + dz);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** Whether the chunk at this offset from this one is out of memory ({@code getChunkNow}: never loads it). */
    public boolean isAbsent(int dx, int dz) {
        return level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz) == null;
    }

    /** The block position of local column ({@code localX}, {@code localZ}) of this chunk at height {@code y}; the
     *  column may lie outside 0..15, in the chunk beside. */
    public BlockPos pos(int localX, int y, int localZ) {
        return new BlockPos(chunkX * 16 + localX, y, chunkZ * 16 + localZ);
    }

    /** Writes {@code state} into the chunk section holding {@code pos}, with no block update; fails when that chunk
     *  is not in memory. */
    public void setRaw(BlockPos pos, BlockState state) {
        LevelChunk target = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (target == null) {
            throw new IllegalStateException(
                    "Assertion failed: precondition: the chunk of " + pos + " must be in memory to build in it");
        }
        target.getSection(level.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15,
                pos.getZ() & 15, state);
    }

    /** Sets a cell to air with no block update, when its chunk is still in memory. */
    public void clearRaw(BlockPos pos) {
        LevelChunk target = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (target != null) {
            target.getSection(level.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15,
                    pos.getZ() & 15, Blocks.AIR.defaultBlockState());
        }
    }
}
