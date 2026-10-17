/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.item;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import buildcraft.builders.snapshot.Snapshot;

/**
 * The F3+H "Date:" line of a used blueprint or template. It showed {@code Date.toString()} (English weekday and
 * month names) whatever language the player had picked; it now carries the creation date formatted for the
 * language the tooltip is built for.
 */
public class ItemSnapshotDateLineTester {

    /** 2026-06-15 12:00:00 UTC; whatever the machine's zone, still June 2026. */
    private static final Date CREATED = Date.from(Instant.parse("2026-06-15T12:00:00Z"));

    private static Snapshot.Header header() {
        return new Snapshot.Header(new Snapshot.Key(), new UUID(0, 7), CREATED, "house");
    }

    private static String shownDate(Locale locale) {
        Component line = ItemSnapshot.dateLine(header(), locale);
        TranslatableContents contents = (TranslatableContents) line.getContents();
        Assertions.assertEquals("item.snapshot.date", contents.getKey());
        Assertions.assertEquals(1, contents.getArgs().length);
        return String.valueOf(contents.getArgs()[0]);
    }

    @Test
    public void dateIsNotTheJavaDateToString() {
        Assertions.assertNotEquals(String.valueOf(CREATED), shownDate(Locale.US));
        Assertions.assertNotEquals(String.valueOf(CREATED), shownDate(Locale.GERMANY));
    }

    @Test
    public void dateFollowsTheTooltipLanguage() {
        String english = shownDate(Locale.US);
        String german = shownDate(Locale.GERMANY);
        Assertions.assertNotEquals(english, german);
        Assertions.assertTrue(english.startsWith("Jun "), english);
        // Day-month-year with dots; the day is the 14th to 16th depending on the zone the test runs in.
        Assertions.assertTrue(german.matches("1[456]\\.06\\.2026, \\d{2}:\\d{2}:\\d{2}"), german);
    }
}
