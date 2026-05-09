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
    public static ModConfigSpec.IntValue smallSpoutMinHeight;
    public static ModConfigSpec.IntValue smallSpoutMaxHeight;
    public static ModConfigSpec.IntValue largeSpoutMinHeight;
    public static ModConfigSpec.IntValue largeSpoutMaxHeight;

    public static ModConfigSpec.DoubleValue smallOilGenProb;
    public static ModConfigSpec.DoubleValue mediumOilGenProb;
    public static ModConfigSpec.DoubleValue largeOilGenProb;

    public static ModConfigSpec.BooleanValue oilIsSticky;

    public static ModConfigSpec.ConfigValue<List<? extends String>> forceExcessiveOilBiomes;
    public static ModConfigSpec.ConfigValue<List<? extends String>> richSurfaceDepositBiomes;
    public static ModConfigSpec.ConfigValue<List<? extends String>> surfaceDepositBiomes;
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
    public static ModConfigSpec.BooleanValue useFullEnergyNames;

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

        smallSpoutMinHeight = builder
                .comment("Minimum height (in blocks) for small oil spouts")
                .defineInRange("smallSpoutMinHeight", 6, -64, 320);
        smallSpoutMaxHeight = builder
                .comment("Maximum height (in blocks) for small oil spouts")
                .defineInRange("smallSpoutMaxHeight", 12, -64, 320);
        largeSpoutMinHeight = builder
                .comment("Minimum height (in blocks) for large oil spouts")
                .defineInRange("largeSpoutMinHeight", 10, -64, 320);
        largeSpoutMaxHeight = builder
                .comment("Maximum height (in blocks) for large oil spouts")
                .defineInRange("largeSpoutMaxHeight", 20, -64, 320);

        builder.pop();
        builder.push("spawnProbability");

        smallOilGenProb = builder
                .comment("Per-chunk probability (0..1) of rolling a small oil deposit. Default 0.005 = 0.5%.")
                .defineInRange("smallOilGenProb", 0.5 / 100, 0.0, 1.0);
        mediumOilGenProb = builder
                .comment("Per-chunk probability (0..1) of rolling a medium oil deposit. Default 0.001 = 0.1%.")
                .defineInRange("mediumOilGenProb", 0.1 / 100, 0.0, 1.0);
        largeOilGenProb = builder
                .comment("Per-chunk probability (0..1) of rolling a large oil deposit. Default 0.0004 = 0.04%.")
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

        // Richest oil tier: largest LAKE-style surface tendrils are gated to these biomes,
        // and they receive the highest bonus multiplier. By default this is deep oceans plus
        // the existing land oil biomes (deserts, badlands).
        richSurfaceDepositBiomes = builder.defineListAllowEmpty(
                "richSurfaceDepositBiomes",
                List.of(
                        "minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean",
                        "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean",
                        "minecraft:desert",
                        "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"
                ),
                () -> "",
                s -> s instanceof String
        );

        // Lighter oil tier: a small bonus multiplier, but no LAKE-style surface tendrils.
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

        excludedBiomes = builder
                .comment("Biome IDs that participate in the include/exclude check below. See biomeListMode.")
                .defineListAllowEmpty(
                        "excludedBiomes",
                        List.of("minecraft:the_void"),
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
    }

    public static void buildDisplay(ModConfigSpec.Builder builder) {
        useRfNaming = builder
                .comment(
                        "If true, use the classic 'Redstone Flux' (RF) names instead of 'Forge Energy' (FE).",
                        "Default is false (FE), matching the modern Forge/NeoForge convention."
                )
                .define("useRfNaming", false);
        useFullEnergyNames = builder
                .comment(
                        "If true, spell energy units in full ('Minecraft Joules', 'Forge Energy', 'Redstone Flux')",
                        "instead of the abbreviated 'MJ', 'FE', and 'RF'. Default is false (abbreviated)."
                )
                .define("useFullEnergyNames", false);
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

    public static Set<Identifier> getExcludedBiomes() {
        return excludedBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getExcludedDimensions() {
        return excludedDimensions.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }
}
