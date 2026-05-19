package buildcraft.energy.generation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import buildcraft.api.core.BCLog;
import buildcraft.api.enums.EnumSpring;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.Box;

import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.tile.TileSpringOil;

public abstract class OilGenStructure {
    public final Box box;
    public final ReplaceType replaceType;

    public OilGenStructure(Box containingBox, ReplaceType replaceType) {
        this.box = containingBox;
        this.replaceType = replaceType;
    }

    public final void generate(LevelAccessor level, Box within) {
        Box intersect = box.getIntersect(within);
        if (intersect != null) {
            generateWithin(level, intersect);
        }
    }

    /** Generates this structure in the world, but only between the given coordinates. */
    protected abstract void generateWithin(LevelAccessor level, Box intersect);

    /** @return The number of oil blocks that this structure will set. Note that this is called *after*
     *         {@link #generateWithin}, by the Spring type, so this can store the number set. */
    protected abstract int countOilBlocks();

    public void setOilIfCanReplace(LevelAccessor level, BlockPos pos) {
        if (canReplaceForOil(level, pos)) {
            setOil(level, pos);
        }
    }

    public boolean canReplaceForOil(LevelAccessor level, BlockPos pos) {
        return replaceType.canReplace(level, pos);
    }

    public static void setOil(LevelAccessor level, BlockPos pos) {
        level.setBlock(pos, BCEnergyFluids.OIL_COOL.source().get().defaultFluidState().createLegacyBlock(), 2);
    }

