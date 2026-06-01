package buildcraft.core;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BCCoreConfig {

    public static ModConfigSpec.BooleanValue worldGen;
    public static ModConfigSpec.BooleanValue minePlayerProtected;
    public static ModConfigSpec.BooleanValue pumpsConsumeWater;
    public static ModConfigSpec.IntValue markerMaxDistance;
    public static ModConfigSpec.IntValue pumpMaxDistance;
    public static ModConfigSpec.IntValue networkUpdateRate;
    public static ModConfigSpec.DoubleValue miningMultiplier;
    public static ModConfigSpec.IntValue miningMaxDepth;

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        minePlayerProtected = builder
                .comment(
                        "If true, mining machines (Quarry, Mining Well, Builder/Filler clear-mode) " +
                                "ignore third-party protection mods and break player-protected blocks. " +
                                "Default false respects block-break protection from mods like " +
                                "FTB Chunks or GriefPrevention. Does not affect Pumps."
                )
                .define("minePlayerProtected", false);

        pumpsConsumeWater = builder
                .comment(
                        "If true, pumps will consume fluid source blocks from infinite pools " +
                                "instead of just simulating the pumping action. " +
                                "Has a negative impact on performance when true, enable for " +
                                "mods that disable infinite-water regeneration."
                )
                .define("pumpsConsumeWater", false);

        markerMaxDistance = builder
                .comment("Maximum distance in blocks a marker volume can connect")
                .defineInRange("markerMaxDistance", 64, 1, 256);

        pumpMaxDistance = builder
                .comment("Maximum distance in blocks a pump can suck liquids")
                .defineInRange("pumpMaxDistance", 64, 1, 512);

        networkUpdateRate = builder
                .comment("How often, in ticks, should the network be updated?")
                .defineInRange("networkUpdateRate", 10, 1, 100);

        miningMultiplier = builder
                .comment("Multiplier for the speed of mining machines")
                .defineInRange("miningMultiplier", 1.0, 0.0, 100.0);

        miningMaxDepth = builder
                .comment("How deep can mining machines dig?")
                .defineInRange("miningMaxDepth", 512, 1, 2048);
    }

    public static void buildWorldgen(ModConfigSpec.Builder builder) {
        worldGen = builder
                .comment("Set true to allow world generation for BuildCraft")
                .define("worldGen", true);

        // worldGenWaterSpring removed — there are no water springs in 26.1.1.
        // Re-add when/if springs come back.
    }
}
