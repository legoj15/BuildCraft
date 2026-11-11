/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import buildcraft.lib.BCLibConfig.FlowDisplay;

/** The full fluid unit names (bucket / millibucket) agree with the number printed beside them, by a plural rule the
 * LANG FILE declares ({@code buildcraft.unit.plural_rule}). Russian needs three forms (1 ведро, 2-4 ведра, 5+ вёдер,
 * 21 ведро); a two-way singular/plural flag printed "4 вёдер" and "Пустой бак (16 ведро)".
 *
 * <p>Each test is a table of rows: the expected text, then the call that must produce it. A wrong row is collected
 * rather than thrown, so one run lists every disagreement at once. The language is a stub (see
 * {@link LocaleUtilUnitKeyTester} for why plain JUnit never reaches the lang branch on its own); the English words
 * are markers so a pass proves the lang file won, not the hard-coded fallback. */
public class LocaleUtilPluralUnitTester {

    private static Map<String, String> russian() {
        return Map.ofEntries(
                Map.entry("buildcraft.unit.plural_rule", "east_slavic"),
                Map.entry("buildcraft.unit.millibucket", "mB"),
                Map.entry("buildcraft.unit.millibucket.full.singular", "милливедро"),
                Map.entry("buildcraft.unit.millibucket.full.plural", "милливёдер"),
                Map.entry("buildcraft.unit.millibucket.full.one", "милливедро"),
                Map.entry("buildcraft.unit.millibucket.full.few", "милливедра"),
                Map.entry("buildcraft.unit.millibucket.full.many", "милливёдер"),
                Map.entry("buildcraft.unit.bucket", "B"),
                Map.entry("buildcraft.unit.bucket.full.singular", "ведро"),
                Map.entry("buildcraft.unit.bucket.full.plural", "вёдер"),
                Map.entry("buildcraft.unit.bucket.full.one", "ведро"),
                Map.entry("buildcraft.unit.bucket.full.few", "ведра"),
                Map.entry("buildcraft.unit.bucket.full.many", "вёдер"),
                Map.entry("buildcraft.unit.per_second.full", "%s в секунду"),
                Map.entry("buildcraft.unit.per_tick.full", "%s за тик"));
    }

    /** Only the two keys English-like languages carry (singular + plural), with markers. */
    private static Map<String, String> singularPluralOnly(String rule) {
        Map<String, String> m = new HashMap<>(Map.of(
                "buildcraft.unit.millibucket", "mB",
                "buildcraft.unit.millibucket.full.singular", "MILLI_SING",
                "buildcraft.unit.millibucket.full.plural", "MILLI_PL",
                "buildcraft.unit.bucket", "B",
                "buildcraft.unit.bucket.full.singular", "BUCKET_SING",
                "buildcraft.unit.bucket.full.plural", "BUCKET_PL"));
        if (rule != null) {
            m.put("buildcraft.unit.plural_rule", rule);
        }
        return m;
    }

    private Language original;
    private final List<String> wrong = new ArrayList<>();

    @BeforeEach
    public void remember() {
        original = Language.getInstance();
        wrong.clear();
    }

    @AfterEach
    public void restoreLanguage() {
        // Language.instance is static; leaking the stub would corrupt every later test in the JVM.
        Language.inject(original);
    }

    private static void inject(Map<String, String> entries) {
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String elementId, String defaultValue) {
                return entries.getOrDefault(elementId, defaultValue);
            }

