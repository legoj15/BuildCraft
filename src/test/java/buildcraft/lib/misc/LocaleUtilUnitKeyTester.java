/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.Map;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import buildcraft.lib.BCLibConfig.FlowDisplay;

/** Covers the LANG-DRIVEN branch of {@link LocaleUtil}'s unit formatting.
 *
 * <p>This exists because {@link LocaleUtilNumberFormatTester} cannot reach it. Under plain JUnit,
 * {@code Language.loadDefault()} parses only vanilla's own {@code /assets/minecraft/lang/en_us.json} and
 * NeoForge's {@code LanguageHook} never runs, so no {@code buildcraft.unit.*} key is present and every
 * lookup takes the hardcoded-English fallback. That makes the sibling tester's assertions pass for the
 * wrong reason: it pins the fallback, not the string a player actually sees.
 *
 * <p>Injecting a {@link Language} stub is the only way to exercise the real path. The stub deliberately
 * returns values that are NOT the English fallbacks, so an assertion can only pass if the lang lookup
 * genuinely won — if the fallback fired instead, the expected and actual strings differ visibly.
 *
 * <p>{@code Language} declares the same four abstract methods on both sides of the 1.21.10 cliff
 * (verified against sources-21.1.238 and sources-26.1.2.81), so this stub needs no Stonecutter
 * directives. */
public class LocaleUtilUnitKeyTester {

    /** Marker values chosen to be impossible to confuse with the English fallbacks. */
    private static final Map<String, String> STUB_ENTRIES = Map.ofEntries(
            Map.entry("buildcraft.unit.mj", "MJ_X"),
            Map.entry("buildcraft.unit.mj.full", "MJ_FULL_X"),
            Map.entry("buildcraft.unit.millibucket", "MB_X"),
            Map.entry("buildcraft.unit.millibucket.full.plural", "MB_PLURAL_X"),
            Map.entry("buildcraft.unit.bucket", "B_X"),
            Map.entry("buildcraft.unit.bucket.full.plural", "B_PLURAL_X"),
            Map.entry("buildcraft.unit.per_tick", "%s@TICK"),
            Map.entry("buildcraft.unit.per_tick.full", "%s FOR EACH TICK"),
            Map.entry("buildcraft.unit.per_second", "%s@SEC"),
            Map.entry("buildcraft.unit.per_second.full", "%s FOR EACH SECOND"),
            Map.entry("buildcraft.unit.temperature", "%s DEG"));

    private Language original;

    @BeforeEach
    public void injectStub() {
        original = Language.getInstance();
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String elementId, String defaultValue) {
                return STUB_ENTRIES.getOrDefault(elementId, defaultValue);
            }

            @Override
            public boolean has(String elementId) {
                return STUB_ENTRIES.containsKey(elementId);
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

    @AfterEach
    public void restoreLanguage() {
        // Language.instance is static; leaking the stub would corrupt every later test in the JVM.
        Language.inject(original);
    }

    @Test
    public void stubActuallyWins_soTheseAssertionsMeanSomething() {
        // Sanity gate. If this fails, the lang branch is unreachable and every other assertion in this
        // class would be silently pinning the fallback instead.
        Assertions.assertEquals("MJ_X", LocaleUtil.unitText("buildcraft.unit.mj", "MJ"));
        Assertions.assertEquals("UNTOUCHED",
                LocaleUtil.unitText("buildcraft.unit.does_not_exist", "UNTOUCHED"),
                "A key absent from the stub must still fall back, or the guard is not guarding.");
    }

    @Test
    public void fluidTankReadoutUsesLangUnits() {
        Assertions.assertEquals("174 / 500 MB_X",
                LocaleUtil.formatFluidTank(174, 500, false, false));
        Assertions.assertEquals("174 / 500 MB_PLURAL_X",
                LocaleUtil.formatFluidTank(174, 500, true, false));
        Assertions.assertEquals("2 / 4 B_X",
                LocaleUtil.formatFluidTank(2000, 4000, false, true));
        Assertions.assertEquals("2 / 4 B_PLURAL_X",
                LocaleUtil.formatFluidTank(2000, 4000, true, true));
    }

    @Test
    public void rateSuffixIsAFormatKey_soWordOrderIsTranslatable() {
        // The whole point of making these format keys rather than concatenated suffixes: the quantity
        // arrives as %s, so a translation may place it anywhere. Assert the quantity is substituted
        // INTO the pattern rather than having the pattern appended after it.
        Assertions.assertEquals("40 MB_X@TICK",
                LocaleUtil.formatFluidFlow(40, FlowDisplay.PER_TICK, false, false));
        Assertions.assertEquals("40 MB_PLURAL_X FOR EACH TICK",
                LocaleUtil.formatFluidFlow(40, FlowDisplay.PER_TICK, true, false));
        // 40 mB/t is 800 mB/s — under a bucket, so it stays in millibuckets even with abbreviation on.
        Assertions.assertEquals("800 MB_PLURAL_X FOR EACH SECOND",
                LocaleUtil.formatFluidFlow(40, FlowDisplay.PER_SECOND, true, true));
        // 100 mB/t is 2000 mB/s, which does cross into buckets — the only way to reach the bucket keys.
        // Note the "2.0", not "2": the flow formatter keeps a trailing .0 where formatFluidTank trims it
        // ("2 / 4 B"). Characterizing the behaviour as it stands; the asymmetry is logged in todos.md.
        Assertions.assertEquals("2.0 B_PLURAL_X FOR EACH SECOND",
                LocaleUtil.formatFluidFlow(100, FlowDisplay.PER_SECOND, true, true));
    }

    @Test
    public void heatReadoutUsesLangTemperatureKey() {
        Assertions.assertEquals("20.00 DEG", LocaleUtil.localizeHeat(20.0d));
    }
}
