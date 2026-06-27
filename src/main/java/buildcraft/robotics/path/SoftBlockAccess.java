/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import buildcraft.api.core.BuildCraftAPI;

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
     *         replacing 7.1.x's explicit {@code y < 0} guard.
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

    /** Production adapter: a robot may occupy a cell when it is in-world and {@link BuildCraftAPI#isSoftBlock soft}. */
    static SoftBlockAccess of(Level level) {
        return new SoftBlockAccess() {
            @Override
            public boolean isSoft(BlockPos pos) {
                if (level.isOutsideBuildHeight(pos)) {
                    return false;
                }
                return BuildCraftAPI.isSoftBlock(level, pos);
            }

            @Override
            public boolean isChunkLoaded(BlockPos pos) {
                return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                        SectionPos.blockToSectionCoord(pos.getZ()));
            }
        };
    }
}
