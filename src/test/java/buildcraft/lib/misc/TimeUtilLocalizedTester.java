/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link TimeUtil#formatLocalized} turns a snapshot's creation date into text in the player's language
 * (it used to be {@code Date.toString()}: English weekday and month names, whatever the language setting). The
 * assertions pin the parts of each locale's medium format that have been stable across CLDR releases (month names,
 * field order, separators) and compare locales against each other, so a JDK moving a comma or a space does not
 * break them.
 */
public class TimeUtilLocalizedTester {

    /** 2026-06-15 12:00:00 UTC; mid-year and mid-month so any time zone still lands in June 2026. */
    private static final Date NOON = Date.from(Instant.parse("2026-06-15T12:00:00Z"));

    @Test
    public void english_usesEnglishMonthAndMonthFirstOrder() {
        String text = TimeUtil.formatLocalized(NOON, Locale.US, ZoneOffset.UTC);
        Assertions.assertTrue(text.startsWith("Jun 15, 2026"), text);
    }

    @Test
    public void german_usesDottedDayMonthYear() {
        String text = TimeUtil.formatLocalized(NOON, Locale.GERMANY, ZoneOffset.UTC);
        Assertions.assertTrue(text.startsWith("15.06.2026"), text);
    }

    @Test
    public void japanese_usesYearFirstSlashes() {
        String text = TimeUtil.formatLocalized(NOON, Locale.JAPAN, ZoneOffset.UTC);
        Assertions.assertTrue(text.startsWith("2026/06/15"), text);
    }

    @Test
    public void french_spellsTheMonthInFrench() {
        String text = TimeUtil.formatLocalized(NOON, Locale.FRANCE, ZoneOffset.UTC);
        Assertions.assertTrue(text.contains("juin"), text);
    }

    @Test
    public void localesDisagreeWithEachOtherAndWithDateToString() {
        String english = TimeUtil.formatLocalized(NOON, Locale.US, ZoneOffset.UTC);
        String german = TimeUtil.formatLocalized(NOON, Locale.GERMANY, ZoneOffset.UTC);
        Assertions.assertNotEquals(english, german);
        Assertions.assertNotEquals(String.valueOf(NOON), english);
        Assertions.assertNotEquals(String.valueOf(NOON), german);
    }

    @Test
    public void timeOfDayIsShownToo() {
        // Date.toString() carried the time as well; the localized form must not lose it.
        Assertions.assertTrue(TimeUtil.formatLocalized(NOON, Locale.GERMANY, ZoneOffset.UTC).contains("12:00:00"));
    }

    @Test
    public void theGivenZoneDecidesTheLocalDayAndHour() {
        // Kiritimati is UTC+14: noon UTC is 02:00 the next day there.
        String utc = TimeUtil.formatLocalized(NOON, Locale.GERMANY, ZoneOffset.UTC);
        String kiritimati = TimeUtil.formatLocalized(NOON, Locale.GERMANY, ZoneId.of("Pacific/Kiritimati"));
        Assertions.assertTrue(utc.startsWith("15.06.2026") && utc.contains("12:00:00"), utc);
        Assertions.assertTrue(kiritimati.startsWith("16.06.2026") && kiritimati.contains("02:00:00"), kiritimati);
    }

    @Test
    public void rootLocaleStillFormats() {
        // The fallback used where no client language is available (a dedicated server).
        String text = TimeUtil.formatLocalized(NOON, Locale.ROOT, ZoneOffset.UTC);
        Assertions.assertTrue(text.contains("2026"), text);
        Assertions.assertNotEquals(String.valueOf(NOON), text);
    }

    @Test
    public void languageCodes_mapToTheirLocale() {
        Assertions.assertEquals(Locale.US, TimeUtil.localeFromLanguageCode("en_us"));
        Assertions.assertEquals(Locale.CHINA, TimeUtil.localeFromLanguageCode("zh_cn"));
        Assertions.assertEquals(Locale.GERMANY, TimeUtil.localeFromLanguageCode("de_de"));
        Assertions.assertEquals(Locale.forLanguageTag("pt-BR"), TimeUtil.localeFromLanguageCode("pt_br"));
        // Minecraft also ships codes with no region and with a three-letter language.
        Assertions.assertEquals(Locale.forLanguageTag("lol-US"), TimeUtil.localeFromLanguageCode("lol_us"));
        Assertions.assertEquals(Locale.GERMAN, TimeUtil.localeFromLanguageCode("de"));
    }

    @Test
    public void languageCodes_thatCannotBeParsedFallBackToRoot() {
        Assertions.assertEquals(Locale.ROOT, TimeUtil.localeFromLanguageCode(null));
        Assertions.assertEquals(Locale.ROOT, TimeUtil.localeFromLanguageCode(""));
        Assertions.assertEquals(Locale.ROOT, TimeUtil.localeFromLanguageCode("   "));
        Assertions.assertEquals(Locale.ROOT, TimeUtil.localeFromLanguageCode("!!_??"));
    }

    @Test
    public void localizedTextFollowsTheParsedLanguageCode() {
        // The two halves together: what the tooltip does with the client's language setting.
        String german = TimeUtil.formatLocalized(NOON, TimeUtil.localeFromLanguageCode("de_de"), ZoneOffset.UTC);
        String english = TimeUtil.formatLocalized(NOON, TimeUtil.localeFromLanguageCode("en_us"), ZoneOffset.UTC);
        Assertions.assertTrue(german.startsWith("15.06.2026"), german);
        Assertions.assertTrue(english.startsWith("Jun 15, 2026"), english);
    }
}
