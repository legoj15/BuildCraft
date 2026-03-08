package buildcraft.energy;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.Identifier;

/**
 * Simplified configuration for BuildCraft Energy.
 * Uses static fields with sensible defaults. A proper ModConfigSpec can be added later.
 */
public class BCEnergyConfig {

    // --- Oil generation master switch ---
    public static boolean enableOilGeneration = true;
    public static double oilWellGenerationRate = 1.0;

    // --- Oil spouts ---
    public static boolean enableOilSpouts = true;
    public static int smallSpoutMinHeight = 6;
    public static int smallSpoutMaxHeight = 12;
    public static int largeSpoutMinHeight = 10;
    public static int largeSpoutMaxHeight = 20;

    // --- Generation probabilities (percentage, divided by 100 at use) ---
    public static double smallOilGenProb = 2.0 / 100;    // 2%
    public static double mediumOilGenProb = 0.1 / 100;   // 0.1%
    public static double largeOilGenProb = 0.04 / 100;   // 0.04%

    // --- Oil stickiness / burn ---
    public static boolean oilIsSticky = false;
    public static boolean enableOilBurn = true;

    // --- Biome lists ---
    /** Biomes that get a 30x oil generation bonus. */
    public static final Set<Identifier> excessiveBiomes = new HashSet<>();

    /** Biomes that get a 3x bonus and allow surface lakes (smallOilGenProb). */
    public static final Set<Identifier> surfaceDepositBiomes = new HashSet<>();

    /** Biomes where oil generation is disabled. */
    public static final Set<Identifier> excludedBiomes = new HashSet<>();

    /** If true, excludedBiomes is a blacklist; if false, a whitelist. */
    public static boolean excludedBiomesIsBlackList = true;

    /** Dimension keys where oil generation is disabled. */
    public static final Set<Identifier> excludedDimensions = new HashSet<>();

    /** If true, excludedDimensions is a blacklist; if false, a whitelist. */
    public static boolean excludedDimensionsIsBlackList = true;

    static {
        // Default: no oil in the nether or end
        excludedDimensions.add(Identifier.withDefaultNamespace("the_nether"));
        excludedDimensions.add(Identifier.withDefaultNamespace("the_end"));

        // Default excluded biomes (equivalent to 1.12 minecraft:hell, minecraft:sky)
        excludedBiomes.add(Identifier.withDefaultNamespace("the_void"));

        // Default surface deposit biomes — deserts and oceans get surface lakes
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("desert"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("ocean"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("deep_ocean"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("warm_ocean"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("lukewarm_ocean"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("deep_lukewarm_ocean"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("cold_ocean"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("deep_cold_ocean"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("deep_frozen_ocean"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("badlands"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("eroded_badlands"));
        surfaceDepositBiomes.add(Identifier.withDefaultNamespace("wooded_badlands"));
    }
}
