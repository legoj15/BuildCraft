package buildcraft.energy.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

import buildcraft.lib.misc.RandUtil;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.Box;

import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.BCEnergyConfig;
import buildcraft.energy.generation.OilGenStructure.GenByPredicate;
import buildcraft.energy.generation.OilGenStructure.ReplaceType;

public class OilGenerator {
    private OilGenerator() {}

    /** Random number, used to differentiate generators */
    private static final long MAGIC_GEN_NUMBER = 0xD0_46_B4_E4_0C_7D_07_CFL;

    /** The distance that oil generation will be checked to see if their structures overlap with the currently
     * generating chunk. */
    private static final int MAX_CHUNK_RADIUS = 5;

    public static final boolean DEBUG_OILGEN_BASIC = BCDebugging.shouldDebugLog("energy.oilgen");
    public static final boolean DEBUG_OILGEN_ALL = BCDebugging.shouldDebugComplex("energy.oilgen");

    private enum GenType {
        LARGE,
        MEDIUM,
        LAKE,
        NONE
    }

    /**
     * Called from {@link buildcraft.energy.BCEnergyWorldGen} when a chunk is loaded for the first time.
     * Generates oil structures that overlap with this chunk.
     */
    public static void generateForChunk(ServerLevel level, int chunkX, int chunkZ) {
        // Don't generate in flat worlds
        if (level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource) {
            if (DEBUG_OILGEN_BASIC) {
                BCLog.logger.info("[energy.oilgen] Not generating oil in chunk " + chunkX + ", " + chunkZ
                    + " because the world is FLAT.");
            }
            return;
        }

        // Check dimension exclusion by comparing ResourceKey directly
        ResourceKey<Level> dimKey = level.dimension();
        if (isDimensionExcluded(dimKey)) {
            if (DEBUG_OILGEN_BASIC) {
                BCLog.logger.info("[energy.oilgen] Not generating oil in chunk " + chunkX + ", " + chunkZ
                    + " because dimension is excluded.");
            }
            return;
        }

        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        BlockPos min = new BlockPos(x, level.getMinY(), z);
        BlockPos maxPos = new BlockPos(x + 15, level.getMaxY(), z + 15);
        Box box = new Box(min, maxPos);

        for (int cdx = -MAX_CHUNK_RADIUS; cdx <= MAX_CHUNK_RADIUS; cdx++) {
            for (int cdz = -MAX_CHUNK_RADIUS; cdz <= MAX_CHUNK_RADIUS; cdz++) {
                int cx = chunkX + cdx;
                int cz = chunkZ + cdz;
                List<OilGenStructure> structures = getStructures(level, cx, cz, cdx == 0 && cdz == 0);
                OilGenStructure.Spring spring = null;
                for (OilGenStructure struct : structures) {
                    struct.generate(level, box);
                    if (struct instanceof OilGenStructure.Spring) {
                        spring = (OilGenStructure.Spring) struct;
                    }
                }
                if (spring != null && box.contains(spring.pos)) {
                    int count = 0;
                    for (OilGenStructure struct : structures) {
                        count += struct.countOilBlocks();
                    }
                    spring.generate(level, count);
                }
            }
        }
    }

    /** Check if the dimension is excluded based on config. */
    private static boolean isDimensionExcluded(ResourceKey<Level> dimKey) {
        if (BCEnergyConfig.getExcludedDimensions().contains(dimKey.identifier())) {
            return BCEnergyConfig.excludedDimensionsIsBlackList.get();
        }
        return !BCEnergyConfig.excludedDimensionsIsBlackList.get();
    }

    public static List<OilGenStructure> getStructures(Level level, int cx, int cz) {
        return getStructures(level, cx, cz, false);
    }