            @Override
            public boolean has(String elementId) {
                return entries.containsKey(elementId);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText logicalOrderText) {
                return FormattedCharSequence.EMPTY;
            }
        });
    }

    private void row(String what, String expected, String actual) {
        if (!expected.equals(actual)) {
            wrong.add(what + ": expected \"" + expected + "\" but got \"" + actual + "\"");
        }
    }

    private void assertNoWrongRows() {
        Assertions.assertTrue(wrong.isEmpty(), wrong.size() + " row(s) disagree:\n" + String.join("\n", wrong));
    }

    /** The capacity ("Empty 4 bucket Tank") position of a single volume, full names, as the public
     *  {@code localizeFluidCapacity} calls it. */
    private static String capacity(long mb, boolean abbreviate) {
        return LocaleUtil.formatStaticFluid(mb, true, abbreviate, LocaleUtil.UnitPosition.COMPOUND_MODIFIER);
    }

    private static String bucketsStatic(long mb) {
        return LocaleUtil.formatStaticFluid(mb, true, true);
    }

    // -- East Slavic ----------------------------------------------------------

    /** The number -> form table: 0 = one, 1 = few, 2 = many. 11-14 are many (not few), 21/22/25 recur, 111/112 keep
     *  the teens exception on the last two digits. */
    private static final int[][] EAST_SLAVIC_FORMS = {
            { 0, 2 }, { 1, 0 }, { 2, 1 }, { 3, 1 }, { 4, 1 }, { 5, 2 }, { 9, 2 }, { 10, 2 }, { 11, 2 }, { 12, 2 },
            { 13, 2 }, { 14, 2 }, { 15, 2 }, { 20, 2 }, { 21, 0 }, { 22, 1 }, { 24, 1 }, { 25, 2 }, { 100, 2 },
            { 101, 0 }, { 102, 1 }, { 111, 2 }, { 112, 2 }, { 121, 0 }, { 122, 1 },
    };
    private static final String[] RU_BUCKET = { "ведро", "ведра", "вёдер" };
    private static final String[] RU_MILLI = { "милливедро", "милливедра", "милливёдер" };

    @Test
    public void eastSlavic_wholeCountsPickOneFewMany() {
        inject(russian());
        for (int[] r : EAST_SLAVIC_FORMS) {
            int n = r[0];
            if (n > 0) { // 0 mB stays in millibuckets under abbreviation, so "0 buckets" is not reachable here
                row(n + " buckets (static)", n + " " + RU_BUCKET[r[1]], bucketsStatic(n * 1000L));
            }
            row(n + " millibuckets (static)", n + " " + RU_MILLI[r[1]], LocaleUtil.formatStaticFluid(n, true, false));
        }
        assertNoWrongRows();
    }

    @Test
    public void eastSlavic_fractionsRoundingAndTiersFollowTheDisplayedNumber() {
        inject(russian());
        // A fractional bucket count is the genitive singular ("1,5 ведра"), whatever its integer part.
        row("1.5", "1.5 ведра", bucketsStatic(1500));
        row("5.5", "5.5 ведра", bucketsStatic(5500));
        row("11.5", "11.5 ведра", bucketsStatic(11_500));
        row("1.1", "1.1 ведра", bucketsStatic(1100));
        // The unit follows the number as DISPLAYED, after one-decimal rounding.
        row("1040 mB shows 1", "1 ведро", bucketsStatic(1040));
        row("2040 mB shows 2", "2 ведра", bucketsStatic(2040));
        row("4960 mB shows 5", "5 вёдер", bucketsStatic(4960));
        row("20960 mB shows 21", "21 ведро", bucketsStatic(20_960));
        // A k-abbreviated value is always the genitive plural, even 1k and 21k.
        row("1k", "1k вёдер в секунду", LocaleUtil.formatFluidFlow(50_000, FlowDisplay.PER_SECOND, true, true));
        row("2k", "2k вёдер в секунду", LocaleUtil.formatFluidFlow(100_000, FlowDisplay.PER_SECOND, true, true));
        row("2.5k", "2.5k вёдер в секунду", LocaleUtil.formatFluidFlow(125_000, FlowDisplay.PER_SECOND, true, true));
        row("21k", "21k вёдер в секунду", LocaleUtil.formatFluidFlow(1_050_000, FlowDisplay.PER_SECOND, true, true));
        assertNoWrongRows();
    }

    @Test
    public void eastSlavic_flowReadoutsAgreeWithTheirOwnNumber() {
        inject(russian());
        int[][] milliPerTick = { { 1, 0 }, { 2, 1 }, { 4, 1 }, { 5, 2 }, { 11, 2 }, { 21, 0 }, { 22, 1 } };
        for (int[] r : milliPerTick) {
            row(r[0] + " mB/t", r[0] + " " + RU_MILLI[r[1]] + " за тик",
                    LocaleUtil.formatFluidFlow(r[0], FlowDisplay.PER_TICK, true, false));
        }
        // 50/100/250/80 mB/t * 20 = 1000/2000/5000/1600 mB/s, which abbreviation turns into 1/2/5/1.6 buckets.
        row("1 B/s", "1 ведро в секунду", LocaleUtil.formatFluidFlow(50, FlowDisplay.PER_SECOND, true, true));
        row("2 B/s", "2 ведра в секунду", LocaleUtil.formatFluidFlow(100, FlowDisplay.PER_SECOND, true, true));
        row("5 B/s", "5 вёдер в секунду", LocaleUtil.formatFluidFlow(250, FlowDisplay.PER_SECOND, true, true));
        row("1.6 B/s", "1.6 ведра в секунду", LocaleUtil.formatFluidFlow(80, FlowDisplay.PER_SECOND, true, true));
        // BOTH: each side agrees with its own number (20 mB/s but 1 mB/t).
        row("BOTH", "20 милливёдер в секунду (1 милливедро за тик)",
                LocaleUtil.formatFluidFlow(1, FlowDisplay.BOTH, true, false));
        assertNoWrongRows();
    }

    @Test
    public void eastSlavic_tankReadoutAgreesWithTheCapacity() {
        inject(russian());
        row("0.5 / 4", "0.5 / 4 ведра", LocaleUtil.formatFluidTank(500, 4000, true, true));
        // The amount alone (3) would take "ведра"; the unit follows the capacity (5).
        row("3 / 5", "3 / 5 вёдер", LocaleUtil.formatFluidTank(3000, 5000, true, true));
        row("0 / 21", "0 / 21 ведро", LocaleUtil.formatFluidTank(0, 21_000, true, true));
        row("1 / 1", "1 / 1 ведро", LocaleUtil.formatFluidTank(1000, 1000, true, true));
        row("0.5 / 1.5", "0.5 / 1.5 ведра", LocaleUtil.formatFluidTank(500, 1500, true, true));
        row("mB 0 / 1", "0 / 1 милливедро", LocaleUtil.formatFluidTank(0, 1, true, false));
        row("mB 3 / 2", "3 / 2 милливедра", LocaleUtil.formatFluidTank(3, 2, true, false));
        row("mB 174 / 500", "174 / 500 милливёдер", LocaleUtil.formatFluidTank(174, 500, true, true));
        assertNoWrongRows();
    }

    @Test
    public void eastSlavic_capacityPositionAgreesWithCount() {
        inject(russian());
        row("capacity 16", "16 вёдер", capacity(16_000, true));
        row("capacity 4", "4 ведра", capacity(4000, true));
        row("capacity 1", "1 ведро", capacity(1000, true));
        row("capacity 21", "21 ведро", capacity(21_000, true));
        row("capacity 1 mB", "1 милливедро", capacity(1, false));
        row("capacity 500 mB", "500 милливёдер", capacity(500, false));
        // Through the public entry points (config unset: full names on, abbreviation off, so millibuckets).
        row("localizeFluidCapacity(2000)", "2,000 милливёдер", LocaleUtil.localizeFluidCapacity(2000));
        row("localizeFluidCapacity(4)", "4 милливедра", LocaleUtil.localizeFluidCapacity(4));
        row("localizeFluidStatic(21)", "21 милливедро", LocaleUtil.localizeFluidStatic(21));
        row("localizeFluidTank(1, 2)", "1 / 2 милливедра", LocaleUtil.localizeFluidTank(1, 2));
        assertNoWrongRows();
    }

    @Test
    public void symbolsNeverInflect() {
        inject(russian());
        for (int n : new int[] { 1, 2, 5, 21 }) {
            row(n + " B", n + " B", LocaleUtil.formatStaticFluid(n * 1000L, false, true));
            row(n + " mB", n + " mB", LocaleUtil.formatStaticFluid(n, false, false));
        }
        row("B/s", "2 B/s", LocaleUtil.formatFluidFlow(100, FlowDisplay.PER_SECOND, false, true));
        row("tank", "3 / 5 B", LocaleUtil.formatFluidTank(3000, 5000, false, true));
        row("capacity symbol", "16 B",
                LocaleUtil.formatStaticFluid(16_000, false, true, LocaleUtil.UnitPosition.COMPOUND_MODIFIER));
        assertNoWrongRows();
    }

    // -- English / undeclared -------------------------------------------------

    @Test
    public void english_onlyExactlyOneIsSingular_capacityStaysSingular() {
        for (String rule : new String[] { null, "english", "klingon" }) {
            Language.inject(original);
            wrong.clear();
            inject(singularPluralOnly(rule));
            String r = "[rule=" + rule + "] ";
            row(r + "1 bucket", "1 BUCKET_SING", bucketsStatic(1000));
            row(r + "2 buckets", "2 BUCKET_PL", bucketsStatic(2000));
            row(r + "1.5 buckets", "1.5 BUCKET_PL", bucketsStatic(1500));
            row(r + "1040 mB shows 1", "1 BUCKET_SING", bucketsStatic(1040));
            row(r + "21 buckets", "21 BUCKET_PL", bucketsStatic(21_000));
            row(r + "0 mB", "0 MILLI_PL", LocaleUtil.formatStaticFluid(0, true, true));
            row(r + "1 mB", "1 MILLI_SING", LocaleUtil.formatStaticFluid(1, true, false));
            row(r + "5 mB", "5 MILLI_PL", LocaleUtil.formatStaticFluid(5, true, false));
            // 1k is an abbreviated value, not the number one.
            row(r + "1k", "1k BUCKET_PL per second",
                    LocaleUtil.formatFluidFlow(50_000, FlowDisplay.PER_SECOND, true, true));
            row(r + "1 mB/t", "1 MILLI_SING per tick",
                    LocaleUtil.formatFluidFlow(1, FlowDisplay.PER_TICK, true, false));
            row(r + "2 mB/t", "2 MILLI_PL per tick",
                    LocaleUtil.formatFluidFlow(2, FlowDisplay.PER_TICK, true, false));
            row(r + "tank 0 / 1", "0 / 1 BUCKET_SING", LocaleUtil.formatFluidTank(0, 1000, true, true));
            row(r + "tank 0 / 2", "0 / 2 BUCKET_PL", LocaleUtil.formatFluidTank(0, 2000, true, true));
            // The capacity modifier ("a 16-bucket tank") is singular whatever the count.
            row(r + "capacity 16", "16 BUCKET_SING", capacity(16_000, true));
            row(r + "capacity 2", "2 BUCKET_SING", capacity(2000, true));
            row(r + "capacity 500 mB", "500 MILLI_SING", capacity(500, false));
            row(r + "fluidUnit()", "MILLI_PL", LocaleUtil.fluidUnit());
            assertNoWrongRows();
        }
    }

    // -- Fallbacks between the form keys and the singular/plural pair ---------

    @Test
    public void fewAndManyFallBackToPlural_oneFallsBackToSingular() {
        inject(singularPluralOnly("east_slavic"));
        row("1", "1 BUCKET_SING", bucketsStatic(1000));
        row("2 (few)", "2 BUCKET_PL", bucketsStatic(2000));
        row("5 (many)", "5 BUCKET_PL", bucketsStatic(5000));
        row("21", "21 BUCKET_SING", bucketsStatic(21_000));
        row("1.5", "1.5 BUCKET_PL", bucketsStatic(1500));
        row("capacity 16", "16 BUCKET_PL", capacity(16_000, true));
        row("capacity 1", "1 BUCKET_SING", capacity(1000, true));
        assertNoWrongRows();
    }

    @Test
    public void aFormKeyWinsOverTheSingularPluralPair_andEachFormFallsBackAlone() {
        Map<String, String> m = singularPluralOnly("east_slavic");
        m.put("buildcraft.unit.bucket.full.one", "BUCKET_ONE");
        m.put("buildcraft.unit.bucket.full.few", "BUCKET_FEW");
        // No .many: 5 must still reach .plural, not break on the absent key.
        inject(m);
        row("1 -> .one", "1 BUCKET_ONE", bucketsStatic(1000));
        row("2 -> .few", "2 BUCKET_FEW", bucketsStatic(2000));
        row("5 -> .plural", "5 BUCKET_PL", bucketsStatic(5000));

        m.put("buildcraft.unit.bucket.full.many", "BUCKET_MANY");
        inject(m);
        row("5 -> .many", "5 BUCKET_MANY", bucketsStatic(5000));
        row("fluidUnit() takes the many/plural form", "MILLI_PL", LocaleUtil.fluidUnit());
        assertNoWrongRows();
    }
}
