package buildcraft.lib.misc;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import buildcraft.api.mj.MjAPI;
import buildcraft.energy.BCEnergyConfig;
import buildcraft.lib.BCLibConfig;
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
     *  {@link BCEnergyConfig#useFullEnergyNames}; thousands and decimal separators follow
     *  {@link BCLibConfig#thousandsSeparator} and {@link BCLibConfig#decimalSeparator}. */
    public static String localizeMj(long microMj) {
        double mj = microMj / (double) MjAPI.MJ;
        return formatDouble(mj, 2) + " " + mjUnit();
    }

    /** Format MJ/t flow for display as MJ/s (per second = ×20 ticks), matching 1.12 format.
     *  Unit and separators follow {@link BCEnergyConfig#useFullEnergyNames},
     *  {@link BCLibConfig#thousandsSeparator}, and {@link BCLibConfig#decimalSeparator}. */
    public static String localizeMjFlow(long microMjPerTick) {
        double mjPerSecond = (microMjPerTick / (double) MjAPI.MJ) * 20.0;
        return formatDouble(mjPerSecond, 2) + " " + mjUnit() + "/s";
    }

    /** Format MJ/t flow for display (e.g. "5.00 MJ/t"). Unit and separators follow the
     *  matching {@link BCEnergyConfig} / {@link BCLibConfig} display flags. */
    public static String localizeMjFlow(double mjPerTick) {
        return formatDouble(mjPerTick, 2) + " " + mjUnit() + "/t";
    }

    /** Format heat level for display (e.g. "20.00 °C"), matching 1.12's format. The decimal
     *  separator follows {@link BCLibConfig#decimalSeparator}. */
    public static String localizeHeat(double heat) {
        return formatDouble(heat, 2) + " °C";
    }

    /** Format heat level for display from a float value. */
    public static String localizeHeat(float heat) {
        return localizeHeat((double) heat);
    }

    /** Format stored Forge Energy for display (e.g. "5,000 / 10,000 Forge Energy"). The unit
     *  follows {@link BCEnergyConfig#useFullEnergyNames} (default on → "Forge Energy") and
     *  {@link BCEnergyConfig#useRfNaming} (off by default → FE branch; on → RF branch); the
     *  thousands separator follows {@link BCLibConfig#thousandsSeparator}. */
    public static String localizeRf(int current, int max) {
        return formatLong(current) + " / " + formatLong(max) + " " + energyUnit();
    }

    /** Format Forge Energy flow for display as per-second (×20 ticks), matching 1.12 format.
     *  Unit follows {@link BCEnergyConfig#useFullEnergyNames} and {@link BCEnergyConfig#useRfNaming};
     *  thousands separator follows {@link BCLibConfig#thousandsSeparator}. */
    public static String localizeRfFlow(int rfPerTick) {
        return formatLong(rfPerTick * 20L) + " " + energyUnit() + "/s";
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

    /** Format a long with the configured thousands grouping separator. When
     *  {@link BCLibConfig#abbreviateLargeNumbers} is on AND |value| ≥ 1000, collapses to a compact
     *  suffixed form (k / M / G / T) at one decimal place using the configured decimal separator. */
    public static String formatLong(long value) {
        return formatLong(value, currentThousandsSep(), currentDecimalSep(), shouldAbbreviate());
    }

    /** Format a double with the configured thousands grouping and decimal separators,
     *  rounded to {@code decimals} fractional digits. Always returns the raw expanded form —
     *  the abbreviation toggle does not apply, so MJ/heat readouts and JEI recipe-cost labels
     *  keep their full precision. */
    public static String formatDouble(double value, int decimals) {
        return formatDouble(value, decimals, currentThousandsSep(), currentDecimalSep());
    }

    /** Two-arg test overload — preserves the original test API without abbreviation. */
    static String formatLong(long value, BCLibConfig.ThousandsSeparator t) {
        return formatLong(value, t, BCLibConfig.DecimalSeparator.DOT, false);
    }

    /** Package-visible variant taking explicit separators and abbreviation flag — used by unit
     *  tests to exercise every combination without booting NeoForge's config system. */
    static String formatLong(long value, BCLibConfig.ThousandsSeparator t,
                             BCLibConfig.DecimalSeparator d, boolean abbreviate) {
        if (abbreviate && Math.abs(value) >= 1000L) {
            return formatAbbreviated((double) value, t, d);
        }
        return numberFormat(t, d, 0).format(value);
    }

    /** Package-visible variant taking explicit separators — used by unit tests. */
    static String formatDouble(double value, int decimals,
                               BCLibConfig.ThousandsSeparator t, BCLibConfig.DecimalSeparator d) {
        return numberFormat(t, d, decimals).format(value);
    }

    private static boolean shouldAbbreviate() {
        return BCLibConfig.abbreviateLargeNumbers != null && BCLibConfig.abbreviateLargeNumbers.get();
    }

    /** Walks {@code value} down through powers of 1000 and renders the result with one decimal
     *  followed by the matching k/M/G/T suffix. Handles the tier-crossing rounding edge — a value
     *  like 999,950 divides to 999.95, which would format as "1000.0k" at one-decimal precision;
     *  the second loop iteration bumps it to the next tier so the output reads "1.0M" instead. */
    private static String formatAbbreviated(double value, BCLibConfig.ThousandsSeparator t,
                                            BCLibConfig.DecimalSeparator d) {
        String[] suffixes = { "", "k", "M", "G", "T" };
        double v = value;
        int tier = 0;
        while (Math.abs(v) >= 1000.0 && tier < suffixes.length - 1) {
            v /= 1000.0;
            tier++;
        }
        if (Math.abs(v) >= 999.95 && tier < suffixes.length - 1) {
            v /= 1000.0;
            tier++;
        }
        return numberFormat(t, d, 1).format(v) + suffixes[tier];
    }

    private static BCLibConfig.ThousandsSeparator currentThousandsSep() {
        return BCLibConfig.thousandsSeparator != null
                ? BCLibConfig.thousandsSeparator.get()
                : BCLibConfig.ThousandsSeparator.COMMA;
    }

    private static BCLibConfig.DecimalSeparator currentDecimalSep() {
        return BCLibConfig.decimalSeparator != null
                ? BCLibConfig.decimalSeparator.get()
                : BCLibConfig.DecimalSeparator.DOT;
    }

    /** Builds a {@link DecimalFormat} on the fly. Re-built per call because {@link DecimalFormat}
     *  is not thread-safe and the call rate is bounded by the UI refresh — the construction cost
     *  is dominated by the surrounding render path. */
    private static DecimalFormat numberFormat(BCLibConfig.ThousandsSeparator t,
                                              BCLibConfig.DecimalSeparator d, int decimals) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator(d.ch);
        if (t != BCLibConfig.ThousandsSeparator.NONE) {
            symbols.setGroupingSeparator(t.ch);
        }

        StringBuilder pattern = new StringBuilder(t == BCLibConfig.ThousandsSeparator.NONE ? "0" : "#,##0");
        if (decimals > 0) {
            pattern.append('.');
            for (int i = 0; i < decimals; i++) pattern.append('0');
        }
        return new DecimalFormat(pattern.toString(), symbols);
    }
}
