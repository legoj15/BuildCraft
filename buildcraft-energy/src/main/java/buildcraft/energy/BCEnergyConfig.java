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

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue enableOilGeneration;
    public static final ModConfigSpec.DoubleValue oilWellGenerationRate;

    public static final ModConfigSpec.BooleanValue enableOilSpouts;
    public static final ModConfigSpec.IntValue smallSpoutMinHeight;
    public static final ModConfigSpec.IntValue smallSpoutMaxHeight;
    public static final ModConfigSpec.IntValue largeSpoutMinHeight;
    public static final ModConfigSpec.IntValue largeSpoutMaxHeight;

    public static final ModConfigSpec.DoubleValue smallOilGenProb;
    public static final ModConfigSpec.DoubleValue mediumOilGenProb;
    public static final ModConfigSpec.DoubleValue largeOilGenProb;

    public static final ModConfigSpec.BooleanValue oilIsSticky;
    public static final ModConfigSpec.BooleanValue enableOilBurn;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> excessiveBiomes;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> surfaceDepositBiomes;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> excludedBiomes;
    public static final ModConfigSpec.BooleanValue excludedBiomesIsBlackList;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> excludedDimensions;
    public static final ModConfigSpec.BooleanValue excludedDimensionsIsBlackList;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("oil_generation");

        enableOilGeneration = builder
                .comment("Set true to enable oil generation")
                .define("enableOilGeneration", true);

        oilWellGenerationRate = builder
                .comment("Multiplier for oil well generation rate")
                .defineInRange("oilWellGenerationRate", 1.0, 0.0, 100.0);

        enableOilSpouts = builder
                .comment("Set true to enable oil spouts generating")
                .define("enableOilSpouts", true);

        smallSpoutMinHeight = builder.defineInRange("smallSpoutMinHeight", 6, 0, 256);
        smallSpoutMaxHeight = builder.defineInRange("smallSpoutMaxHeight", 12, 0, 256);
        largeSpoutMinHeight = builder.defineInRange("largeSpoutMinHeight", 10, 0, 256);
        largeSpoutMaxHeight = builder.defineInRange("largeSpoutMaxHeight", 20, 0, 256);

        smallOilGenProb = builder.defineInRange("smallOilGenProb", 2.0 / 100, 0.0, 1.0);
        mediumOilGenProb = builder.defineInRange("mediumOilGenProb", 0.1 / 100, 0.0, 1.0);
        largeOilGenProb = builder.defineInRange("largeOilGenProb", 0.04 / 100, 0.0, 1.0);

        builder.pop();
        builder.push("oil_properties");

        oilIsSticky = builder.define("oilIsSticky", false);
        enableOilBurn = builder.define("enableOilBurn", true);

        builder.pop();
        builder.push("biomes_and_dimensions");

        excessiveBiomes = builder.defineListAllowEmpty(
                "excessiveBiomes",
                List.of(),
                () -> "",
                s -> s instanceof String
        );

        surfaceDepositBiomes = builder.defineListAllowEmpty(
                "surfaceDepositBiomes",
                List.of(
                        "minecraft:desert", "minecraft:ocean", "minecraft:deep_ocean",
                        "minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean",
                        "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean",
                        "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"
                ),
                () -> "",
                s -> s instanceof String
        );

        excludedBiomes = builder.defineListAllowEmpty(
                "excludedBiomes",
                List.of("minecraft:the_void"),
                () -> "",
                s -> s instanceof String
        );

        excludedBiomesIsBlackList = builder.define("excludedBiomesIsBlackList", true);

        excludedDimensions = builder.defineListAllowEmpty(
                "excludedDimensions",
                List.of("minecraft:the_nether", "minecraft:the_end"),
                () -> "",
                s -> s instanceof String
        );

        excludedDimensionsIsBlackList = builder.define("excludedDimensionsIsBlackList", true);

        builder.pop();
        SPEC = builder.build();
    }

    public static Set<Identifier> getExcessiveBiomes() {
        return excessiveBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getSurfaceDepositBiomes() {
        return surfaceDepositBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getExcludedBiomes() {
        return excludedBiomes.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }

    public static Set<Identifier> getExcludedDimensions() {
        return excludedDimensions.get().stream().map(Identifier::parse).collect(Collectors.toSet());
    }
}
