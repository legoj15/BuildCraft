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

    /** Format MJ value for display (e.g. "5.00 Minecraft Joules", or "10.0k Minecraft Joules" when
     *  the abbreviation toggle is on and the value is ≥ 1000). Unit follows
     *  {@link BCEnergyConfig#useFullUnitNames}; thousands and decimal separators follow
     *  {@link BCLibConfig#thousandsSeparator} and {@link BCLibConfig#decimalSeparator};
     *  abbreviation follows {@link BCLibConfig#abbreviateLargeNumbers}. */
    public static String localizeMj(long microMj) {
        double mj = microMj / (double) MjAPI.MJ;
        return formatMjAmount(mj, 2, shouldAbbreviate()) + " " + mjUnit();
    }

    /** Format MJ flow for display, picking PER_SECOND ("100.00 MJ/s"), PER_TICK ("5.00 MJ/t"), or
     *  BOTH ("100.00 MJ/s (5.00 MJ/t)") per {@link BCLibConfig#flowDisplay}. Unit, time suffix, and
     *  separators follow {@link BCEnergyConfig#useFullUnitNames} (full names also spell out
     *  " per second" / " per tick" instead of "/s" / "/t"), {@link BCLibConfig#thousandsSeparator},
     *  and {@link BCLibConfig#decimalSeparator}; abbreviation follows
     *  {@link BCLibConfig#abbreviateLargeNumbers}. */
    public static String localizeMjFlow(long microMjPerTick) {
        double mjPerTick = microMjPerTick / (double) MjAPI.MJ;
        return formatMjFlow(mjPerTick, currentFlowDisplay(), mjUnit(), shouldUseFullNames(), shouldAbbreviate());
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
     *  follows {@link BCEnergyConfig#useFullUnitNames} (default on → "Forge Energy") and
     *  {@link BCEnergyConfig#useRfNaming} (off by default → FE branch; on → RF branch); the
     *  thousands separator follows {@link BCLibConfig#thousandsSeparator}. */
    public static String localizeRf(int current, int max) {
        return formatLong(current) + " / " + formatLong(max) + " " + energyUnit();
    }

    /** Format Forge Energy flow for display, picking PER_SECOND, PER_TICK, or BOTH per
     *  {@link BCLibConfig#flowDisplay}. Unit and time suffix follow
     *  {@link BCEnergyConfig#useFullUnitNames} (full names also spell out " per second" /
     *  " per tick" instead of "/s" / "/t") and {@link BCEnergyConfig#useRfNaming}; thousands
     *  separator follows {@link BCLibConfig#thousandsSeparator}. */
    public static String localizeRfFlow(int rfPerTick) {
        return formatRfFlow(rfPerTick, currentFlowDisplay(), energyUnit(), shouldUseFullNames());
    }

    /** Returns the FE-family unit label for the user's display preferences:
     *  full "Forge Energy"/"Redstone Flux" (default) or abbreviated "FE"/"RF"
     *  when {@link BCEnergyConfig#useFullUnitNames} is off. */
    public static String energyUnit() {
        boolean rf = BCEnergyConfig.useRfNaming != null && BCEnergyConfig.useRfNaming.get();
        if (shouldUseFullNames()) return rf ? "Redstone Flux" : "Forge Energy";
        return rf ? "RF" : "FE";
    }

    /** Returns "Minecraft Joules" (default) or "MJ" depending on
     *  {@link BCEnergyConfig#useFullUnitNames}. */
    public static String mjUnit() {
        return shouldUseFullNames() ? "Minecraft Joules" : "MJ";
    }

    /** Reads {@link BCEnergyConfig#useFullUnitNames}, defaulting to {@code true} when the config
     *  hasn't been loaded yet (e.g. unit tests). When on, unit names spell out as
     *  "Minecraft Joules"/"Forge Energy"/"Redstone Flux"/"millibuckets" AND time suffixes spell
     *  out as " per second"/" per tick" instead of "/s"/"/t". */
    private static boolean shouldUseFullNames() {
        return BCEnergyConfig.useFullUnitNames == null || BCEnergyConfig.useFullUnitNames.get();
    }

    /** Format fluid flow for display, picking PER_SECOND ("800 mB/s"), PER_TICK ("40 mB/t"), or
     *  BOTH ("800 mB/s (40 mB/t)") per {@link BCLibConfig#flowDisplay}. Unit and time suffix
     *  follow {@link BCEnergyConfig#useFullUnitNames} (full names also spell out " per second" /
     *  " per tick" instead of "/s" / "/t"); thousands separator follows
     *  {@link BCLibConfig#thousandsSeparator}. When {@link BCLibConfig#abbreviateLargeNumbers} is
     *  on AND the value ≥ 1000 mB, the unit shifts from millibuckets to buckets (e.g.
     *  "1.6 buckets per second" instead of "1.6k millibuckets per second") since whole-bucket
     *  rates are the more natural reading once values cross that threshold. The fluid pipe
     *  tooltip is the only consumer. */
    public static String localizeFluidFlow(int mbPerTick) {
        return formatFluidFlow(mbPerTick, currentFlowDisplay(), shouldUseFullNames(), shouldAbbreviate());
    }

    /** Package-visible — fixed-style variant for unit tests. The {@code mbPerTick * 20L} widening
     *  matches the RF formatter's overflow-safe widening (a pipe transferring near {@code
     *  Integer.MAX_VALUE/20} mB/tick would otherwise wrap to negative on the multiplication). */
    static String formatFluidFlow(int mbPerTick, BCLibConfig.FlowDisplay mode, boolean fullSuffix, boolean abbreviate) {
        String perSec = formatFluidAmount(mbPerTick * 20L, fullSuffix, abbreviate) + perSecondSuffix(fullSuffix);
        String perTick = formatFluidAmount(mbPerTick, fullSuffix, abbreviate) + perTickSuffix(fullSuffix);
        return switch (mode) {
            case PER_SECOND -> perSec;
            case PER_TICK -> perTick;
            case BOTH -> perSec + " (" + perTick + ")";
        };
    }

    /** {@code "<value> <unit>"} — under abbreviation switches the unit from mB to B (1000 mB = 1 B)
     *  and applies k/M/G/T on top of buckets only if it crosses the next tier (a 50,000 mB/s pipe
     *  would read as "50.0 buckets per second", not "50.0k mB" or "5.0E1 buckets"). The
     *  formatAbbreviated helper handles values < 1000 by returning the raw 1-decimal form. */
    private static String formatFluidAmount(long mb, boolean fullSuffix, boolean abbreviate) {
        if (abbreviate && Math.abs(mb) >= 1000L) {
            double buckets = mb / 1000.0;
            return formatAbbreviated(buckets, currentThousandsSep(), currentDecimalSep())
                    + " " + bucketsUnit(fullSuffix, false);
        }
        return formatLong(mb, currentThousandsSep(), currentDecimalSep(), false)
                + " " + fluidUnit(fullSuffix, false);
    }

    /** Buckets with an OPTIONAL single decimal — "1" for a whole bucket, "1.1" for a fraction, "16" for
     *  a full tank — matching the "1 bucket" / "1.1 B" form players expect. (The flow display keeps its
     *  own forced-one-decimal formatAbbreviated with k/M tiers; tank volumes don't need tiers.) */
    private static String formatBuckets(long mb, BCLibConfig.ThousandsSeparator t, BCLibConfig.DecimalSeparator d) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator(d.ch);
        if (t != BCLibConfig.ThousandsSeparator.NONE) {
            symbols.setGroupingSeparator(t.ch);
        }
        String pattern = (t == BCLibConfig.ThousandsSeparator.NONE ? "0" : "#,##0") + ".#";
        return new DecimalFormat(pattern, symbols).format(mb / 1000.0);
    }

    /** The numeric part only (no unit) of a fluid volume, in either mB or buckets. */
    private static String formatFluidValue(long mb, boolean buckets) {
        return buckets
                ? formatBuckets(mb, currentThousandsSep(), currentDecimalSep())
                : formatLong(mb, currentThousandsSep(), currentDecimalSep(), false);
    }

    /** {@code "<value> <unit>"} for a single standalone fluid volume — e.g. {@code "4000 mB"},
     *  {@code "4 buckets"}, {@code "1.1 B"}. Standalone position, so the unit is PLURAL. The unit follows
     *  {@link BCEnergyConfig#useFullUnitNames}; under {@link BCLibConfig#abbreviateLargeNumbers} a value
     *  ≥ 1000 mB is shown in buckets. */
    static String formatStaticFluid(long mb, boolean full, boolean abbreviate) {
        return formatStaticFluid(mb, full, abbreviate, false);
    }

    /** As {@link #formatStaticFluid(long, boolean, boolean)} but {@code singular} forces the SINGULAR
     *  unit name ("bucket"/"millibucket") for the compound-modifier position — the "Empty 4 bucket Tank"
     *  capacity readout, where a number+unit modifies the following noun and English keeps the unit
     *  singular regardless of count ("a 4-bucket tank", like "a 5-gallon drum"). The mB/B symbols don't
     *  inflect, so {@code singular} only affects the spelled-out full names. */
    static String formatStaticFluid(long mb, boolean full, boolean abbreviate, boolean singular) {
        boolean buckets = abbreviate && Math.abs(mb) >= 1000L;
        return formatFluidValue(mb, buckets) + " " + unitFor(buckets, full, singular);
    }

    /** A standalone single fluid volume with the PLURAL unit (e.g. "4 buckets"). */
    public static String localizeFluidStatic(long mb) {
        return formatStaticFluid(mb, shouldUseFullNames(), shouldAbbreviate(), false);
    }

    /** A tank CAPACITY for the "Empty &lt;capacity&gt; Tank" line — SINGULAR unit (compound modifier). */
    public static String localizeFluidCapacity(long mb) {
        return formatStaticFluid(mb, shouldUseFullNames(), shouldAbbreviate(), true);
    }

    /** {@code "<amount> / <capacity> <unit>"} for a tank readout — see {@link #formatFluidTank}. */
    public static String localizeFluidTank(long amount, long capacity) {
        return formatFluidTank(amount, capacity, shouldUseFullNames(), shouldAbbreviate());
    }

    /** Package-visible explicit-flag variant for unit tests (mirrors {@link #formatFluidFlow}). ONE shared
     *  unit, chosen by the CAPACITY's scale: when abbreviation is on and the capacity is ≥ 1000 mB the whole
     *  readout is in buckets — a near-empty bucket-scale tank reads {@code "0.2 / 4 buckets"}, never a mixed
     *  {@code "174 mB / 4 buckets"} — otherwise both sides stay in mB ({@code "174 / 500 mB"}). The unit is
     *  PLURAL (standalone "x / y unit" position, no trailing noun). */
    static String formatFluidTank(long amount, long capacity, boolean full, boolean abbreviate) {
        boolean buckets = abbreviate && Math.abs(capacity) >= 1000L;
        return formatFluidValue(amount, buckets) + " / " + formatFluidValue(capacity, buckets)
                + " " + unitFor(buckets, full, false);
    }

    /** Selects the fluid-volume unit label: buckets vs millibuckets, full name vs symbol, and (for the
     *  full names only) singular vs plural. The mB/B symbols never inflect. */
    private static String unitFor(boolean buckets, boolean full, boolean singular) {
        return buckets ? bucketsUnit(full, singular) : fluidUnit(full, singular);
    }

    private static String fluidUnit(boolean fullSuffix, boolean singular) {
        if (!fullSuffix) return "mB";
        return singular ? "millibucket" : "millibuckets";
    }

    /** The fluid-volume unit label (plural) for the user's display preferences: "millibuckets" when
     *  {@link BCEnergyConfig#useFullUnitNames} is on (default), else "mB". */
    public static String fluidUnit() {
        return fluidUnit(shouldUseFullNames(), false);
    }

    private static String bucketsUnit(boolean fullSuffix, boolean singular) {
        if (!fullSuffix) return "B";
        return singular ? "bucket" : "buckets";
    }

    /** Format a long with the configured thousands grouping separator. When
     *  {@link BCLibConfig#abbreviateLargeNumbers} is on AND |value| ≥ 1000, collapses to a compact
     *  suffixed form (k / M / G / T) at one decimal place using the configured decimal separator. */
    public static String formatLong(long value) {
        return formatLong(value, currentThousandsSep(), currentDecimalSep(), shouldAbbreviate());
    }

    /** Format a double with the configured thousands grouping and decimal separators,
     *  rounded to {@code decimals} fractional digits. Always returns the raw expanded form —
     *  the abbreviation toggle does not apply at this level, so JEI recipe-cost labels (the
     *  remaining direct caller) keep their full precision. MJ readouts now route through
     *  {@link #formatMjAmount(double, int, boolean)} which adds an abbreviation branch on top
     *  of this formatter. */
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

    /** Under JUnit (config null) this returns {@code false} so the structural formatter tests (flow
     *  modes, thousands grouping, RF readouts — paths that abbreviate via the global toggle with no
     *  explicit flag) see raw un-abbreviated output. In game the registered {@code abbreviateLargeNumbers}
     *  default ({@code true}) takes over once the config loads. The unset fallback is deliberately the
     *  safe raw value, NOT the configured default — unlike the label-choice toggles (full unit names,
     *  separators) whose fallback mirrors their default. */
    private static boolean shouldAbbreviate() {
        return BCLibConfig.abbreviateLargeNumbers != null && BCLibConfig.abbreviateLargeNumbers.get();
    }

    private static BCLibConfig.FlowDisplay currentFlowDisplay() {
        return BCLibConfig.flowDisplay != null ? BCLibConfig.flowDisplay.get() : BCLibConfig.FlowDisplay.PER_SECOND;
    }

    /** Package-visible — takes the unit string, suffix style, and abbreviation flag explicitly so
     *  unit tests can exercise every {@link BCLibConfig.FlowDisplay} × suffix × abbreviation combo
     *  without depending on the {@code mjUnit()} / {@code useFullUnitNames} /
     *  {@code abbreviateLargeNumbers} defaults. */
    static String formatMjFlow(double mjPerTick, BCLibConfig.FlowDisplay mode, String unit, boolean fullSuffix, boolean abbreviate) {
        String perSec = formatMjAmount(mjPerTick * 20.0, 2, abbreviate) + " " + unit + perSecondSuffix(fullSuffix);
        String perTick = formatMjAmount(mjPerTick, 2, abbreviate) + " " + unit + perTickSuffix(fullSuffix);
        return switch (mode) {
            case PER_SECOND -> perSec;
            case PER_TICK -> perTick;
            case BOTH -> perSec + " (" + perTick + ")";
        };
    }

    /** Format an MJ scalar: when abbreviation is on AND |value| ≥ 1000, collapses to the k/M/G/T
     *  one-decimal form ("10,000 → 10.0k"); otherwise returns the raw {@link #formatDouble(double,
     *  int)} output. Centralises the MJ-side abbreviation branching so {@link #localizeMj},
     *  {@link #localizeMjFlow}, and the package-visible {@link #formatMjFlow} agree. */
    static String formatMjAmount(double mj, int decimals, boolean abbreviate) {
        if (abbreviate && Math.abs(mj) >= 1000.0) {
            return formatAbbreviated(mj, currentThousandsSep(), currentDecimalSep());
        }
        return formatDouble(mj, decimals);
    }

    /** Package-visible test variant. The {@code rfPerTick * 20L} widening matches
     *  {@link #localizeRfFlow(int)}'s overflow-safe widening. */
    static String formatRfFlow(int rfPerTick, BCLibConfig.FlowDisplay mode, String unit, boolean fullSuffix) {
        String perSec = formatLong(rfPerTick * 20L) + " " + unit + perSecondSuffix(fullSuffix);
        String perTick = formatLong(rfPerTick) + " " + unit + perTickSuffix(fullSuffix);
        return switch (mode) {
            case PER_SECOND -> perSec;
            case PER_TICK -> perTick;
            case BOTH -> perSec + " (" + perTick + ")";
        };
    }

    private static String perSecondSuffix(boolean fullSuffix) {
        return fullSuffix ? " per second" : "/s";
    }

    private static String perTickSuffix(boolean fullSuffix) {
        return fullSuffix ? " per tick" : "/t";
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