    /**
     * Finds the topmost non-air block in the column at (x, z). Mirrors what
     * {@link net.minecraft.world.level.levelgen.Heightmap.Types#WORLD_SURFACE}
     * would return — but by direct downward scan, so it works when the heightmap
     * hasn't been primed yet (which is the case for neighbour chunks scanned by
     * the 5-chunk-radius oil generator firing on ChunkEvent.Load).
     */
    protected static BlockPos findWorldSurfaceTop(LevelAccessor level, int x, int z) {
        int maxY = level.getMaxY();
        int minY = level.getMinY();
        for (int y = maxY; y > minY; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p).isAir()) {
                return p;
            }
        }
        return new BlockPos(x, minY, z);
    }

    /**
     * Finds the topmost block that blocks motion (skipping air, water, lava,
     * foliage, etc.) in the column at (x, z). Mirrors
     * {@link net.minecraft.world.level.levelgen.Heightmap.Types#OCEAN_FLOOR_WG}
     * — but by direct downward scan, avoiding the "Unprimed heightmap" error
     * spam when the generator runs on ChunkEvent.Load and probes neighbour
     * chunks whose heightmaps aren't yet built.
     */
    protected static BlockPos findSolidSurfaceTop(LevelAccessor level, int x, int z) {
        int maxY = level.getMaxY();
        int minY = level.getMinY();
        for (int y = maxY; y > minY; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(p);
            if (!state.isAir() && !state.canBeReplaced()) {
                return p;
            }
        }
        return new BlockPos(x, minY, z);
    }

    /** Maximum vertical scan distance (blocks) when looking for a tree above a surface
     *  cell. Tall jungle canopies peak ~25 blocks above ground; 32 gives slack for
     *  modded variants without paying for unbounded scans. */
    private static final int MAX_TREE_SCAN_HEIGHT = 32;
    /** Per-cell expansion of the loading chunk's box that the tree-clear BFS is
     *  allowed to write into. Lets the BFS follow a tree trunk a few blocks across
     *  a chunk boundary without unbounded cross-chunk writes. */
    private static final int TREE_CLEAR_CHUNK_EXPANSION = 4;
    /** Safety cap on the BFS, in tree-blocks visited. With enqueue-time filtering
     *  (we only queue logs/leaves, not their air neighbours) each unit of budget
     *  corresponds to one cleared tree block. Megajungles peak ~600 blocks; 8192
     *  comfortably covers connected canopy clusters from several touching trees. */
    private static final int TREE_CLEAR_BFS_BUDGET = 8192;

    /**
     * Walks the column at {@code baseTop} downward to the true ground, BFS-clearing
     * any tree blocks encountered along the way (and any canopy that overhangs from
     * neighbouring columns). Returns the position of the first non-tree solid below
     * {@code baseTop} — i.e. the dirt/grass/sand/stone the oil should be placed on.
     *
     * <p>Two phases:
     * <ol>
     *   <li><b>Upward sweep</b> — scan up to {@link #MAX_TREE_SCAN_HEIGHT} blocks above
     *       {@code baseTop} for the first log/leaf and BFS-clear it. Handles the
     *       common case where {@link net.minecraft.world.level.levelgen.Heightmap.Types#OCEAN_FLOOR_WG}
     *       landed on a non-tree block (e.g. dirt where a tree's canopy overhangs from
     *       the side) so the tree's lowest reachable block is above {@code baseTop}.</li>
     *   <li><b>Downward sweep</b> — walk from {@code baseTop} downward, BFS-clearing
     *       any surviving tree blocks encountered until a non-tree solid (the true
     *       ground) is found and returned. Critical for the case where an earlier
     *       cell's BFS partially cleared a tree (budget pressure, cross-chunk clamp,
     *       etc.) and left a stump — this cell's downward walk catches it.</li>
     * </ol>
     *
     * <p>BFS uses 26-connectivity, enqueue-time filtering (only logs/leaves go on the
     * queue, so {@link #TREE_CLEAR_BFS_BUDGET} corresponds to actual cleared blocks),
     * and {@link net.minecraft.world.level.block.Block#UPDATE_CLIENTS} flag on writes
     * to avoid neighbour-change cascades. Writes are clamped to {@code chunkBox}
     * expanded by {@link #TREE_CLEAR_CHUNK_EXPANSION} blocks — the BFS may follow a
     * tree across a chunk boundary by a few blocks but won't wander into unloaded
     * neighbours. Trees the BFS can't fully reach end up as detached leaves that
     * decay via vanilla {@code LeavesBlock.randomTick}.
     */
    protected static BlockPos clearTreesAndFindGround(LevelAccessor level, BlockPos baseTop, Box chunkBox) {
        Set<Long> visited = new HashSet<>();
        // Phase 1: upward sweep — find a tree starting at baseTop (or just above).
        BlockState baseState = level.getBlockState(baseTop);
        if (baseState.is(BlockTags.LOGS) || baseState.is(BlockTags.LEAVES)) {
            bfsClearTree(level, baseTop, chunkBox, visited);
        } else {
            for (int dy = 1; dy <= MAX_TREE_SCAN_HEIGHT; dy++) {
                BlockPos pos = baseTop.above(dy);
                BlockState s = level.getBlockState(pos);
                if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
                    bfsClearTree(level, pos, chunkBox, visited);
                    break;
                }
                if (s.isAir() || s.canBeReplaced()) continue;
                break; // hit something solid that isn't a tree — stop
            }
        }
        // Phase 2: downward sweep — walk to true ground, BFS-clearing any survivor we meet.
        BlockPos pos = baseTop;
        int minY = level.getMinY();
        for (int i = 0; i < 64; i++) {
            if (pos.getY() <= minY) return pos;
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                bfsClearTree(level, pos, chunkBox, visited);
                // BFS may have cleared this position; either way fall through and keep walking.
            } else if (!state.isAir() && !state.canBeReplaced()) {
                return pos;
            }
            pos = pos.below();
        }
        return pos;
    }

    private static void bfsClearTree(LevelAccessor level, BlockPos start, Box chunkBox, Set<Long> visited) {
        int minX = chunkBox.min().getX() - TREE_CLEAR_CHUNK_EXPANSION;
        int maxX = chunkBox.max().getX() + TREE_CLEAR_CHUNK_EXPANSION;
        int minZ = chunkBox.min().getZ() - TREE_CLEAR_CHUNK_EXPANSION;
        int maxZ = chunkBox.max().getZ() + TREE_CLEAR_CHUNK_EXPANSION;
        if (!visited.add(start.asLong())) return;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int budget = TREE_CLEAR_BFS_BUDGET;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            // The block may have flipped to air between enqueue and dequeue (a prior
            // pop's setBlock could touch a neighbour that's already in our queue if
            // they share a tag — rare but cheap to handle).
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = pos.offset(dx, dy, dz);
                        if (n.getX() < minX || n.getX() > maxX || n.getZ() < minZ || n.getZ() > maxZ) continue;
                        if (!visited.add(n.asLong())) continue;
                        BlockState ns = level.getBlockState(n);
                        if (ns.is(BlockTags.LOGS) || ns.is(BlockTags.LEAVES)) {
                            queue.add(n);
                        }
                    }
                }
            }
        }
    }

    public enum ReplaceType {
        ALWAYS {
            @Override
            public boolean canReplace(LevelAccessor level, BlockPos pos) {
                return true;
            }
        },
        /** Replaces everything except bedrock. Used for the oil tube through the
         *  bedrock gradient so that natural bedrock is preserved and the void
         *  isn't exposed. */
        NOT_BEDROCK {
            @Override
            public boolean canReplace(LevelAccessor level, BlockPos pos) {
                return !level.getBlockState(pos).is(Blocks.BEDROCK);
            }
        },
        IS_FOR_LAKE {
            @Override
            public boolean canReplace(LevelAccessor level, BlockPos pos) {
                return ALWAYS.canReplace(level, pos);
            }
        };
        public abstract boolean canReplace(LevelAccessor level, BlockPos pos);
    }

    public static class GenByPredicate extends OilGenStructure {
        public final Predicate<BlockPos> predicate;

        public GenByPredicate(Box containingBox, ReplaceType replaceType, Predicate<BlockPos> predicate) {
            super(containingBox, replaceType);
            this.predicate = predicate;
        }

        @Override
        protected void generateWithin(LevelAccessor level, Box intersect) {
            for (BlockPos pos : BlockPos.betweenClosed(intersect.min(), intersect.max())) {
                if (predicate.test(pos)) {
                    setOilIfCanReplace(level, pos);
                }
            }
        }

        @Override
        protected int countOilBlocks() {
            int count = 0;
            for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
                if (predicate.test(pos)) {
                    count++;
                }
            }
            return count;
        }
    }

    public static class FlatPattern extends OilGenStructure {
        private final boolean[][] pattern;
        private final int depth;

        private FlatPattern(Box containingBox, ReplaceType replaceType, boolean[][] pattern, int depth) {
            super(containingBox, replaceType);
            this.pattern = pattern;
            this.depth = depth;
        }

        public static FlatPattern create(BlockPos start, ReplaceType replaceType, boolean[][] pattern, int depth) {
            BlockPos min = start.offset(0, 1 - depth, 0);
            BlockPos max = start.offset(pattern.length - 1, 0, pattern.length == 0 ? 0 : pattern[0].length - 1);
            Box box = new Box(min, max);
            return new FlatPattern(box, replaceType, pattern, depth);
        }

        @Override
        protected void generateWithin(LevelAccessor level, Box intersect) {
            BlockPos start = box.min();
            for (BlockPos pos : BlockPos.betweenClosed(intersect.min(), intersect.max())) {
                int x = pos.getX() - start.getX();
                int z = pos.getZ() - start.getZ();
                if (pattern[x][z]) {
                    setOilIfCanReplace(level, pos);
                }
            }
        }

        @Override
        protected int countOilBlocks() {
            int count = 0;
            for (int x = 0; x < pattern.length; x++) {
                for (int z = 0; z < pattern[x].length; z++) {
                    if (pattern[x][z]) {
                        count++;
                    }
                }
            }
            return count * depth;
        }
    }

    public static class PatternTerrainHeight extends OilGenStructure {
        private final boolean[][] pattern;
        private final int depth;

        private PatternTerrainHeight(Box containingBox, ReplaceType replaceType, boolean[][] pattern, int depth) {
            super(containingBox, replaceType);
            this.pattern = pattern;
            this.depth = depth;
        }

        public static PatternTerrainHeight create(BlockPos start, ReplaceType replaceType, boolean[][] pattern,
            int depth) {
            BlockPos min = VecUtil.replaceValue(start, Axis.Y, 1);
            BlockPos max = min.offset(pattern.length - 1, 319, pattern.length == 0 ? 0 : pattern[0].length - 1);
            Box box = new Box(min, max);
            return new PatternTerrainHeight(box, replaceType, pattern, depth);
        }

        @Override
        protected void generateWithin(LevelAccessor level, Box intersect) {
            for (int x = intersect.min().getX(); x <= intersect.max().getX(); x++) {
                int px = x - box.min().getX();

                for (int z = intersect.min().getZ(); z <= intersect.max().getZ(); z++) {
                    int pz = z - box.min().getZ();

                    if (pattern[px][pz]) {
                        BlockPos upper = findWorldSurfaceTop(level, x, z);
                        if (canReplaceForOil(level, upper)) {
                            for (int y = 0; y < 5; y++) {
                                level.setBlock(upper.above(y), Blocks.AIR.defaultBlockState(), 2);
                            }
                            for (int y = 0; y < depth; y++) {
                                setOilIfCanReplace(level, upper.below(y));
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected int countOilBlocks() {
            int count = 0;
            for (int x = 0; x < pattern.length; x++) {
                for (int z = 0; z < pattern[x].length; z++) {
                    if (pattern[x][z]) {
                        count++;
                    }
                }
            }
            return count * depth;
        }
    }

    /**
     * Clean-pool surface oil structure used in rich land biomes (desert/mesa) where
     * the messy tendril aesthetic of {@link PatternTerrainHeight} looks pasted-on.
     * Footprint is a filled disc with ±1 block radial noise. Always tree-aware — the
     * helper is a no-op in tree-free desert/mesa terrain but defensively handles
     * modded biomes added to the rich list that might contain trees.
     */
    public static class SurfacePool extends OilGenStructure {
        private final boolean[][] pattern;
        private final int depth;

        private SurfacePool(Box containingBox, ReplaceType replaceType, boolean[][] pattern, int depth) {
            super(containingBox, replaceType);
            this.pattern = pattern;
            this.depth = depth;
        }

        public static SurfacePool create(BlockPos start, ReplaceType replaceType, boolean[][] pattern, int depth) {
            BlockPos min = VecUtil.replaceValue(start, Axis.Y, 1);
            BlockPos max = min.offset(pattern.length - 1, 319, pattern.length == 0 ? 0 : pattern[0].length - 1);
            Box box = new Box(min, max);
            return new SurfacePool(box, replaceType, pattern, depth);
        }

        @Override
        protected void generateWithin(LevelAccessor level, Box intersect) {
            for (int x = intersect.min().getX(); x <= intersect.max().getX(); x++) {
                int px = x - box.min().getX();

                for (int z = intersect.min().getZ(); z <= intersect.max().getZ(); z++) {
                    int pz = z - box.min().getZ();

                    if (pattern[px][pz]) {
                        BlockPos baseTop = findSolidSurfaceTop(level, x, z);
                        BlockPos upper = clearTreesAndFindGround(level, baseTop, intersect);
                        if (canReplaceForOil(level, upper)) {
                            for (int y = 0; y < 5; y++) {
                                level.setBlock(upper.above(y), Blocks.AIR.defaultBlockState(), 2);
                            }
                            for (int y = 0; y < depth; y++) {
                                setOilIfCanReplace(level, upper.below(y));
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected int countOilBlocks() {
            int count = 0;
            for (int x = 0; x < pattern.length; x++) {
                for (int z = 0; z < pattern[x].length; z++) {
                    if (pattern[x][z]) {
                        count++;
                    }
                }
            }
            return count * depth;
        }
    }

    public static class Spout extends OilGenStructure {
        public final BlockPos start;
        public final int radius;
        public final int height;
        private int count = 0;

        public Spout(BlockPos start, ReplaceType replaceType, int radius, int height) {
            super(createBox(start), replaceType);
            this.start = start;
            this.radius = radius;
            this.height = height;
        }

        private static Box createBox(BlockPos start) {
            return new Box(start, VecUtil.replaceValue(start, Axis.Y, 320));
        }

        @Override
        protected void generateWithin(LevelAccessor level, Box intersect) {
            count = 0;
            // Find the highest non-air, non-fluid block above the spout origin
            int maxY = level.getMaxY();
            BlockPos worldTop = new BlockPos(start.getX(), maxY, start.getZ());
            for (int y = maxY; y >= start.getY(); y--) {
                worldTop = new BlockPos(start.getX(), y, start.getZ());
                BlockState state = level.getBlockState(worldTop);
                if (state.isAir()) {
                    continue;
                }
                if (BlockUtil.getFluidWithFlowing(state.getBlock()) != null) {
                    break;
                }
                if (state.blocksMotion()) {
                    break;
                }
            }
            OilGenStructure tubeY = OilGenerator.createTube(start, worldTop.getY() - start.getY(), radius, Axis.Y);
            tubeY.generate(level, tubeY.box);
            count += tubeY.countOilBlocks();
            BlockPos base = worldTop;
            for (int r = radius; r >= 0; r--) {
                OilGenStructure struct = OilGenerator.createTube(base, height, r, Axis.Y);
                struct.generate(level, struct.box);
                base = base.offset(0, height, 0);
                count += struct.countOilBlocks();
            }
        }

        @Override
        protected int countOilBlocks() {
            if (count == 0) {
                throw new IllegalStateException("Called countOilBlocks before calling generateWithin!");
            }
            return count;
        }
    }

    public static class Spring extends OilGenStructure {
        public final BlockPos pos;

        public Spring(BlockPos pos) {
            super(new Box(pos, pos), ReplaceType.ALWAYS);
            this.pos = pos;
        }

        @Override
        protected void generateWithin(LevelAccessor level, Box intersect) {
            // NO-OP (this one is called separately)
        }

        @Override
        protected int countOilBlocks() {
            return 0;
        }

        public void generate(LevelAccessor level, int count) {
            BlockState state = BCCoreBlocks.SPRING_OIL.get().defaultBlockState();
            level.setBlock(pos, state, 2);
            // Force-place oil directly above the spring so it can function.
            // Spring sits at level.getMinY() (replacing the 100%-bedrock floor),
            // so pos.above() is at minY+1 — well within the bedrock gradient.
            // Without ALWAYS-replace we'd land on bedrock and the spring would
            // never regenerate (its tick checks isEmptyBlock above).
            setOil(level, pos.above());
            BlockEntity tile = level.getBlockEntity(pos);
            TileSpringOil spring;
            if (tile instanceof TileSpringOil) {
                spring = (TileSpringOil) tile;
            } else {
                BCLog.logger.warn("[energy.gen.oil] Setting the blockstate didn't also set the tile at " + pos);
                return;
            }
            spring.totalSources = count;
        }
    }
}
