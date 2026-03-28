package buildcraft.core;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BCCoreConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue worldGen;
    public static final ModConfigSpec.BooleanValue worldGenWaterSpring;
    public static final ModConfigSpec.BooleanValue minePlayerProtected;
    public static final ModConfigSpec.BooleanValue hidePower;
    public static final ModConfigSpec.BooleanValue hideFluid;
    public static final ModConfigSpec.BooleanValue pumpsConsumeWater;
    public static final ModConfigSpec.IntValue markerMaxDistance;
    public static final ModConfigSpec.IntValue pumpMaxDistance;
    public static final ModConfigSpec.IntValue networkUpdateRate;
    public static final ModConfigSpec.DoubleValue miningMultiplier;
    public static final ModConfigSpec.IntValue miningMaxDepth;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");

        worldGen = builder
                .comment("Set true to allow world generation for BuildCraft")
                .define("worldGen", true);

        worldGenWaterSpring = builder
                .comment("Set true to allow water springs to generate")
                .define("worldGenWaterSpring", true);

        minePlayerProtected = builder
                .comment("Set true to prevent quarries from mining player-protected areas")
                .define("minePlayerProtected", false);

        hidePower = builder
                .comment("Should power indicators be hidden?")
                .define("hidePower", false);

        hideFluid = builder
                .comment("Should fluid indicators be hidden?")
                .define("hideFluid", false);

        pumpsConsumeWater = builder
                .comment("Should pumps slowly drain infinite water sources?")
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

        builder.pop();
        SPEC = builder.build();
    }
}