    private static List<OilGenStructure> getStructures(Level level, int cx, int cz, boolean log) {
        Random rand = RandUtil.createRandomForChunk(level, cx, cz, MAGIC_GEN_NUMBER);

        // shift to world coordinates
        int x = cx * 16 + 8 + rand.nextInt(16);
        int z = cz * 16 + 8 + rand.nextInt(16);

        Holder<Biome> biomeHolder = level.getBiome(new BlockPos(x, 64, z));
        // Get the biome's Identifier from the holder's registered name
        String registeredName = biomeHolder.getRegisteredName();
        Identifier biomeId = Identifier.parse(registeredName);

        // Do not generate oil in excluded biomes
        boolean isExcludedBiome = BCEnergyConfig.getExcludedBiomes().contains(biomeId);
        if (isExcludedBiome == BCEnergyConfig.excludedBiomesIsBlackList.get()) {
            if (DEBUG_OILGEN_BASIC & log) {
                BCLog.logger.info("[energy.oilgen] Not generating oil in chunk " + cx + ", " + cz
                    + " because the biome (" + biomeId + ") is excluded!");
            }
            return ImmutableList.of();
        }

        // Skip end biome near dragon fight
        if (biomeId.equals(Identifier.withDefaultNamespace("the_end"))
                && (Math.abs(x) < 1200 || Math.abs(z) < 1200)) {
            return ImmutableList.of();
        }

        boolean oilBiome = BCEnergyConfig.getSurfaceDepositBiomes().contains(biomeId);

        double bonus = oilBiome ? 3.0 : 1.0;
        bonus *= BCEnergyConfig.oilWellGenerationRate.get();
        if (BCEnergyConfig.getExcessiveBiomes().contains(biomeId)) {
            bonus *= 30.0;
        }
        final GenType type;

        if (rand.nextDouble() <= BCEnergyConfig.largeOilGenProb.get() * bonus) {
            type = GenType.LARGE;
        } else if (rand.nextDouble() <= BCEnergyConfig.mediumOilGenProb.get() * bonus) {
            type = GenType.MEDIUM;
        } else if (oilBiome && rand.nextDouble() <= BCEnergyConfig.smallOilGenProb.get() * bonus) {
            type = GenType.LAKE;
        } else {
            if (DEBUG_OILGEN_ALL & log) {
                BCLog.logger.info("[energy.oilgen] Not generating oil in chunk " + cx + ", " + cz
                    + " because none of the random numbers were above the thresholds.");
            }
            return ImmutableList.of();
        }
        if (DEBUG_OILGEN_BASIC & log) {
            BCLog.logger.info("[energy.oilgen] Generating an oil well (" + type.name().toLowerCase(Locale.ROOT)
                + ") in chunk " + cx + ", " + cz + " at " + x + ", " + z);
        }

        List<OilGenStructure> structures = new ArrayList<>();
        int lakeRadius;
        int tendrilRadius;
        if (type == GenType.LARGE) {
            lakeRadius = 4;
            tendrilRadius = 25 + rand.nextInt(20);
        } else if (type == GenType.LAKE) {
            lakeRadius = 6;
            tendrilRadius = 25 + rand.nextInt(20);
        } else {
            lakeRadius = 2;
            tendrilRadius = 5 + rand.nextInt(10);
        }
        structures.add(createTendril(new BlockPos(x, 62, z), lakeRadius, tendrilRadius, rand));

        if (type != GenType.LAKE) {
            // Generate a spherical cave deposit
            int wellY = 20 + rand.nextInt(10);

            int radius;
            if (type == GenType.LARGE) {
                radius = 8 + rand.nextInt(9);
            } else {
                radius = 4 + rand.nextInt(4);
            }

            structures.add(createSphere(new BlockPos(x, wellY, z), radius));

            // Generate a spout
            if (BCEnergyConfig.enableOilSpouts.get()) {
                int maxHeight, minHeight;

                if (type == GenType.LARGE) {
                    minHeight = BCEnergyConfig.largeSpoutMinHeight.get();
                    maxHeight = BCEnergyConfig.largeSpoutMaxHeight.get();
                    radius = 1;
                } else {
                    minHeight = BCEnergyConfig.smallSpoutMinHeight.get();
                    maxHeight = BCEnergyConfig.smallSpoutMaxHeight.get();
                    radius = 0;
                }
                final int height;
                if (maxHeight == minHeight) {
                    height = maxHeight;
                } else {
                    if (maxHeight < minHeight) {
                        int t = maxHeight;
                        maxHeight = minHeight;
                        minHeight = t;
                    }
                    height = minHeight + rand.nextInt(maxHeight - minHeight);
                }
                structures.add(createSpout(new BlockPos(x, wellY, z), height, radius));
            }

            // Generate a spring at the very bottom
            if (type == GenType.LARGE) {
                structures.add(createTube(new BlockPos(x, level.getMinY() + 1, z), wellY, radius, Axis.Y));
                if (BCCoreBlocks.SPRING_OIL != null) {
                    structures.add(createSpring(new BlockPos(x, level.getMinY(), z)));
                }
            }
        }
        return structures;
    }

