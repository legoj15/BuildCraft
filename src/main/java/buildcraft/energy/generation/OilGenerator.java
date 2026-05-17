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
     * Whether oil generation is permitted in this level at all (regardless of biome
     * and the per-chunk RNG roll). Combines the flat-world veto and the dimension
     * include/exclude config check that {@link #generateForChunk} already performs.
     * <p>
     * Extracted so that observers of the oil-gen pipeline (e.g. the
     * {@code fine_riches} advancement handler in {@code BCEnergyWorldGen}) can ask
     * the same question without re-running generation.
     */
    public static boolean canGenerateOilIn(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource) {
            return false;
        }
        return !isDimensionExcluded(level.dimension());
    }

    /**
     * Composite predicate: would {@link #generateForChunk} place oil whose origin is
     * the given chunk, given the current world and config state? This mirrors what
     * {@code generateForChunk} actually does for the centre cell of its 5-chunk loop
     * — {@link #canGenerateOilIn} (level-wide veto) AND {@link #getStructures}
     * non-empty (biome + per-chunk RNG roll). Oil that spills in from a neighbouring
     * origin chunk is intentionally NOT counted here; callers tracking per-chunk
     * events will see the neighbour fire on its own watch.
     */
    public static boolean wouldGenerateOilForOriginChunk(ServerLevel level, int chunkX, int chunkZ) {
        return canGenerateOilIn(level) && !getStructures(level, chunkX, chunkZ).isEmpty();
    }

    /**
     * Whether the given biome ID is one the player can reasonably call an
     * "oil biome" for the {@code fine_riches} advancement: the rich tier or
     * the (debug) excessive tier. <b>Excludes the light tier on purpose.</b>
     * <p>
     * Why excluded: light tier (shallow ocean variants by default) gets a 1.25×
     * bonus on the chunk roll, but a MEDIUM roll there produces (a) a 4-block
     * surface tendril at y=62 — inside the water column for an ocean, easy to
     * swim past unnoticed — plus (b) an underground sphere at y=minY+25..34
     * and (c) a small spout that extends only 6-12 blocks up from there, ending
     * around y=-33..-18 in a modern overworld. None of those reach the seafloor,
     * let alone the surface, so the player rolls "oil found" without anything
     * visible. A small patch of light-tier biome inside a non-oil region (e.g.
     * 2-3 chunks of cold_ocean inside a taiga) then false-fires the advancement.
     * <p>
     * Rich-tier biomes (deep oceans, deserts, badlands by default) avoid this
     * because they're either deeper-water (so the surface pool is reachable by
     * swimming) or terrestrial (oil clearly visible on land). Excessive tier is
     * an explicit admin opt-in so we honour it regardless.
     * <p>
     * Also excludes the implicit 1.0× base tier (any other biome), which was
     * the previous bug — getStructures rolls LARGE/MEDIUM in any biome at the
     * base rate, so without any tier gate the advancement fires anywhere at
     * large render distances.
     */
    public static boolean isOilDesignBiome(Identifier biomeId) {
        return BCEnergyConfig.getRichSurfaceDepositBiomes().contains(biomeId)
                || BCEnergyConfig.getForceExcessiveOilBiomes().contains(biomeId);
    }

    /**
     * Stricter sibling of {@link #wouldGenerateOilForOriginChunk}: additionally
     * requires the sampled biome to be in an oil-design tier (see
     * {@link #isOilDesignBiome}). This is what the {@code fine_riches} advancement
     * handler calls — it stops the advancement from firing in unrelated biomes
     * that happen to roll an oil deposit via the per-chunk RNG.
     * <p>
     * Re-derives {@code getStructures}'s sample point from the same deterministic
     * chunk RNG so chunks straddling a biome border are evaluated against the same
     * biome the actual roll uses. The biome lookup runs twice (once here, once
     * inside {@code getStructures}) — accepted for code simplicity, marginal cost.
     */
    public static boolean wouldGenerateOilForOriginChunkInOilBiome(ServerLevel level, int chunkX, int chunkZ) {
        if (!canGenerateOilIn(level)) return false;
        if (!isOilDesignBiome(sampleBiomeForChunkRoll(level, chunkX, chunkZ))) return false;
        return !getStructures(level, chunkX, chunkZ).isEmpty();
    }

    /** Replays {@link #getStructures}'s sample-point derivation so the biome
     * checked here matches the biome the actual roll evaluates against. */
    private static Identifier sampleBiomeForChunkRoll(Level level, int cx, int cz) {
        Random rand = RandUtil.createRandomForChunk(level, cx, cz, MAGIC_GEN_NUMBER);
        int x = cx * 16 + 8 + rand.nextInt(16);
        int z = cz * 16 + 8 + rand.nextInt(16);
        return Identifier.parse(level.getBiome(new BlockPos(x, 64, z)).getRegisteredName());
    }

    /** Convenience: {@code isOilDesignBiome} applied to the biome
     * {@link #getStructures} would sample for the given chunk. Used by the
     * {@code fine_riches} handler to check whether the player's current chunk
     * is an oil-design biome, before scanning the 3×3 neighbourhood for an
     * actual rolled oil deposit. */
    public static boolean isOilDesignBiomeAt(Level level, int chunkX, int chunkZ) {
        return isOilDesignBiome(sampleBiomeForChunkRoll(level, chunkX, chunkZ));
    }

    /**
     * Called from {@link buildcraft.energy.BCEnergyWorldGen} when a chunk is loaded for the first time.
     * Generates oil structures that overlap with this chunk.
     */
    public static void generateForChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (!canGenerateOilIn(level)) {
            if (DEBUG_OILGEN_BASIC) {
                String reason = level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource
                        ? "the world is FLAT" : "dimension is excluded";
                BCLog.logger.info("[energy.oilgen] Not generating oil in chunk " + chunkX + ", " + chunkZ
                    + " because " + reason + ".");
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
        boolean inList = BCEnergyConfig.getExcludedDimensions().contains(dimKey.identifier());
        return BCEnergyConfig.dimensionListMode.get() == BCEnergyConfig.ListMode.BLACKLIST ? inList : !inList;
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
        boolean biomeBlacklisted = BCEnergyConfig.biomeListMode.get() == BCEnergyConfig.ListMode.BLACKLIST;
        if (isExcludedBiome == biomeBlacklisted) {
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

        // Two-tier surface-deposit classification:
        //   richBiome  → 1.5x bonus, eligible for the LAKE-style surface tendril roll
        //   oilBiome   → 1.25x bonus, NOT eligible for LAKE (smaller wells only)
        //   otherwise  → 1.0x bonus, NOT eligible for LAKE
        // Defaults put deep oceans + deserts + badlands in the rich tier and shallow
        // ocean variants in the light tier; see BCEnergyConfig.
        boolean richBiome = BCEnergyConfig.getRichSurfaceDepositBiomes().contains(biomeId);
        boolean oilBiome = richBiome || BCEnergyConfig.getSurfaceDepositBiomes().contains(biomeId);

        double bonus = richBiome ? 1.5 : (oilBiome ? 1.25 : 1.0);
        bonus *= BCEnergyConfig.oilWellGenerationRate.get();
        if (BCEnergyConfig.getForceExcessiveOilBiomes().contains(biomeId)) {
            bonus *= 30.0;
        }
        final GenType type;

        if (rand.nextDouble() <= BCEnergyConfig.largeOilGenProb.get() * bonus) {
            type = GenType.LARGE;
        } else if (rand.nextDouble() <= BCEnergyConfig.mediumOilGenProb.get() * bonus) {
            type = GenType.MEDIUM;
        } else if (richBiome && rand.nextDouble() <= BCEnergyConfig.smallOilGenProb.get() * bonus) {
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
            // Generate a spherical cave deposit.
            // In 1.12.2 bedrock was a single flat layer at Y=0, so wellY=20..29 placed
            // the cavity 20-29 blocks above bedrock. Modern worlds have a 5-block bedrock
            // gradient from minY to minY+4. We use a base offset of 25 so the sphere
            // (with max large radius of 16) never clips into the bedrock gradient:
            //   sphere bottom = (minY + 25) - 16 = minY + 9, safely above minY + 4.
            int wellY = level.getMinY() + 25 + rand.nextInt(10);

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

            // Generate a spring at the very bottom, with a + shaped tube
            // connecting the cavity down through the bedrock gradient.
            //   minY:   solid bedrock floor (always 100%, prevents void access)
            //   minY+1: spring block (sits on bedrock; any air neighbors are safe)
            //   minY+2: single oil block above spring (force-placed by Spring.generate)
            //   minY+3 → wellY: + shaped oil tube, ALWAYS replaces everything
            //           (including bedrock) to create clear line-of-sight
            if (type == GenType.LARGE) {
                int tubeStart = level.getMinY() + 3;
                int tubeLength = wellY - tubeStart;
                structures.add(createTube(new BlockPos(x, tubeStart, z), tubeLength, radius, Axis.Y));
                if (BCCoreBlocks.SPRING_OIL != null) {
                    structures.add(createSpring(new BlockPos(x, level.getMinY() + 1, z)));
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
        return createTube(center, length, radius, axis, ReplaceType.ALWAYS);
    }

    public static OilGenStructure createTube(BlockPos center, int length, int radius, Axis axis, ReplaceType replaceType) {
        int valForAxis = VecUtil.getValue(center, axis);
        BlockPos min = VecUtil.replaceValue(center.offset(-radius, -radius, -radius), axis, valForAxis);
        BlockPos max = VecUtil.replaceValue(center.offset(radius, radius, radius), axis, valForAxis + length);
        double radiusSq = radius * radius;
        int toReplace = valForAxis;
        Predicate<BlockPos> tester = p -> VecUtil.replaceValue(p, axis, toReplace).distSqr(center) <= radiusSq;
        return new GenByPredicate(new Box(min, max), replaceType, tester);
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
