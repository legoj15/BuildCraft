package buildcraft.energy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for BuildCraft Energy.
 */
public class BCEnergyConfig {

    public static ModConfigSpec.BooleanValue enableOilGeneration;
    public static ModConfigSpec.DoubleValue oilWellGenerationRate;

    public static ModConfigSpec.BooleanValue enableOilSpouts;
    public static ModConfigSpec.IntValue finiteSpoutMinHeight;
    public static ModConfigSpec.IntValue finiteSpoutMaxHeight;
    public static ModConfigSpec.IntValue largeSpoutMinHeight;
    public static ModConfigSpec.IntValue largeSpoutMaxHeight;

    public static ModConfigSpec.DoubleValue mediumOilGenProb;
    public static ModConfigSpec.DoubleValue largeOilGenProb;

    public static ModConfigSpec.BooleanValue oilIsSticky;

    public static ModConfigSpec.BooleanValue searingFluidSteam;

    public static ModConfigSpec.ConfigValue<List<? extends String>> forceExcessiveOilBiomes;
    public static ModConfigSpec.ConfigValue<List<? extends String>> richSurfaceDepositBiomes;
    public static ModConfigSpec.ConfigValue<List<? extends String>> surfaceDepositBiomes;
    public static ModConfigSpec.ConfigValue<List<? extends String>> standardSurfaceDepositBiomes;
    public static ModConfigSpec.ConfigValue<List<? extends String>> mountainousSurfaceDepositBiomes;
    public static ModConfigSpec.ConfigValue<List<? extends String>> excludedBiomes;
    public static ModConfigSpec.EnumValue<ListMode> biomeListMode;
    public static ModConfigSpec.ConfigValue<List<? extends String>> excludedDimensions;
    public static ModConfigSpec.EnumValue<ListMode> dimensionListMode;

    /** Polarity for the excludedBiomes / excludedDimensions lists. */
    public enum ListMode {
        BLACKLIST,
        WHITELIST
    }

    public static ModConfigSpec.BooleanValue useRfNaming;
    public static ModConfigSpec.BooleanValue useFullUnitNames;

