package buildcraft.core;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BCCoreConfig {

    // TODO: wire as global worldgen kill switch over BCCoreWorldGen / BCEnergyWorldGen
    public static ModConfigSpec.BooleanValue worldGen;
    // TODO: wire into TileQuarry / TileMiner break-block path; respects player-protection mods
    public static ModConfigSpec.BooleanValue minePlayerProtected;
    public static ModConfigSpec.BooleanValue pumpsConsumeWater;
    public static ModConfigSpec.IntValue markerMaxDistance;
    public static ModConfigSpec.IntValue pumpMaxDistance;
    public static ModConfigSpec.IntValue networkUpdateRate;
    // TODO: thread into mining-tile MJ-per-block math (TileQuarry, TileMiner, TileMiningWell)
    public static ModConfigSpec.DoubleValue miningMultiplier;
    public static ModConfigSpec.IntValue miningMaxDepth;

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        minePlayerProtected = builder
                .comment("Set true to prevent quarries from mining player-protected areas")
                .define("minePlayerProtected", false);

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
    }

    public static void buildWorldgen(ModConfigSpec.Builder builder) {
        worldGen = builder
                .comment("Set true to allow world generation for BuildCraft")
                .define("worldGen", true);

        // worldGenWaterSpring removed — there are no water springs in 26.1.1.
        // Re-add when/if springs come back.
    }
}
