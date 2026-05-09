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
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.PER_SECOND, "MJ"));
    }

    @Test
    public void mjFlow_perTick_passesThroughRaw() {
        Assertions.assertEquals("5.00 MJ/t",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.PER_TICK, "MJ"));
    }

    @Test
    public void mjFlow_both_rendersBothInParens() {
        Assertions.assertEquals("100.00 MJ/s (5.00 MJ/t)",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.BOTH, "MJ"));
    }

    @Test
    public void mjFlow_acceptsAnyUnitString() {
        // Long-form unit name plumbs through unchanged.
        Assertions.assertEquals("100.00 Minecraft Joules/s (5.00 Minecraft Joules/t)",
                LocaleUtil.formatMjFlow(5.0, FlowDisplay.BOTH, "Minecraft Joules"));
    }

    @Test
    public void rfFlow_perSecond_multipliesByTwentyTicks() {
        // 100 fe/tick * 20 = 2000 fe/sec, formatted with default COMMA grouping (config null).
        Assertions.assertEquals("2,000 FE/s",
                LocaleUtil.formatRfFlow(100, FlowDisplay.PER_SECOND, "FE"));
    }

    @Test
    public void rfFlow_perTick_passesThroughRaw() {
        Assertions.assertEquals("100 FE/t",
                LocaleUtil.formatRfFlow(100, FlowDisplay.PER_TICK, "FE"));
    }

    @Test
    public void rfFlow_both_rendersBothInParens() {
        Assertions.assertEquals("2,000 FE/s (100 FE/t)",
                LocaleUtil.formatRfFlow(100, FlowDisplay.BOTH, "FE"));
    }

    @Test
    public void rfFlow_zeroValueRendersZeroOnBothSides() {
        Assertions.assertEquals("0 FE/s (0 FE/t)",
                LocaleUtil.formatRfFlow(0, FlowDisplay.BOTH, "FE"));
    }
}