    public static void buildWorldgen(ModConfigSpec.Builder builder) {
        builder.push("oil");

        enableOilGeneration = builder
                .comment("Set true to enable oil generation")
                .define("enableOilGeneration", true);

        oilWellGenerationRate = builder
                .comment("Multiplier for oil well generation rate")
                .defineInRange("oilWellGenerationRate", 1.0, 0.0, 100.0);

        // enableOilBurn removed — oil flammability isn't implemented in 26.1.1's fluid registration
        // (BCEnergyFluids ignores it). Re-add when oil burn behaviour is ported.

        builder.push("spouts");

        enableOilSpouts = builder
                .comment("Set true to enable oil spouts generating")
                .define("enableOilSpouts", true);

        finiteSpoutMinHeight = builder
                .comment("Minimum height (in blocks above ground) for finite-deposit oil spouts.",
                         "Finite spouts are the surface signature of non-rich-tier formations — a small",
                         "1x1 bump that lets oil overflow naturally. Kept shorter than the rich-tier 3x3",
                         "tower so the two formations are easy to tell apart at a glance.")
                .defineInRange("finiteSpoutMinHeight", 7, -64, 320);
        finiteSpoutMaxHeight = builder
                .comment("Maximum height (in blocks above ground) for finite-deposit oil spouts.")
                .defineInRange("finiteSpoutMaxHeight", 10, -64, 320);
        largeSpoutMinHeight = builder
                .comment("Minimum height (in blocks above ground) for rich-tier infinite-spring spouts.",
                         "These are the tall 3x3 towers that mark a rich-tier LARGE formation (deep ocean,",
                         "desert, mesa by default) with its bedrock-level oil spring. Heights here apply",
                         "ONLY to that case; finite deposits use the finiteSpout range above.")
                .defineInRange("largeSpoutMinHeight", 13, -64, 320);
        largeSpoutMaxHeight = builder
                .comment("Maximum height (in blocks above ground) for rich-tier infinite-spring spouts.")
                .defineInRange("largeSpoutMaxHeight", 20, -64, 320);

        builder.pop();
        builder.push("spawnProbability");

        mediumOilGenProb = builder
                .comment("Per-chunk probability (0..1) of rolling a medium oil deposit in non-rich-land biomes.",
                         "Default 0.001 = 0.1%. Scaled by the tier bonus (1.25x for light ocean, 1.0x for",
                         "standard, 0.5x for the unlisted-biome default, 0.1x for mountainous). Rich-land",
                         "biomes (desert/mesa) use a dedicated rate structure in OilGenerator and ignore",
                         "this value.")
                .defineInRange("mediumOilGenProb", 0.1 / 100, 0.0, 1.0);
        largeOilGenProb = builder
                .comment("Per-chunk probability (0..1) of rolling a large oil deposit in non-rich-land biomes.",
                         "Default 0.0004 = 0.04%. Same scaling and rich-land carve-out as mediumOilGenProb.")
                .defineInRange("largeOilGenProb", 0.04 / 100, 0.0, 1.0);

        builder.pop();

        forceExcessiveOilBiomes = builder
                .comment(
                        "Biome IDs that should always receive a 30x oil bonus (small/medium/large rolls all multiplied).",
                        "Originally targeted BuildCraft's own oil_desert/oil_ocean biomes (which no longer exist);",
                        "can now be used to force vanilla or modded biomes to gush oil. Empty by default."
                )
                .defineListAllowEmpty(
                        "forceExcessiveOilBiomes",
                        List.of(),
                        () -> "",
                        s -> s instanceof String
                );

        // Richest oil tier: 1.5x bonus and the standalone LAKE-style surface roll is gated
        // to these biomes. Land biomes in this tier (desert, badlands) get the new clean
        // surface-pool visual treatment instead of tendrils — LARGE's surface becomes a
        // medium pool, MEDIUM becomes a small pool with no underground or spout (so the
        // tall spout of a LARGE infinite spring is unambiguous), and the LAKE roll
        // produces a large pool. Ocean biomes in this tier keep the existing tendril
        // aesthetic. Defaults: deep oceans + deserts + badlands variants.
        richSurfaceDepositBiomes = builder.defineListAllowEmpty(
                "richSurfaceDepositBiomes",
                List.of(
                        "minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean",
                        "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean",
                        "minecraft:desert",
                        "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands"
                ),
                () -> "",
                s -> s instanceof String
        );

        // Lighter oil tier: a 1.25x bonus multiplier, but no LAKE-style surface tendrils.
        // By default this contains shallow ocean variants only.
        surfaceDepositBiomes = builder.defineListAllowEmpty(
                "surfaceDepositBiomes",
                List.of(
                        "minecraft:ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
                        "minecraft:cold_ocean", "minecraft:frozen_ocean"
                ),
                () -> "",
                s -> s instanceof String
        );

        // Standard oil tier: 1.0x rate (unchanged from the historical default for these
        // biomes). Defaults to biomes that are otherwise low-value to the player —
        // jungles (lots of biomass but mostly hostile/dense), ice_spikes (flat snowy
        // with only polar bears), snowy_beach (a thin frozen transition strip), and
        // frozen_river (a thin iced-over transit biome — distinct enough from vanilla
        // rivers, which are excluded, to support oil here). Biomes not in this list
        // and not in any richer/lighter tier drop to the 0.5x reduced base rate.
        standardSurfaceDepositBiomes = builder.defineListAllowEmpty(
                "standardSurfaceDepositBiomes",
                List.of(
                        "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
                        "minecraft:ice_spikes", "minecraft:snowy_beach", "minecraft:frozen_river"
                ),
                () -> "",
                s -> s instanceof String
        );

        // Mountainous oil tier: 0.1x (10x reduced) rate. Rationale: surface oil seeping
        // through bedrock-grade rock is unrealistic. Includes alpine valley biomes
        // (meadow, grove, cherry_grove) by default; move them out if you'd like more
        // oil there.
        mountainousSurfaceDepositBiomes = builder.defineListAllowEmpty(
                "mountainousSurfaceDepositBiomes",
                List.of(
                        "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                        "minecraft:windswept_forest", "minecraft:jagged_peaks",
                        "minecraft:frozen_peaks", "minecraft:stony_peaks",
                        "minecraft:snowy_slopes", "minecraft:meadow", "minecraft:grove",
                        "minecraft:cherry_grove"
                ),
                () -> "",
                s -> s instanceof String
        );

        excludedBiomes = builder
                .comment("Biome IDs that participate in the include/exclude check below. See biomeListMode.",
                         "Vanilla rivers are excluded by default — they're thin transit biomes where an oil",
                         "deposit would just look like a polluted creek, and the spout's column of source",
                         "blocks tends to surface awkwardly across the narrow river bed. (Frozen rivers are",
                         "left in, classified as standard tier — they're functionally the same shape but",
                         "their iced-over surface gives them a more isolated, frontier-like feel.)")
                .defineListAllowEmpty(
                        "excludedBiomes",
                        List.of("minecraft:the_void", "minecraft:river"),
                        () -> "",
                        s -> s instanceof String
                );

        biomeListMode = builder
                .comment(
                        "BLACKLIST: oil never generates in biomes from the excludedBiomes list.",
                        "WHITELIST: oil only generates in biomes from the excludedBiomes list."
                )
                .defineEnum("biomeListMode", ListMode.BLACKLIST);

        excludedDimensions = builder
                .comment("Dimension IDs that participate in the include/exclude check below. See dimensionListMode.")
                .defineListAllowEmpty(
                        "excludedDimensions",
                        List.of("minecraft:the_nether", "minecraft:the_end"),
                        () -> "",
                        s -> s instanceof String
                );

        dimensionListMode = builder
                .comment(
                        "BLACKLIST: oil never generates in dimensions from the excludedDimensions list.",
                        "WHITELIST: oil only generates in dimensions from the excludedDimensions list."
                )
                .defineEnum("dimensionListMode", ListMode.BLACKLIST);

        builder.pop();
    }

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        oilIsSticky = builder
                .comment("If true, oil source blocks slow down entities standing or moving through them")
                .define("oilIsSticky", false);

