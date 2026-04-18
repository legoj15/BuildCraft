package buildcraft.lib.misc;

import buildcraft.api.mj.MjAPI;
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

    /** Format MJ value for display (e.g. "5.00 MJ") */
    public static String localizeMj(long microMj) {
        double mj = microMj / (double) MjAPI.MJ;
        return String.format("%.2f MJ", mj);
    }

    /** Format MJ/t flow for display as MJ/s (per second = ×20 ticks), matching 1.12 format */
    public static String localizeMjFlow(long microMjPerTick) {
        double mjPerSecond = (microMjPerTick / (double) MjAPI.MJ) * 20.0;
        return String.format("%.2f MJ/s", mjPerSecond);
    }

    /** Format MJ/t flow for display (e.g. "5.00 MJ/t") */
    public static String localizeMjFlow(double mjPerTick) {
        return String.format("%.2f MJ/t", mjPerTick);
    }

    /** Format heat level for display (e.g. "20.00 °C"), matching 1.12's format. */
    public static String localizeHeat(double heat) {
        return String.format("%.2f \u00B0C", heat);
    }

    /** Format heat level for display from a float value. */
    public static String localizeHeat(float heat) {
        return localizeHeat((double) heat);
    }

    /** Format RF stored for display (e.g. "5,000 / 10,000 RF"). */
    public static String localizeRf(int current, int max) {
        return String.format("%,d / %,d RF", current, max);
    }

    /** Format RF/t flow for display as RF/s (per second = ×20 ticks), matching 1.12 format. */
    public static String localizeRfFlow(int rfPerTick) {
        return String.format("%,d RF/s", rfPerTick * 20);
    }

    /** Format fluid flow for display (e.g. "40 mB/t"), matching 1.12.2's tooltip format. */
    public static String localizeFluidFlow(int mbPerTick) {
        return mbPerTick + " mB/t";
    }
}
