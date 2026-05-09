package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.BCLibConfig.DecimalSeparator;
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
        // so the public helpers should fall back to COMMA / DOT.
        Assertions.assertEquals("1,000", LocaleUtil.formatLong(1000L));
        Assertions.assertEquals("12,345.50", LocaleUtil.formatDouble(12345.5, 2));
    }
}