    private static OilGenStructure createSpout(BlockPos start, int height, int radius) {
        return new OilGenStructure.Spout(start, ReplaceType.ALWAYS, radius, height);
    }

    public static OilGenStructure createTubeY(BlockPos base, int height, int radius) {
        return createTube(base, height, radius, Axis.Y);
    }

    public static OilGenStructure createSpring(BlockPos at) {
        return new OilGenStructure.Spring(at);
    }

    public static OilGenStructure createTube(BlockPos center, int length, int radius, Axis axis) {
        int valForAxis = VecUtil.getValue(center, axis);
        BlockPos min = VecUtil.replaceValue(center.offset(-radius, -radius, -radius), axis, valForAxis);
        BlockPos max = VecUtil.replaceValue(center.offset(radius, radius, radius), axis, valForAxis + length);
        double radiusSq = radius * radius;
        int toReplace = valForAxis;
        Predicate<BlockPos> tester = p -> VecUtil.replaceValue(p, axis, toReplace).distSqr(center) <= radiusSq;
        return new GenByPredicate(new Box(min, max), ReplaceType.ALWAYS, tester);
    }

    public static OilGenStructure createSphere(BlockPos center, int radius) {
        Box box = new Box(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius));
        double radiusSq = radius * radius + 0.01;
        Predicate<BlockPos> tester = p -> p.distSqr(center) <= radiusSq;
        return new GenByPredicate(box, ReplaceType.ALWAYS, tester);
    }

    public static OilGenStructure createTendril(BlockPos center, int lakeRadius, int radius, Random rand) {
        BlockPos start = center.offset(-radius, 0, -radius);
        int diameter = radius * 2 + 1;
        boolean[][] pattern = new boolean[diameter][diameter];

        int x = radius;
        int z = radius;
        for (int dx = -lakeRadius; dx <= lakeRadius; dx++) {
            for (int dz = -lakeRadius; dz <= lakeRadius; dz++) {
                pattern[x + dx][z + dz] = dx * dx + dz * dz <= lakeRadius * lakeRadius;
            }
        }

        for (int w = 1; w < radius; w++) {
            float proba = (float) (radius - w + 4) / (float) (radius + 4);

            fillPatternIfProba(rand, proba, x, z + w, pattern);
            fillPatternIfProba(rand, proba, x, z - w, pattern);
            fillPatternIfProba(rand, proba, x + w, z, pattern);
            fillPatternIfProba(rand, proba, x - w, z, pattern);

            for (int i = 1; i <= w; i++) {
                fillPatternIfProba(rand, proba, x + i, z + w, pattern);
                fillPatternIfProba(rand, proba, x + i, z - w, pattern);
                fillPatternIfProba(rand, proba, x + w, z + i, pattern);
                fillPatternIfProba(rand, proba, x - w, z + i, pattern);

                fillPatternIfProba(rand, proba, x - i, z + w, pattern);
                fillPatternIfProba(rand, proba, x - i, z - w, pattern);
                fillPatternIfProba(rand, proba, x + w, z - i, pattern);
                fillPatternIfProba(rand, proba, x - w, z - i, pattern);
            }
        }

        int depth = rand.nextDouble() < 0.5 ? 1 : 2;
        return OilGenStructure.PatternTerrainHeight.create(start, ReplaceType.IS_FOR_LAKE, pattern, depth);
    }

    private static void fillPatternIfProba(Random rand, float proba, int x, int z, boolean[][] pattern) {
        if (rand.nextFloat() <= proba) {
            pattern[x][z] = isSet(pattern, x, z - 1) | isSet(pattern, x, z + 1)
                | isSet(pattern, x - 1, z) | isSet(pattern, x + 1, z);
        }
    }

    private static boolean isSet(boolean[][] pattern, int x, int z) {
        if (x < 0 || x >= pattern.length) return false;
        if (z < 0 || z >= pattern[x].length) return false;
        return pattern[x][z];
    }
}
