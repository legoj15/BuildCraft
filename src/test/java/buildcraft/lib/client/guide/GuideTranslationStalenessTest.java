/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package buildcraft.lib.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import buildcraft.TestHelper;

/** The guide-book pages under {@code compat/buildcraft/guide/zh_cn} and {@code ru_ru} are translations of the en_us
 *  pages, validated structurally by {@code scripts/guide-translation-validate.py}. That validation is a point-in-time
 *  check, so this test pins its result: {@code scripts/guide-translation.stamp} records the sha256 of the en_us source
 *  AND of the validated translation for every page, and this recomputes both exactly as the script's {@code sha256}
 *  does (UTF-8 bytes, BOM stripped, carriage returns removed, so a CRLF checkout hashes the same).
 *
 *  <p>It fails when an en_us page changed after its translation was stamped (stale translation), when a translated
 *  page was edited by hand after validation, when a translated page exists without a stamp entry, or when a stamp
 *  entry lost its file. The remedy is always the same: retranslate the flagged page (or drop it, but never just
 *  re-stamp an unfixed one — {@code --stamp} refuses while any page is invalid) and run
 *  {@code python scripts/guide-translation-validate.py <locale> --stamp}. */
class GuideTranslationStalenessTest {

    private static final String GUIDE = "src/main/resources/assets/buildcraftunofficial/compat/buildcraft/guide";
    private static final String STAMP = "scripts/guide-translation.stamp";
    private static final String[] LOCALES = {"zh_cn", "ru_ru"};

    @Test
    void translationsMatchTheirStampedSources() throws IOException {
        Path root = TestHelper.repoRoot();

        Map<String, String[]> stamped = new TreeMap<>(); // "locale/page" -> [en sha, translation sha]
        Path stampFile = root.resolve(STAMP);
        if (Files.isRegularFile(stampFile)) {
            for (String line : Files.readAllLines(stampFile, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split(" ", 3);
                assertEquals(3, parts.length, STAMP + " has a malformed line: " + line);
                stamped.put(parts[2], new String[]{parts[0], parts[1]});
            }
        }

        TreeSet<String> translated = new TreeSet<>();
        for (String locale : LOCALES) {
            Path dir = root.resolve(GUIDE).resolve(locale);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(p -> p.toString().endsWith(".md")).forEach(p ->
                    translated.add(locale + "/" + dir.relativize(p).normalize().toString().replace('\\', '/')));
            }
        }

        List<String> problems = new ArrayList<>();
        for (String page : new TreeSet<>(stamped.keySet())) {
            if (!translated.contains(page)) problems.add(page + ": stamped but the translated file is gone");
        }
        for (String page : translated) {
            String[] entry = stamped.get(page);
            if (entry == null) {
                problems.add(page + ": translated but not stamped — run the validator with --stamp");
                continue;
            }
            int slash = page.indexOf('/');
            String locale = page.substring(0, slash);
            Path en = root.resolve(GUIDE).resolve("en_us").resolve(page.substring(slash + 1));
            Path tr = root.resolve(GUIDE).resolve(page);
            if (!Files.isRegularFile(en)) {
                problems.add(page + ": the en_us source disappeared");
                continue;
            }
            if (!entry[0].equals(sha256(en))) {
                problems.add(page + ": STALE — the en_us source changed since translation; retranslate and re-stamp");
            }
            if (!entry[1].equals(sha256(tr))) {
                problems.add(page + ": edited after validation; revalidate (or undo the hand edit) and re-stamp");
            }
        }

        assertTrue(problems.isEmpty(),
            String.join("\n", problems)
                + "\nRemedy: retranslate the flagged page, then run python scripts/guide-translation-validate.py <locale> --stamp.");
    }

    private static String sha256(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int start = bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF ? 3 : 0;
        String text = new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8).replace("\r", "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("JVM without SHA-256", e);
        }
    }
}