        searingFluidSteam = builder
                .comment("Whether or not Searing fluids emit visual steam",
                         "particles when placed in the world. Cosmetic only.")
                .define("searingFluidSteam", true);
    }

    public static void buildDisplay(ModConfigSpec.Builder builder) {
        useRfNaming = builder
                .comment(
                        "If true, use the classic 'Redstone Flux' (RF) names instead of 'Forge Energy' (FE).",
                        "Default is false (FE), matching the modern Forge/NeoForge convention."
                )
                .define("useRfNaming", false);
        useFullUnitNames = builder
                .comment(
                        "If true, spell units in full ('Minecraft Joules', 'Forge Energy', 'Redstone Flux',",
                        "'millibuckets') AND spell out time suffixes (' per second' / ' per tick') instead of",
                        "the abbreviated 'MJ'/'FE'/'RF'/'mB' units and '/s'/'/t' suffixes. Also applies to fluid",
                        "pipe tooltips. Default is true (full names + spelled-out time suffix, matching 1.12.2);",
                        "set false to get the compact 'MJ/s'/'FE/t'/'mB/t' form back."
                )
                .define("useFullUnitNames", true);
    }

    /**
     * Returns the translation key to use given a base (FE-default) key. When the
     * {@code useRfNaming} toggle is on, returns {@code baseKey + ".rf"} so callers
     * can pick up the RF-named sibling entry from the lang file.
     */
    public static String rfFeKey(String baseKey) {
        return useRfNaming != null && useRfNaming.get() ? baseKey + ".rf" : baseKey;
    }

    public static Set<Identifier> getForceExcessiveOilBiomes() {
        return forceExcessiveOilBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getSurfaceDepositBiomes() {
        return surfaceDepositBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getRichSurfaceDepositBiomes() {
        return richSurfaceDepositBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getStandardSurfaceDepositBiomes() {
        return standardSurfaceDepositBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getMountainousSurfaceDepositBiomes() {
        return mountainousSurfaceDepositBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getExcludedBiomes() {
        return excludedBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getExcludedDimensions() {
        return excludedDimensions.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }
}
