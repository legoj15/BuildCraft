package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.BCLibConfig.DecimalSeparator;
import buildcraft.lib.BCLibConfig.FlowDisplay;
import buildcraft.lib.BCLibConfig.ThousandsSeparator;

/** Exercises every supported (thousands × decimal) combination of {@link LocaleUtil}'s number
 *  formatters via the package-private overloads. The public no-arg overloads route through
 *  {@code BCLibConfig.thousandsSeparator.get()} which is unset under JUnit and falls back to the
 *  documented defaults — also covered here. */
public class LocaleUtilNumberFormatTester {

    @Test
    public void formatLong_eachThousandsSeparator() {
        Assertions.assertEquals("1,234,567", LocaleUtil.formatLong(1234567L, ThousandsSeparator.COMMA));
        Assertions.assertEquals("1.234.567", LocaleUtil.formatLong(1234567L, ThousandsSeparator.DOT));
        Assertions.assertEquals("1 234 567".replace(' ', ' '),
                LocaleUtil.formatLong(1234567L, ThousandsSeparator.SPACE));
        Assertions.assertEquals("1234567", LocaleUtil.formatLong(1234567L, ThousandsSeparator.NONE));
    }

    @Test
    public void formatLong_negativeAndSmall() {
        Assertions.assertEquals("0", LocaleUtil.formatLong(0L, ThousandsSeparator.COMMA));
        Assertions.assertEquals("999", LocaleUtil.formatLong(999L, ThousandsSeparator.COMMA));
        Assertions.assertEquals("-1,000", LocaleUtil.formatLong(-1000L, ThousandsSeparator.COMMA));
        Assertions.assertEquals("-1000", LocaleUtil.formatLong(-1000L, ThousandsSeparator.NONE));
    }

    @Test
    public void formatDouble_thousandsAndDecimalsCombine() {
        // US-style: 1,234.50
        Assertions.assertEquals("1,234.50",
                LocaleUtil.formatDouble(1234.5, 2, ThousandsSeparator.COMMA, DecimalSeparator.DOT));
        // European-style: 1.234,50
        Assertions.assertEquals("1.234,50",
                LocaleUtil.formatDouble(1234.5, 2, ThousandsSeparator.DOT, DecimalSeparator.COMMA));
        // SI-style: 1 234,50
        Assertions.assertEquals("1 234,50",
                LocaleUtil.formatDouble(1234.5, 2, ThousandsSeparator.SPACE, DecimalSeparator.COMMA));
        // Plain: 1234.50
        Assertions.assertEquals("1234.50",
                LocaleUtil.formatDouble(1234.5, 2, ThousandsSeparator.NONE, DecimalSeparator.DOT));
    }

    @Test
    public void formatDouble_decimalsCount() {
        Assertions.assertEquals("5", LocaleUtil.formatDouble(5.0, 0, ThousandsSeparator.COMMA, DecimalSeparator.DOT));
        Assertions.assertEquals("5.5", LocaleUtil.formatDouble(5.5, 1, ThousandsSeparator.COMMA, DecimalSeparator.DOT));
        Assertions.assertEquals("5.50", LocaleUtil.formatDouble(5.5, 2, ThousandsSeparator.COMMA, DecimalSeparator.DOT));
        Assertions.assertEquals("5.5000",
                LocaleUtil.formatDouble(5.5, 4, ThousandsSeparator.COMMA, DecimalSeparator.DOT));
    }

    @Test
    public void formatDouble_roundsHalfUpToConfiguredDigits() {
        // Confirms the formatter rounds at the boundary rather than truncating.
        Assertions.assertEquals("1.57",
                LocaleUtil.formatDouble(1.5678, 2, ThousandsSeparator.COMMA, DecimalSeparator.DOT));
        Assertions.assertEquals("0.0",
                LocaleUtil.formatDouble(0.04, 1, ThousandsSeparator.COMMA, DecimalSeparator.DOT));
        Assertions.assertEquals("0.1",
                LocaleUtil.formatDouble(0.05, 1, ThousandsSeparator.COMMA, DecimalSeparator.DOT));
    }

    @Test
    public void publicHelpers_useDocumentedDefaultsWhenConfigUnset() {
        // BCLibConfig.thousandsSeparator and decimalSeparator are null under JUnit (no NeoForge boot),
        // so the public helpers should fall back to COMMA / DOT and abbreviation OFF.
        Assertions.assertEquals("1,000", LocaleUtil.formatLong(1000L));
        Assertions.assertEquals("12,345.50", LocaleUtil.formatDouble(12345.5, 2));
    }

