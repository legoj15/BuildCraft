/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.block;

import static buildcraft.lib.test.GameTestUtil.check;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.EnumWirePart;

import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.FarChunk;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;
import buildcraft.transport.wire.SavedDataWireSystems;
import buildcraft.transport.wire.WireSystem;

/**
 * A pipe wire saved in a chunk is back in a wire system once that chunk loads again, with nothing but the chunk load
 * to cause it (#259). Since #257 a wired pipe registers with the server's wire systems only from its load hook
 * ({@code TilePipeHolder.onLoaded}, which marks the systems stale for the wire tick at the end of the level tick), and
 * the three loaders reach that hook by three mechanisms (NeoForge's {@code onLoad} from the level's fresh block entity
 * list, Forge through {@code LevelChunkMixin}, Fabric through {@code ServerBlockEntityEvents.BLOCK_ENTITY_LOAD}).
 * {@code loader_parity_block_entity_joins_after_block_ticks} pins placement only and
 * {@code wire_load_rejoins_stranded_wire} calls the hook by hand, so a loader or Minecraft bump that stopped running a
 * block entity's load hook on chunk load would have left every other test green and every wire in a loaded save dark.
 *
 * <p>The chunk is a {@link FarChunk} held loaded to {@code FULL} without ticking, which is the case that matters: a
 * chunk in memory but not ticking never ticks its pipes, so only the load hook can register the wire. A wired
 * structure pipe is placed in it, the chunk is released and waited out until it is gone from memory (saved on the way),
 * the systems holding the wire are removed by hand (what a rebuild that cut the wire off while its chunk was out of
 * memory leaves behind), and the chunk is held again. The pipe that comes back must be a block entity read from the
 * save, still carrying its wire, and the wire must be in a system again some ticks later. No tick of the pipe is
 * involved: the chunk does not tick.
 */
public class PipeWireChunkLoadTester {

    /** Chunks east and south of the arena's, at least 8 from every other test's far chunk (see {@link FarChunk}). */
    private static final int CHUNK_OFFSET = 1999;

    /** The pipe's column is the chunk's middle, well inside it so that nothing reads across a chunk border; the
     *  height is fixed so that no per-version minimum height is needed (every line builds up to y 320). */
    private static final int LOCAL_X = 8;
    private static final int LOCAL_Z = 8;
    private static final int PIPE_Y = 200;

    private static final EnumWirePart WIRE = EnumWirePart.EAST_DOWN_NORTH;
    private static final DyeColor COLOUR = DyeColor.YELLOW;

    /** How long the released chunk may take to leave memory. */
    private static final int UNLOAD_TICKS = 200;

    /** How long the wire may take to be in a system again once the chunk is held again: the load hook runs at the head
     *  of the level's next block entity tick, and the wire tick that rebuilds runs at the end of that tick. */
    private static final int REJOIN_TICKS = 100;

    /** The far chunk, the pipe in it and the wire's systems; {@link #close} takes it all down on every exit. */
    private static final class Rig {
        final ServerLevel level;
        final SavedDataWireSystems wireSystems;
        final BlockPos pos;
        final WireSystem.WireElement element;
        /** The chunk as it stands: replaced by a new {@link FarChunk} when the test holds the chunk again, so the
         *  cleanup always releases the one that is held. */
        FarChunk chunk;
        /** The block entity the test placed, to tell it from the one the chunk load reads back. */
        TilePipeHolder placed;

        Rig(GameTestHelper helper) {
            level = helper.getLevel();
            wireSystems = SavedDataWireSystems.get(level);
            chunk = FarChunk.hold(helper, CHUNK_OFFSET);
            pos = chunk.pos(LOCAL_X, PIPE_Y, LOCAL_Z);
            element = new WireSystem.WireElement(pos, WIRE);
        }

        /** A structure pipe with one yellow wire, placed straight on the level (the arena helper's positions are
         *  relative to the arena, which is not where the far chunk is). */
        void placeWiredPipe() {
            level.setBlock(pos, BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState(), 3);
            check(level.getBlockEntity(pos) instanceof TilePipeHolder, "the pipe holder must have a block entity");
            placed = (TilePipeHolder) level.getBlockEntity(pos);
            placed.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRUCTURE.get()));
            placed.getWireManager().addPart(WIRE, COLOUR);
        }

        List<WireSystem> systemsOfWire() {
            return wireSystems.getWireSystemsWithElement(element);
        }

        void removeSystemsOfWire() {
            systemsOfWire().forEach(wireSystems::removeWireSystem);
        }

        /** Holds the chunk again (a new {@link FarChunk}: the old one was released), which loads it from the save. */
        void holdAgain(GameTestHelper helper) {
            chunk = FarChunk.hold(helper, CHUNK_OFFSET);
        }

        void close() {
            if (!chunk.isAbsent(0, 0)) {
                level.removeBlock(pos, false);
            }
            removeSystemsOfWire();
            chunk.release();
        }
    }

    /**
     * The sequence of the class comment, as one flat poll whose phase array is the state machine (never a poll
     * scheduled from inside a poll). The poll starts at once and never waits for the arena cell to tick: the pipe is
     * not in the arena and nothing here depends on a tick of any cell.
     */
    public static void testChunkLoadRejoinsWire(GameTestHelper helper) {
        Rig rig = null;
        boolean polling = false;
        try {
            rig = new Rig(helper);
            rig.placeWiredPipe();
            check(!rig.systemsOfWire().isEmpty(),
                    "precondition: adding the wire must have registered its system at once");
            rig.chunk.release();

            Rig polled = rig;
            int[] phase = { 0 };
            int[] polls = { 0 };
            EntityArenaUtil.tickUntil(helper, UNLOAD_TICKS + REJOIN_TICKS, () -> {
                polls[0]++;
                if (phase[0] == 0) {
                    if (!polled.chunk.isAbsent(0, 0)) {
                        if (polls[0] > UNLOAD_TICKS) {
                            helper.fail("the released far chunk was still in memory after " + UNLOAD_TICKS
                                    + " ticks (the pipe was never tested: the server's chunk unloading is slow, not"
                                    + " the wire code)");
                        }
                        return false;
                    }
                    // The chunk is gone (and saved). A rebuild that cut the wire off while the chunk was out of memory
                    // leaves it in no system: stand that in by removing them, then load the chunk again.
                    polled.removeSystemsOfWire();
                    check(polled.systemsOfWire().isEmpty(), "precondition: the wire must be in no system before the"
                            + " chunk loads again");
                    polled.holdAgain(helper);
                    check(polled.level.getBlockEntity(polled.pos) instanceof TilePipeHolder,
                            "precondition: the reloaded chunk must hold a pipe holder at " + polled.pos);
                    TilePipeHolder reloaded = (TilePipeHolder) polled.level.getBlockEntity(polled.pos);
                    check(reloaded != polled.placed, "precondition: the pipe must be a block entity read back from"
                            + " the saved chunk, not the one placed (the chunk never really left memory)");
                    check(reloaded.getWireManager().getColorOfPart(WIRE) == COLOUR, "precondition: the saved pipe must"
                            + " still carry its " + COLOUR + " wire, got " + reloaded.getWireManager().parts);
                    phase[0] = 1;
                    return false;
                }
                // Nothing but the chunk load can have put the wire back: the chunk does not tick, so neither does
                // the pipe.
                return !polled.systemsOfWire().isEmpty();
            }, helper::succeed, "the wire was in no system after its chunk loaded again: the pipe's load hook did not"
                    + " run on the chunk load, or did not ask for a rebuild", polled::close);
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
