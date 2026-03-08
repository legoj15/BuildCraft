package buildcraft.lib.misc;

import buildcraft.api.mj.MjAPI;
import net.minecraft.client.resources.language.I18n;

/** Localization utility. Uses Minecraft's I18n for translation. */
public class LocaleUtil {
    public static String localize(String key) {
        return I18n.get(key);
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
}
