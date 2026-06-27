/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;

/**
 * In-memory {@link SoftBlockAccess} for pure-JUnit pathfinding tests — no {@link net.minecraft.world.level.Level}
 * required. Cells default to <em>not</em> soft (the void); only cells explicitly marked are traversable, which
 * matches the production behaviour where empty world is unreachable until a soft block is present.
 *
 * <p>The ASCII-map DSL ({@link #parse2D}) builds a single horizontal layer from top-down rows, where the row
 * index is {@code z} (increasing downward) and the column index is {@code x} (increasing rightward):
 * <pre>{@code
 *   MockSoftGrid grid = MockSoftGrid.parse2D(64,
 *       "S....",
 *       ".###.",
 *       "....E");
 * }</pre>
 * Legend: {@code '.'} or {@code ' '} soft · {@code '#'} solid · {@code 'S'} soft+start · {@code 'E'} soft+end ·
 * {@code 'T'} solid search target · {@code 'U'} unloaded chunk (neither soft nor loaded). {@link #start()} /
 * {@link #end()} expose the markers and {@link #targetFilter()} a filter matching the {@code 'T'} cells.
 */
public final class MockSoftGrid implements SoftBlockAccess {

    private final Set<BlockPos> soft = new HashSet<>();
    private final Set<BlockPos> unloaded = new HashSet<>();
    private final Set<BlockPos> targets = new HashSet<>();
    private BlockPos start;
    private BlockPos end;

    public MockSoftGrid() {
    }

    public static MockSoftGrid parse2D(int y, String... rows) {
        MockSoftGrid grid = new MockSoftGrid();
        for (int z = 0; z < rows.length; z++) {
            String row = rows[z];
            for (int x = 0; x < row.length(); x++) {
                BlockPos pos = new BlockPos(x, y, z);
                switch (row.charAt(x)) {
                    case '.':
                    case ' ':
                        grid.soft.add(pos);
                        break;
                    case '#':
                        // solid: leave out of the soft set
                        break;
                    case 'S':
                        grid.soft.add(pos);
                        grid.start = pos;
                        break;
                    case 'E':
                        grid.soft.add(pos);
                        grid.end = pos;
                        break;
                    case 'T':
                        // solid block that the search is hunting for
                        grid.targets.add(pos);
                        break;
                    case 'U':
                        grid.unloaded.add(pos);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown map char '" + row.charAt(x) + "' at x=" + x + " z=" + z);
                }
            }
        }
        return grid;
    }

    /** Marks {@code pos} soft, returning {@code this} for chaining. */
    public MockSoftGrid soft(BlockPos pos) {
        soft.add(pos);
        return this;
    }

    /** Records {@code pos} as a (solid) search target. */
    public MockSoftGrid target(BlockPos pos) {
        targets.add(pos);
        return this;
    }

    /** Marks {@code pos}'s cell as belonging to an unloaded chunk (skipped by searches). */
    public MockSoftGrid unloaded(BlockPos pos) {
        unloaded.add(pos);
        return this;
    }

    /** Marks a solid {@code box} of soft cells (inclusive bounds) — handy for open-field tests. */
    public MockSoftGrid fill(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    soft.add(new BlockPos(x, y, z));
                }
            }
        }
        return this;
    }

    public BlockPos start() {
        if (start == null) {
            throw new IllegalStateException("map has no 'S' start marker");
        }
        return start;
    }

    public BlockPos end() {
        if (end == null) {
            throw new IllegalStateException("map has no 'E' end marker");
        }
        return end;
    }

    public Set<BlockPos> targets() {
        return targets;
    }

    /** A filter matching exactly the {@code 'T'} cells of this map. */
    public IBlockFilter targetFilter() {
        return targets::contains;
    }

    @Override
    public boolean isSoft(BlockPos pos) {
        return soft.contains(pos);
    }

    @Override
    public boolean isChunkLoaded(BlockPos pos) {
        return !unloaded.contains(pos);
    }
}
