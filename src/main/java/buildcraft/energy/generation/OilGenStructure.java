package buildcraft.energy.generation;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
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
                        BlockPos upper = level.getHeightmapPos(
                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                            new BlockPos(x, 0, z)
                        ).below();
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
            // In 1.12.2, Y=1 was always stone/oil above the flat bedrock at Y=0.
            // In modern MC, the bedrock gradient means pos.above() is often bedrock,
            // which would block the spring's oil regeneration (it checks for empty/air above).
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
