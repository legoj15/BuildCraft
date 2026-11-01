/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.gen;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//? if >=26.3 {
import com.mojang.serialization.MapCodec;
//?} else {
/*import com.mojang.serialization.Codec;
*///?}

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
//? if >=26.3 {
import net.minecraft.world.level.chunk.ChunkGenerator;
//?}
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
//? if <26.3 {
/*import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
*///?}
import net.minecraft.world.level.material.FluidState;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersConfig;
import buildcraft.builders.block.BlockFrame;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.data.AxisOrder;
import buildcraft.lib.misc.data.BoxIterator;
import buildcraft.lib.misc.data.EnumAxisOrder;

/**
 * An abandoned quarry: a default-sized (11x11, 5 tall) quarry frame, decayed into gaps and cobwebs, over a pit dug
 * the way the quarry digs. No quarry block. The pit goes at least {@link #MIN_DEPTH} full layers below the frame and
 * stops above the first layer that holds deepslate or a fluid source; its last layer may be half mined, in the
 * quarry's own snaking order.
 *
 * <p>Everything is decided by reads before the first write, so a rejected site leaves the world untouched. The
 * footprint is centred on the placement origin, which keeps every write inside the decorating chunk and its
 * neighbours.
 */
//? if >=26.3 {
// 26.3 merged Feature and ConfiguredFeature: Feature is a plain interface that carries its own
// MapCodec and receives the four place() values directly.
public record QuarryPitFeature() implements Feature {

    public static final MapCodec<QuarryPitFeature> CODEC = MapCodec.unit(QuarryPitFeature::new);

    @Override
    public MapCodec<QuarryPitFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        return generate(level, random, origin);
    }
