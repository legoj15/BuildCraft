package buildcraft.lib.misc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;

import javax.annotation.Nullable;

public enum TimeUtil {
    ;

    public static String formatNow() {
        return format(LocalDateTime.now());
    }

    public static String format(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * {@code moment} as a medium-length local date and time in {@code locale}'s conventions ("Jun 15, 2026, 12:00:00
     * PM" for US English, "15.06.2026, 12:00:00" for German), shifted to {@code zone}. Pure: both the language and
     * the zone are arguments, so a caller on a server (which has no player language) decides its own fallback.
     */
    public static String formatLocalized(Date moment, Locale locale, ZoneId zone) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(zone)
            .format(moment.toInstant());
    }

    /**
     * The {@link Locale} for a Minecraft language code such as {@code en_us} or {@code zh_cn}, or
     * {@link Locale#ROOT} when the code is missing or cannot be parsed.
     */
    public static Locale localeFromLanguageCode(@Nullable String languageCode) {
        if (languageCode == null) return Locale.ROOT;
        // Minecraft's codes are language_REGION; a BCP 47 tag is language-REGION (an ill-formed tag yields ROOT).
        return Locale.forLanguageTag(languageCode.trim().replace('_', '-'));
    }
}
