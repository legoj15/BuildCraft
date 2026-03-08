package buildcraft.lib.misc;

import buildcraft.api.mj.MjAPI;

/** Localization utility. Currently a passthrough stub — returns the key as-is.
 *  Will be wired to Minecraft's I18n when the client layer is ported. */
public class LocaleUtil {
    public static String localize(String key) {
        return key;
    }

    /** Format MJ value for display (e.g. "5.00 MJ") */
    public static String localizeMj(long microMj) {
        double mj = microMj / (double) MjAPI.MJ;
        return String.format("%.2f MJ", mj);
    }

    /** Format MJ/t flow for display (e.g. "5.00 MJ/t") */
    public static String localizeMjFlow(long microMjPerTick) {
        double mj = microMjPerTick / (double) MjAPI.MJ;
        return String.format("%.2f MJ/t", mj);
    }

    /** Format MJ/t flow for display (e.g. "5.00 MJ/t") */
    public static String localizeMjFlow(double mjPerTick) {
        return String.format("%.2f MJ/t", mjPerTick);
    }
}