//?} else {
/*public class QuarryPitFeature extends Feature<NoneFeatureConfiguration> {

    public QuarryPitFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return generate(context.level(), context.random(), context.origin());
    }
*///?}

    /** The quarry's default frame: 11 blocks across, 4 above its base (TileQuarry's no-marker box). */
    static final int FRAME_SIZE = 11;
    static final int FRAME_HEIGHT = 4;
    /** Fewest fully dug layers below the frame base. */
    static final int MIN_DEPTH = 5;
    /** Most the ground under the footprint may rise above its lowest column: the frame base sits on the highest. */
    static final int MAX_ROUGHNESS = 3;
    /** Chance a frame block has rotted away, and of those, the share left as a cobweb rather than air. */
    static final float FRAME_DECAY_CHANCE = 0.3f;
    static final float COBWEB_SHARE = 0.35f;

    private static boolean generate(WorldGenLevel level, RandomSource random, BlockPos origin) {
        if (!BCCoreConfig.worldGen.get() || !BCBuildersConfig.generateQuarryPits.get()) {
            return false;
        }
        int x0 = origin.getX() - FRAME_SIZE / 2;
        int z0 = origin.getZ() - FRAME_SIZE / 2;
        int x1 = x0 + FRAME_SIZE - 1;
        int z1 = z0 + FRAME_SIZE - 1;

        // Site check: dry, nearly level ground (h = the first free Y above each column's ground).
        int hMin = Integer.MAX_VALUE;
        int hMax = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int h = groundHeight(level, pos, x, z);
                if (h == Integer.MIN_VALUE) {
                    return false;
                }
                hMin = Math.min(hMin, h);
                hMax = Math.max(hMax, h);
            }
        }
        if (hMax - hMin > MAX_ROUGHNESS || level.isOutsideBuildHeight(hMax + FRAME_HEIGHT)) {
            return false;
        }
        int baseY = hMax;

        // Depth: count the diggable layers below the base, stopping above deepslate, fluid or bedrock.
        int diggable = 0;
        while (isLayerDiggable(level, pos, x0 + 1, z0 + 1, x1 - 1, z1 - 1, baseY - diggable - 1)) {
            diggable++;
        }
        if (diggable < MIN_DEPTH) {
            return false;
        }
        int fullLayers = MIN_DEPTH + random.nextInt(diggable - MIN_DEPTH + 1);

        // Clear the mining box (frame interior, from just under the frame top) down through the full layers, and any
        // tree standing over it, which would otherwise be left floating above the pit.
        Set<BlockPos> fluidNeighbours = new HashSet<>();
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            for (int z = z0 + 1; z <= z1 - 1; z++) {
                for (int y = baseY + FRAME_HEIGHT; !level.isOutsideBuildHeight(y); y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                        break;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int y = baseY + FRAME_HEIGHT - 1; y >= baseY - fullLayers; y--) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                for (int z = z0 + 1; z <= z1 - 1; z++) {
                    dig(level, pos.set(x, y, z), fluidNeighbours);
                }
            }
        }

        // The layer the quarry was working on when it was abandoned: some prefix of its walk.
        int partialY = baseY - fullLayers - 1;
        if (fullLayers < diggable) {
            BlockPos layerMin = new BlockPos(x0 + 1, partialY, z0 + 1);
            BlockPos layerMax = new BlockPos(x1 - 1, partialY, z1 - 1);
            EnumAxisOrder axisOrder = random.nextBoolean() ? EnumAxisOrder.XZY : EnumAxisOrder.ZXY;
            AxisOrder.Inversion inv = AxisOrder.Inversion.getFor(random.nextBoolean(), random.nextBoolean(), false);
            BoxIterator walk = new BoxIterator(layerMin, layerMax, AxisOrder.getFor(axisOrder, inv), true);
            int interior = (FRAME_SIZE - 2) * (FRAME_SIZE - 2);
            int mined = random.nextInt(interior);
            for (int i = 0; i < mined && walk.hasNext(); i++) {
                dig(level, pos.set(walk.next()), fluidNeighbours);
            }
        }

        // Let fluids the pit cut into flow in, rather than standing as walls.
        for (BlockPos fluidPos : fluidNeighbours) {
            FluidState fluid = level.getFluidState(fluidPos);
            if (!fluid.isEmpty()) {
                level.scheduleTick(fluidPos, fluid.getType(), 0);
            }
        }

        placeFrame(level, random, x0, baseY, z0);
        return true;
    }

    /** The first free Y above the column's ground, looking through trees and plants (the heightmap counts a tree a
     *  neighbouring chunk has already grown), or MIN_VALUE when the ground is under a fluid. WORLD_SURFACE, not its
     *  _WG twin: the feature step primes it, and a finished chunk (a /place) keeps only the final heightmaps current. */
    private static int groundHeight(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        while (!level.isOutsideBuildHeight(y)) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!state.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (!state.isAir() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.canBeReplaced()) {
                return y + 1;
            }
            y--;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isLayerDiggable(WorldGenLevel level, BlockPos.MutableBlockPos pos, int xMin, int zMin,
            int xMax, int zMax, int y) {
        if (level.isOutsideBuildHeight(y)) {
            return false;
        }
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                BlockState state = level.getBlockState(pos.set(x, y, z));
                if (state.is(Blocks.DEEPSLATE) || state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void dig(WorldGenLevel level, BlockPos.MutableBlockPos pos, Set<BlockPos> fluidNeighbours) {
        if (level.isEmptyBlock(pos)) {
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        for (Direction dir : Direction.values()) {
            if (dir != Direction.DOWN) {
                BlockPos neighbour = pos.relative(dir);
                if (!level.getFluidState(neighbour).isEmpty()) {
                    fluidNeighbours.add(neighbour);
                }
            }
        }
    }

    /** Places the 12 frame edges, some rotted to air or cobweb, with each frame's arms set from its final neighbours
     *  (worldgen writes skip onPlace, which is where a placed frame normally connects). */
    private static void placeFrame(WorldGenLevel level, RandomSource random, int x0, int y0, int z0) {
        int x1 = x0 + FRAME_SIZE - 1;
        int y1 = y0 + FRAME_HEIGHT;
        int z1 = z0 + FRAME_SIZE - 1;
        Set<BlockPos> frames = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    int onFaces = (x == x0 || x == x1 ? 1 : 0) + (y == y0 || y == y1 ? 1 : 0)
                            + (z == z0 || z == z1 ? 1 : 0);
                    if (onFaces < 2) {
                        continue;
                    }
                    BlockPos edge = new BlockPos(x, y, z);
                    if (random.nextFloat() >= FRAME_DECAY_CHANCE) {
                        frames.add(edge);
                    } else if (random.nextFloat() < COBWEB_SHARE) {
                        level.setBlock(edge, Blocks.COBWEB.defaultBlockState(), 2);
                    } else {
                        level.setBlock(edge, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        BlockState frame = BCBuildersBlocks.FRAME.get().defaultBlockState();
        for (BlockPos edge : frames) {
            BlockState state = frame;
            for (Map.Entry<Direction, Property<Boolean>> arm : BlockFrame.CONNECTED_MAP.entrySet()) {
                state = state.setValue(arm.getValue(), frames.contains(edge.relative(arm.getKey())));
            }
            level.setBlock(edge, state, 2);
        }
    }
}
