/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.lib.misc.BlockUtil;

/**
 * Block-traversability oracle for the robotics A* pathfinder.
 *
 * <p>This is the seam that decouples {@link PathFinding} / {@link PathFindingSearch} from a live
 * {@link Level}. In production it wraps a level (see {@link #of(Level)}); in tests it is backed by an
 * in-memory grid, so the entire pathfinding column can be exercised as cheap pure JUnit without
 * bootstrapping the game.
 *
 * <p>Note this is distinct from {@code buildcraft.lib.compat.ISoftBlockAccessor}, which fetches tiles
 * and block states (optionally force-loading chunks). This interface answers only the two yes/no
 * questions A* needs: "can a robot occupy this cell?" and "is this cell's chunk available to scan?".
 */
public interface SoftBlockAccess {

    /**
     * @return true if a flying robot may occupy {@code pos} — i.e. the block there is "soft" (air,
     *         replaceable foliage, fluids, …). Positions outside the world (below the floor, above the
     *         ceiling) must return false; that is how the pathfinder avoids routing through the void,
     *         replacing 7.1.x's explicit {@code y < 0} guard. So must a position whose chunk is not
     *         {@link #isChunkLoaded loaded}: the robot treats the edge of the loaded world as a wall, and
     *         the production adapter never reads (and so never loads) a block there.
     */
    boolean isSoft(BlockPos pos);

    /**
     * @return true if the chunk containing {@code pos} is loaded and may be scanned. Search targets in
     *         unloaded chunks are skipped rather than force-loaded. Defaults to true for in-memory test
     *         grids, where chunk loading is not a concern.
     */
    default boolean isChunkLoaded(BlockPos pos) {
        return true;
    }

    /**
     * The loaded-chunk gate every production read goes through: {@code soft} is consulted only for a cell whose
     * chunk {@code chunkLoaded} reports loaded, and every other cell is not soft. On a server,
     * {@code Level.getBlockState} on an unloaded chunk loads (or generates) it synchronously on the server thread,
     * so an ungated read by a robot near the edge of view distance stalls the server on every search or path that
     * brushes the boundary - the neighbour checks of a search target and every A* expansion included.
     */
    static SoftBlockAccess loadedOnly(Predicate<BlockPos> chunkLoaded, Predicate<BlockPos> soft) {
        return new SoftBlockAccess() {
            @Override
            public boolean isSoft(BlockPos pos) {
                return chunkLoaded.test(pos) && soft.test(pos);
            }

            @Override
            public boolean isChunkLoaded(BlockPos pos) {
                return chunkLoaded.test(pos);
            }
        };
    }

    /** Production adapter: a robot may occupy a cell when its chunk is loaded and it is in-world and
     *  {@link BuildCraftAPI#isSoftBlock soft} (see {@link #loadedOnly}). */
    static SoftBlockAccess of(Level level) {
        // Presence, not Level.hasChunk: on a server that only tests the chunk's ticket level, which holds for a chunk
        // that is queued to load but not in memory yet, and a read there would load it (BlockUtil.isChunkPresent).
        return loadedOnly(
                pos -> BlockUtil.isChunkPresent(level, pos),
                pos -> !level.isOutsideBuildHeight(pos) && BuildCraftAPI.isSoftBlock(level, pos));
    }
}