    @Test
    public void abbreviation_off_returnsRawValue() {
        // Below threshold: identical to abbreviation-on.
        Assertions.assertEquals("999",
                LocaleUtil.formatLong(999L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, false));
        // Above threshold but abbreviation off: full grouping applies.
        Assertions.assertEquals("1,500,000",
                LocaleUtil.formatLong(1_500_000L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, false));
    }

    @Test
    public void abbreviation_belowThreshold_returnsRaw() {
        // |value| < 1000 never abbreviates regardless of toggle.
        Assertions.assertEquals("0",
                LocaleUtil.formatLong(0L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
        Assertions.assertEquals("999",
                LocaleUtil.formatLong(999L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
        Assertions.assertEquals("-999",
                LocaleUtil.formatLong(-999L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
    }

    @Test
    public void abbreviation_eachTier() {
        Assertions.assertEquals("1.0k",
                LocaleUtil.formatLong(1000L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
        Assertions.assertEquals("1.2k",
                LocaleUtil.formatLong(1234L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
        Assertions.assertEquals("1.5M",
                LocaleUtil.formatLong(1_500_000L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
        Assertions.assertEquals("2.5G",
                LocaleUtil.formatLong(2_500_000_000L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
        Assertions.assertEquals("4.0T",
                LocaleUtil.formatLong(4_000_000_000_000L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
    }

    @Test
    public void abbreviation_negativeValues() {
        Assertions.assertEquals("-1.5k",
                LocaleUtil.formatLong(-1500L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
        Assertions.assertEquals("-2.5M",
                LocaleUtil.formatLong(-2_500_000L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
    }

    @Test
    public void abbreviation_tierCrossingEdge() {
        // 999,950 / 1000 = 999.95 which rounds to 1000.0 at one decimal — bump to the next tier
        // so we render "1.0M" rather than the awkward "1000.0k".
        Assertions.assertEquals("1.0M",
                LocaleUtil.formatLong(999_950L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
        // 999,949 still rounds to 999.9 at one decimal, so it stays at the k tier.
        Assertions.assertEquals("999.9k",
                LocaleUtil.formatLong(999_949L, ThousandsSeparator.COMMA, DecimalSeparator.DOT, true));
    }

    @Test
    public void abbreviation_honoursDecimalSeparator() {
        // Configured decimal separator applies to the abbreviated form's fractional digit.
        Assertions.assertEquals("1,5k",
                LocaleUtil.formatLong(1500L, ThousandsSeparator.DOT, DecimalSeparator.COMMA, true));
        Assertions.assertEquals("2,5M",
                LocaleUtil.formatLong(2_500_000L, ThousandsSeparator.SPACE, DecimalSeparator.COMMA, true));
    }

    @Test
    public void abbreviation_thousandsSeparatorIrrelevantBelow1000() {
        // Abbreviated values are always < 1000 in their integer portion, so the thousands separator
        // doesn't surface in the abbreviated output (no group ever forms past the suffix).
        Assertions.assertEquals("1.5k",
                LocaleUtil.formatLong(1500L, ThousandsSeparator.NONE, DecimalSeparator.DOT, true));
    }

    // -- Flow display modes ---------------------------------------------------

    @Test
    public void mjFlow_perSecond_multipliesByTwentyTicks() {
        // 5 mj/tick * 20 = 100 mj/sec
        Assertions.assertEquals("100.00 MJ/s",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.PER_SECOND, "MJ", false, false));
    }

    @Test
    public void mjFlow_perTick_passesThroughRaw() {
        Assertions.assertEquals("5.00 MJ/t",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.PER_TICK, "MJ", false, false));
    }

    @Test
    public void mjFlow_both_rendersBothInParens() {
        Assertions.assertEquals("100.00 MJ/s (5.00 MJ/t)",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.BOTH, "MJ", false, false));
    }

    @Test
    public void mjFlow_acceptsAnyUnitString() {
        // Long-form unit name plumbs through unchanged when fullSuffix is off — the suffix
        // remains "/s"/"/t" regardless of how verbose the unit string itself is.
        Assertions.assertEquals("100.00 Minecraft Joules/s (5.00 Minecraft Joules/t)",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.BOTH, "Minecraft Joules", false, false));
    }

    @Test
    public void mjFlow_fullSuffix_spellsOutPerSecondAndPerTick() {
        // When the "Spell Units In Full" config is on, "/s"/"/t" become " per second"/" per tick"
        // — matches 1.12.2's wording. Pairs with the long-form unit name in practice.
        Assertions.assertEquals("100.00 Minecraft Joules per second",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.PER_SECOND, "Minecraft Joules", true, false));
        Assertions.assertEquals("5.00 Minecraft Joules per tick",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.PER_TICK, "Minecraft Joules", true, false));
        Assertions.assertEquals("100.00 Minecraft Joules per second (5.00 Minecraft Joules per tick)",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.BOTH, "Minecraft Joules", true, false));
    }

    @Test
    public void mjFlow_abbreviate_collapsesAtThreeFigures() {
        // 500 mj/tick * 20 = 10,000 mj/sec → abbreviated to "10.0k MJ/s". Per-tick value (500) is
        // under the abbreviation threshold so it stays raw inside the BOTH-form parens.
        Assertions.assertEquals("10.0k MJ/s",
                LocaleUtil.formatMjFlow(500.0, FlowDisplay.PER_SECOND, "MJ", false, true));
        Assertions.assertEquals("500.00 MJ/t",
                LocaleUtil.formatMjFlow(500.0, FlowDisplay.PER_TICK, "MJ", false, true));
        Assertions.assertEquals("10.0k MJ/s (500.00 MJ/t)",
                LocaleUtil.formatMjFlow(500.0, FlowDisplay.BOTH, "MJ", false, true));
    }

    @Test
    public void mjAmount_abbreviate_collapsesScalar() {
        // Backs the assembly-table power ledger ("Required: 10,000 MJ" → "10.0k MJ").
        Assertions.assertEquals("10.0k", LocaleUtil.formatMjAmount(10_000.0, 2, true));
        Assertions.assertEquals("2.2k", LocaleUtil.formatMjAmount(2_158.09, 2, true));
        // Under threshold: raw form with the requested decimal precision.
        Assertions.assertEquals("500.00", LocaleUtil.formatMjAmount(500.0, 2, true));
        // Abbreviation off: raw form regardless of magnitude.
        Assertions.assertEquals("10,000.00", LocaleUtil.formatMjAmount(10_000.0, 2, false));
    }

    @Test
    public void rfFlow_perSecond_multipliesByTwentyTicks() {
        // 100 fe/tick * 20 = 2000 fe/sec, formatted with default COMMA grouping (config null).
        Assertions.assertEquals("2,000 FE/s",
                LocaleUtil.formatRfFlow(100, FlowDisplay.PER_SECOND, "FE", false));
    }

    @Test
    public void rfFlow_perTick_passesThroughRaw() {
        Assertions.assertEquals("100 FE/t",
                LocaleUtil.formatRfFlow(100, FlowDisplay.PER_TICK, "FE", false));
    }

    @Test
    public void rfFlow_both_rendersBothInParens() {
        Assertions.assertEquals("2,000 FE/s (100 FE/t)",
                LocaleUtil.formatRfFlow(100, FlowDisplay.BOTH, "FE", false));
    }

    @Test
    public void rfFlow_zeroValueRendersZeroOnBothSides() {
        Assertions.assertEquals("0 FE/s (0 FE/t)",
                LocaleUtil.formatRfFlow(0, FlowDisplay.BOTH, "FE", false));
    }

    @Test
    public void rfFlow_fullSuffix_spellsOutPerSecondAndPerTick() {
        // Long-form unit + spelled-out time suffix combination — the configuration the "Spell
        // Units In Full" toggle enables in practice.
        Assertions.assertEquals("2,000 Forge Energy per second",
                LocaleUtil.formatRfFlow(100, FlowDisplay.PER_SECOND, "Forge Energy", true));
        Assertions.assertEquals("100 Forge Energy per tick",
                LocaleUtil.formatRfFlow(100, FlowDisplay.PER_TICK, "Forge Energy", true));
        Assertions.assertEquals("2,000 Redstone Flux per second (100 Redstone Flux per tick)",
                LocaleUtil.formatRfFlow(100, FlowDisplay.BOTH, "Redstone Flux", true));
    }

    // -- Fluid flow ----------------------------------------------------------

    @Test
    public void fluidFlow_perTick_abbreviated_keepsMillibucketsPerTickCompact() {
        // PER_TICK + toggle off: matches the historical "40 mB/t" pipe-tooltip format.
        Assertions.assertEquals("40 mB/t", LocaleUtil.formatFluidFlow(40, FlowDisplay.PER_TICK, false, false));
        Assertions.assertEquals("0 mB/t", LocaleUtil.formatFluidFlow(0, FlowDisplay.PER_TICK, false, false));
    }

    @Test
    public void fluidFlow_perTick_fullSuffix_spellsOutMillibucketsPerTick() {
        // PER_TICK + toggle on (default): spells out the unit and the time suffix the same way
        // the MJ/RF flow readouts do.
        Assertions.assertEquals("40 millibuckets per tick",
                LocaleUtil.formatFluidFlow(40, FlowDisplay.PER_TICK, true, false));
        Assertions.assertEquals("1 millibuckets per tick",
                LocaleUtil.formatFluidFlow(1, FlowDisplay.PER_TICK, true, false));
    }

    @Test
    public void fluidFlow_perSecond_multipliesByTwentyTicks() {
        // 40 mB/tick * 20 = 800 mB/sec. Default COMMA grouping (config null under JUnit) applies
        // once the value crosses 1000.
        Assertions.assertEquals("800 mB/s",
                LocaleUtil.formatFluidFlow(40, FlowDisplay.PER_SECOND, false, false));
        Assertions.assertEquals("800 millibuckets per second",
                LocaleUtil.formatFluidFlow(40, FlowDisplay.PER_SECOND, true, false));
        Assertions.assertEquals("2,000 mB/s",
                LocaleUtil.formatFluidFlow(100, FlowDisplay.PER_SECOND, false, false));
    }

    @Test
    public void fluidFlow_both_rendersBothInParens() {
        // The BOTH mode is what the user reported as broken — only the per-tick figure was showing.
        Assertions.assertEquals("800 mB/s (40 mB/t)",
                LocaleUtil.formatFluidFlow(40, FlowDisplay.BOTH, false, false));
        Assertions.assertEquals("800 millibuckets per second (40 millibuckets per tick)",
                LocaleUtil.formatFluidFlow(40, FlowDisplay.BOTH, true, false));
    }

    @Test
    public void fluidFlow_zeroValueRendersZeroOnBothSides() {
        Assertions.assertEquals("0 mB/s (0 mB/t)",
                LocaleUtil.formatFluidFlow(0, FlowDisplay.BOTH, false, false));
    }

    @Test
    public void fluidFlow_abbreviate_shiftsToBucketsAtThousandMb() {
        // Once mb ≥ 1000 the unit shifts from millibuckets to buckets — the user's golden-pipe
        // example: 80 mB/tick * 20 = 1600 mB/s → "1.6 buckets per second" (or "1.6 B/s").
        Assertions.assertEquals("1.6 B/s",
                LocaleUtil.formatFluidFlow(80, FlowDisplay.PER_SECOND, false, true));
        Assertions.assertEquals("1.6 buckets per second",
                LocaleUtil.formatFluidFlow(80, FlowDisplay.PER_SECOND, true, true));
    }

    @Test
    public void fluidFlow_abbreviate_keepsMillibucketsBelowThreshold() {
        // Per-tick value of a golden-pipe-rate pipe (80 mB/tick) stays in mB — only above 1000 mB
        // does the unit shift. In BOTH mode the per-sec side abbreviates while per-tick doesn't.
        Assertions.assertEquals("80 mB/t",
                LocaleUtil.formatFluidFlow(80, FlowDisplay.PER_TICK, false, true));
        Assertions.assertEquals("1.6 B/s (80 mB/t)",
                LocaleUtil.formatFluidFlow(80, FlowDisplay.BOTH, false, true));
    }

    @Test
    public void fluidFlow_abbreviate_offFallsBackToRawMb() {
        // Abbreviation OFF: even at 1.6 buckets/sec, render the integer mB count with thousands sep.
        Assertions.assertEquals("1,600 mB/s",
                LocaleUtil.formatFluidFlow(80, FlowDisplay.PER_SECOND, false, false));
    }

    @Test
    public void fluidFlow_abbreviate_largeBucketRatesApplyKMG() {
        // 10,000 mB/tick * 20 = 200,000 mB/sec = 200 buckets/sec — should render as "200.0 B/s",
        // not as some hybrid "200.0k mB/s". k/M/G/T applies on top of buckets once the bucket
        // count itself crosses 1000.
        Assertions.assertEquals("200.0 B/s",
                LocaleUtil.formatFluidFlow(10_000, FlowDisplay.PER_SECOND, false, true));
        // 100,000 mB/tick * 20 = 2,000,000 mB/sec = 2000 buckets/sec → "2.0k B/s".
        Assertions.assertEquals("2.0k B/s",
                LocaleUtil.formatFluidFlow(100_000, FlowDisplay.PER_SECOND, false, true));
    }
}
