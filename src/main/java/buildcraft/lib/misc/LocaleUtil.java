package buildcraft.lib.misc;

import buildcraft.api.mj.MjAPI;
import buildcraft.energy.BCEnergyConfig;
import net.minecraft.locale.Language;

/** Localization utility. Uses Minecraft's Language registry for translation to avoid preemptive formatting errors. */
public class LocaleUtil {
    public static String localize(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    /** Translate a key and apply String.format with the given arguments.
     *  Matches 1.12.2's LocaleUtil.localize(String, Object...) for parameterized translations. */
    public static String localize(String key, Object... args) {
        String template = Language.getInstance().getOrDefault(key);
        return String.format(template, args);
    }

    /** Format MJ value for display (e.g. "5.00 Minecraft Joules"). Unit follows
     *  {@link BCEnergyConfig#useFullEnergyNames} — "Minecraft Joules" by default, "MJ" when off. */
    public static String localizeMj(long microMj) {
        double mj = microMj / (double) MjAPI.MJ;
        return String.format("%.2f %s", mj, mjUnit());
    }

    /** Format MJ/t flow for display as MJ/s (per second = ×20 ticks), matching 1.12 format.
     *  Unit follows {@link BCEnergyConfig#useFullEnergyNames}. */
    public static String localizeMjFlow(long microMjPerTick) {
        double mjPerSecond = (microMjPerTick / (double) MjAPI.MJ) * 20.0;
        return String.format("%.2f %s/s", mjPerSecond, mjUnit());
    }

    /** Format MJ/t flow for display (e.g. "5.00 MJ/t"). Unit follows
     *  {@link BCEnergyConfig#useFullEnergyNames}. */
    public static String localizeMjFlow(double mjPerTick) {
        return String.format("%.2f %s/t", mjPerTick, mjUnit());
    }

    /** Format heat level for display (e.g. "20.00 °C"), matching 1.12's format. */
    public static String localizeHeat(double heat) {
        return String.format("%.2f \u00B0C", heat);
    }

    /** Format heat level for display from a float value. */
    public static String localizeHeat(float heat) {
        return localizeHeat((double) heat);
    }

    /** Format stored Forge Energy for display (e.g. "5,000 / 10,000 Forge Energy"). The unit
     *  follows {@link BCEnergyConfig#useFullEnergyNames} (default on → "Forge Energy") and
     *  {@link BCEnergyConfig#useRfNaming} (off by default → FE branch; on → RF branch). */
    public static String localizeRf(int current, int max) {
        return String.format("%,d / %,d %s", current, max, energyUnit());
    }

    /** Format Forge Energy flow for display as per-second (×20 ticks), matching 1.12 format.
     *  Unit follows {@link BCEnergyConfig#useFullEnergyNames} and {@link BCEnergyConfig#useRfNaming};
     *  default reads as "Forge Energy/s". */
    public static String localizeRfFlow(int rfPerTick) {
        return String.format("%,d %s/s", rfPerTick * 20, energyUnit());
    }

    /** Returns the FE-family unit label for the user's display preferences:
     *  full "Forge Energy"/"Redstone Flux" (default) or abbreviated "FE"/"RF"
     *  when {@link BCEnergyConfig#useFullEnergyNames} is off. */
    public static String energyUnit() {
        boolean rf = BCEnergyConfig.useRfNaming != null && BCEnergyConfig.useRfNaming.get();
        boolean full = BCEnergyConfig.useFullEnergyNames == null
                || BCEnergyConfig.useFullEnergyNames.get();
        if (full) return rf ? "Redstone Flux" : "Forge Energy";
        return rf ? "RF" : "FE";
    }

    /** Returns "Minecraft Joules" (default) or "MJ" depending on
     *  {@link BCEnergyConfig#useFullEnergyNames}. */
    public static String mjUnit() {
        return BCEnergyConfig.useFullEnergyNames == null || BCEnergyConfig.useFullEnergyNames.get()
                ? "Minecraft Joules"
                : "MJ";
    }

    /** Format fluid flow for display (e.g. "40 mB/t"), matching 1.12.2's tooltip format. */
    public static String localizeFluidFlow(int mbPerTick) {
        return mbPerTick + " mB/t";
    }
}
