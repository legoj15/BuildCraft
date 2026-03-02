package buildcraft.core;

import java.io.File;

public class BCCoreConfig {
    public static File configFolder;

    public static boolean worldGen = true;
    public static boolean worldGenWaterSpring = true;
    public static boolean minePlayerProtected = false;
    public static boolean hidePower = false;
    public static boolean hideFluid = false;
    public static boolean pumpsConsumeWater = false;
    public static int markerMaxDistance = 64;
    public static int pumpMaxDistance = 64;
    public static int networkUpdateRate = 10;
    public static double miningMultiplier = 1;
    public static int miningMaxDepth = 512;

    public static void preInit(File cfgFolder) {
        configFolder = cfgFolder;
    }

    public static void postInit() {
    }

    public static void saveConfigs() {
    }
}
