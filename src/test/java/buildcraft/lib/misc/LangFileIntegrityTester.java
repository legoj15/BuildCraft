/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/** Structural checks on the shipped translation files.
 *
 * <p>Every assertion here corresponds to a bug that actually shipped and was found by hand during the
 * 2026-07-21 lang audit — none of them are hypothetical:
 * <ul>
 * <li>{@code gui.list.nbt} had a double-escaped {@code \\n}, so Chinese players saw the two literal
 *     characters {@code \n} in the middle of a tooltip.</li>
 * <li>{@code buildcraft.item.nonclean.usage} dropped both {@code %s} placeholders, so the Map Location
 *     and Gate Copier hints named Shift and right-click literally instead of the player's real keybinds.</li>
 * <li>Deleting a dead key from en_us without mirroring it into zh_cn leaves a stale zh-only key.</li>
 * </ul>
 *
 * <p>A translation file is data, not code, so nothing else in the build validates it: a wrong value
 * compiles, loads and renders. These tests are the only gate. */
public class LangFileIntegrityTester {

    private static final String LANG_DIR = "/assets/buildcraftunofficial/lang/";
    private static final String EN = "en_us.json";
    private static final String ZH = "zh_cn.json";

    /** Matches one {@code "key":} declaration at the start of a line. The lang files are written one
     *  entry per line, which is what lets us count RAW declarations — Gson silently keeps only the last
     *  of a duplicated key, so a parsed map can never reveal one. */
    private static final Pattern KEY_LINE = Pattern.compile("(?m)^\\s*\"((?:[^\"\\\\]|\\\\.)+)\"\\s*:");

    /** {@code %s} / {@code %d} and their positional {@code %1$s} forms. */
    private static final Pattern FORMAT_SPEC = Pattern.compile("%(?:\\d+\\$)?[sd]");

    private static String readRaw(String fileName) {
        try (InputStream in = LangFileIntegrityTester.class.getResourceAsStream(LANG_DIR + fileName)) {
            Assertions.assertNotNull(in, LANG_DIR + fileName + " is not on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + fileName, e);
        }
    }

    private static Map<String, String> parse(String fileName) {
        try (InputStream in = LangFileIntegrityTester.class.getResourceAsStream(LANG_DIR + fileName)) {
            Assertions.assertNotNull(in, LANG_DIR + fileName + " is not on the test classpath");
            JsonObject obj = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            Map<String, String> out = new LinkedHashMap<>();
            obj.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            return out;
        } catch (IOException e) {
            throw new AssertionError("Could not read " + fileName, e);
        }
    }

    private static List<String> rawKeys(String fileName) {
        List<String> keys = new ArrayList<>();
        Matcher m = KEY_LINE.matcher(readRaw(fileName));
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static int countFormatSpecs(String value) {
        int n = 0;
        Matcher m = FORMAT_SPEC.matcher(value);
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    public void bothFilesParseAsJson() {
        Assertions.assertFalse(parse(EN).isEmpty(), "en_us.json parsed to zero entries");
        Assertions.assertFalse(parse(ZH).isEmpty(), "zh_cn.json parsed to zero entries");
    }

    @Test
    public void noDuplicateKeys() {
        for (String file : new String[] { EN, ZH }) {
            List<String> raw = rawKeys(file);
            Set<String> seen = new HashSet<>();
            List<String> dupes = new ArrayList<>();
            for (String k : raw) {
                if (!seen.add(k)) {
                    dupes.add(k);
                }
            }
            Assertions.assertTrue(dupes.isEmpty(),
                    file + " declares the same key more than once " + dupes
                            + " — JSON parsers silently keep the LAST one, so the earlier value is discarded"
                            + " with no error anywhere.");
            // Guards the regex itself: if the one-entry-per-line layout is ever reflowed, the raw scan
            // would under-count and every duplicate check above would pass vacuously.
            Assertions.assertEquals(parse(file).size(), raw.size(),
                    file + ": raw key-line count does not match the parsed entry count, so the"
                            + " one-entry-per-line assumption behind the duplicate scan no longer holds.");
        }
    }

    @Test
    public void translationHasNoStaleKeys() {
        Map<String, String> en = parse(EN);
        List<String> stale = parse(ZH).keySet().stream().filter(k -> !en.containsKey(k)).sorted().toList();
        Assertions.assertTrue(stale.isEmpty(),
                "zh_cn.json carries keys that no longer exist in en_us.json " + stale
                        + " — a key deleted from en_us must be deleted from every translation in the SAME"
                        + " change, or the translation silently accumulates dead weight.");
    }

    /** en_us keys that are deliberately NOT translated into zh_cn. Keep this tiny: it is for text no player ever
     *  reads in a localised client (dev-only command output and the like), never for "I'll translate it later".
     *  Today nothing needs it, so the set is empty; add a key here with a one-line reason on the entry. */
    private static final Set<String> ENGLISH_ONLY = Set.of();

    @Test
    public void translationCoversEveryEnglishKey() {
        Map<String, String> zh = parse(ZH);
        List<String> missing = parse(EN).keySet().stream()
                .filter(k -> !zh.containsKey(k) && !ENGLISH_ONLY.contains(k))
                .sorted()
                .toList();
        Assertions.assertTrue(missing.isEmpty(),
                "zh_cn.json lacks " + missing.size() + " key(s) that en_us.json has " + missing
                        + " — a Chinese player sees the raw English fallback for each. Translate the key (matching"
                        + " the terminology already in zh_cn.json), or, if it is genuinely never shown to a"
                        + " player, list it in ENGLISH_ONLY with the reason.");
    }

    @Test
    public void englishOnlyAllowlistHasNoStaleEntries() {
        Map<String, String> en = parse(EN);
        Map<String, String> zh = parse(ZH);
        List<String> stale = ENGLISH_ONLY.stream()
                .filter(k -> !en.containsKey(k) || zh.containsKey(k))
                .sorted()
                .toList();
        Assertions.assertTrue(stale.isEmpty(),
                "ENGLISH_ONLY entries that no longer exist in en_us or have since been translated " + stale
                        + " — drop them from the allowlist.");
    }

    /** The Zone Planner is one machine with one Chinese name, the block's. Its help-ledger title and the prose that
     *  points at "the planner" once drifted into a second spelling (规划器, "-er" for a device, vs the block's
     *  规划机, "-machine"), so a player saw two different names for the same GUI. */
    @Test
    public void zonePlannerIsNamedConsistentlyInChinese() {
        Map<String, String> zh = parse(ZH);
        String blockName = zh.get("block.buildcraftunofficial.zone_planner");
        Assertions.assertNotNull(blockName, "zh_cn must name the Zone Planner block");
        Assertions.assertEquals(blockName, zh.get("buildcraft.help.zone_planner.map.title"),
                "the Zone Planner's help-ledger title must be the block's own name");

        String wrongForm = "区域规划器";
        List<String> drifted = zh.entrySet().stream()
                .filter(e -> e.getValue().contains(wrongForm))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        Assertions.assertTrue(drifted.isEmpty(),
                "these zh_cn values spell the Zone Planner " + wrongForm + " instead of " + blockName + " " + drifted);

        // The Filler Planner is a different item (填充规划器) and keeps its own name; only the Zone Planner's own help
        // ledger is in scope for the bare "规划器" shorthand, which must read as the block name instead.
        List<String> bareShorthand = zh.entrySet().stream()
                .filter(e -> e.getKey().startsWith("buildcraft.help.zone_planner.") && e.getValue().contains("规划器"))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        Assertions.assertTrue(bareShorthand.isEmpty(),
                "the Zone Planner help ledger refers to the machine as 规划器 instead of " + blockName + " "
                        + bareShorthand);
    }

    /** Anything robotics: the robot and its entity, the docking station, the redstone board item and every
     *  {@code buildcraft.boardRobot*} programme name/description. Matched on the KEY so a newly added board
     *  is covered the moment its lang key lands, with no list to maintain. */
    private static final Pattern ROBOTICS_KEY = Pattern.compile("(?i)robot|board|station");

    @Test
    public void roboticsKeysAreTranslatedInChinese() {
        Map<String, String> en = parse(EN);
        Map<String, String> zh = parse(ZH);
        List<String> roboticsKeys = en.keySet().stream().filter(k -> ROBOTICS_KEY.matcher(k).find()).sorted().toList();

        // Liveness: the filter must actually see the robotics block, or the checks below pass vacuously.
        Assertions.assertTrue(roboticsKeys.contains("item.buildcraftunofficial.robot"), "filter lost the robot item");
        Assertions.assertTrue(roboticsKeys.contains("buildcraft.boardRobotPicker"), "filter lost the board names");
        Assertions.assertTrue(roboticsKeys.contains("buildcraft.boardRobotPicker.desc"), "filter lost the board descs");

        assertTranslatedInChinese(en, zh, roboticsKeys, "robotics keys (matched by name)");
    }

    /** A string literal of the form {@code "..."} in Java source, escapes included. */
    private static final Pattern JAVA_STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    /** The name filter above misses robotics-owned strings whose keys say nothing about robots: the robot's
     *  charge readout ({@code tip.charge.*}) and the Zone Planner help ledger ({@code buildcraft.help.zone_planner.*})
     *  both shipped English-only in zh_cn that way. So the robotics CODE is the second source of truth: every
     *  en_us key a {@code buildcraft/robotics/**} file names as a string literal must be translated too. */
    @Test
    public void langKeysNamedByRoboticsCodeAreTranslatedInChinese() {
        Map<String, String> en = parse(EN);
        Map<String, String> zh = parse(ZH);
        Set<String> referenced = new HashSet<>();
        for (MainSourceSet.SourceFile file : MainSourceSet.javaFiles()) {
            if (!file.relativePath().startsWith("buildcraft/robotics/")) {
                continue;
            }
            Matcher m = JAVA_STRING_LITERAL.matcher(file.text());
            while (m.find()) {
                if (en.containsKey(m.group(1))) {
                    referenced.add(m.group(1));
                }
            }
        }
        List<String> keys = referenced.stream().sorted().toList();

        // Liveness: the scan must actually see the robotics code, or the checks below pass vacuously.
        Assertions.assertTrue(keys.contains("tip.charge.fullcharge"), "scan lost ItemRobot's charge readout");
        Assertions.assertTrue(keys.contains("buildcraft.help.zone_planner.map.title"), "scan lost GuiZonePlanner");

        assertTranslatedInChinese(en, zh, keys, "lang keys named by robotics code");
    }

    /** Fails on any of {@code keys} that zh_cn lacks, leaves blank, or copies verbatim from English. A value with no
     *  letters at all (a pure format string such as {@code "%s %s"}) is language-neutral and may match exactly. */
    private static void assertTranslatedInChinese(Map<String, String> en, Map<String, String> zh, List<String> keys,
            String what) {
        List<String> missing = keys.stream().filter(k -> !zh.containsKey(k)).toList();
        Assertions.assertTrue(missing.isEmpty(),
                "zh_cn.json lacks " + missing.size() + " " + what + " that en_us.json has " + missing
                        + " — a Chinese player sees the English text (or the raw key) for these. The robotics"
                        + " strings were added after the 'zh_cn at 100%' sweep and nothing enforced coverage.");

        List<String> untranslated = keys.stream()
                .filter(k -> zh.containsKey(k) && (zh.get(k).isBlank()
                        || (zh.get(k).equals(en.get(k)) && FORMAT_SPEC.matcher(en.get(k)).replaceAll("")
                                .chars().anyMatch(Character::isLetter))))
                .toList();
        Assertions.assertTrue(untranslated.isEmpty(),
                "zh_cn.json carries these " + what + " blank or copied verbatim from English " + untranslated);
    }

    /** A whole {@code "chat.…"} translation key written as a string literal. A key assembled at runtime
     *  ({@code "chat.pipe." + x + ".mode"}) shows up as a literal ending in a dot and is reported as missing. */
    private static final Pattern CHAT_KEY_LITERAL = Pattern.compile("\"(chat\\.[A-Za-z0-9_.]+)\"");

    /** Messages the server sends a player ride on {@code chat.*} keys. When the Iron/Diamond pipe wrench message was
     *  re-keyed for the creative engine, the two {@code chat.pipe.*.iron.mode} entries went with it but the pipe code
     *  still built them by concatenation, so every wrench click on those pipes showed the raw key — and no test saw
     *  it, because nothing compared the keys the code names with the keys the lang files have. */
    @Test
    public void chatKeysNamedByCodeExistInBothLanguages() {
        Set<String> referenced = new HashSet<>();
        for (MainSourceSet.SourceFile file : MainSourceSet.javaFiles()) {
            Matcher m = CHAT_KEY_LITERAL.matcher(file.text());
            while (m.find()) {
                referenced.add(m.group(1));
            }
        }
        // Liveness: the scan must actually see the chat keys, or the checks below pass vacuously.
        Assertions.assertTrue(referenced.contains("chat.gateCopier.gateCopied"), "scan lost the gate copier messages");
        Assertions.assertTrue(referenced.contains("chat.engine.creative.mode"), "scan lost the creative engine message");

        for (String file : new String[] { EN, ZH }) {
            Map<String, String> lang = parse(file);
            List<String> missing = referenced.stream().filter(k -> !lang.containsKey(k)).sorted().toList();
            Assertions.assertTrue(missing.isEmpty(),
                    file + " lacks chat keys that the code sends to players " + missing
                            + " — the player sees the raw key. (A key built by concatenation cannot be checked:"
                            + " write each key out in full.)");
        }
    }

    /** Painted pipes and charged paintbrushes are named through a two-argument format key (colour, then the base
     *  item name) so each language owns the word order; see {@code ColouredItemNameTest} for the Java side. */
    @Test
    public void colouredItemNameFormatKeysExistInBothLanguages() {
        for (String file : new String[] { EN, ZH }) {
            Map<String, String> lang = parse(file);
            for (String key : new String[] { "item.buildcraftunofficial.pipe.coloured",
                    "item.buildcraftunofficial.paintbrush.coloured" }) {
                String value = lang.get(key);
                Assertions.assertNotNull(value, file + " is missing the colour-name format key " + key);
                Assertions.assertEquals(2, countFormatSpecs(value),
                        file + ": " + key + " must take exactly the colour and the base name (two placeholders)");
            }
        }
    }

    /** The robot's partial-charge line ({@code ItemRobot}) is one format key taking the percentage, so each language
     *  places the number itself instead of the code gluing {@code "50% "} in front of a translated word. */
    @Test
    public void chargePercentFormatKeyExistsInBothLanguages() {
        for (String file : new String[] { EN, ZH }) {
            String value = parse(file).get("tip.charge.percent");
            Assertions.assertNotNull(value, file + " is missing the charge-percentage format key");
            Assertions.assertEquals(1, countFormatSpecs(value),
                    file + ": tip.charge.percent must take exactly the percentage (one placeholder)");
            // Vanilla reads '%%' as a literal percent sign; a bare '%' in front of a space would be misparsed.
            Assertions.assertTrue(String.format(value, 50).contains("50%"),
                    file + ": tip.charge.percent must render the number followed by a literal percent sign, got "
                            + String.format(value, 50));
        }
    }

    /** The four 1.12.2-era {@code tile.*.name} titles each duplicated a modern {@code block.*} key with the same
     *  text, and the GUI titles were still pointed at the legacy copies — two keys to keep in sync for one name,
     *  and a rename done on only one of them silently splits the block's item name from its screen title. */
    @Test
    public void noLegacyTileNameKeys() {
        for (String file : new String[] { EN, ZH }) {
            List<String> legacy = parse(file).keySet().stream().filter(k -> k.startsWith("tile.")).sorted().toList();
            Assertions.assertTrue(legacy.isEmpty(),
                    file + " still declares legacy tile.* keys " + legacy
                            + " — GUI titles and tile display names read the block.buildcraftunofficial.* key.");
        }
    }

    @Test
    public void blockKeysThatReplacedTheLegacyTileKeysExistInBothLanguages() {
        for (String file : new String[] { EN, ZH }) {
            Map<String, String> lang = parse(file);
            for (String key : new String[] { "block.buildcraftunofficial.autoworkbench_item",
                    "block.buildcraftunofficial.engine_stone", "block.buildcraftunofficial.engine_iron",
                    "block.buildcraftunofficial.library" }) {
                Assertions.assertTrue(lang.containsKey(key) && !lang.get(key).isBlank(),
                        file + " must carry " + key + " — it is the GUI title for the machine");
            }
        }
    }

    @Test
    public void mainCodeNeverReadsLegacyTileNameKeys() {
        List<String> offenders = MainSourceSet.javaFiles().stream()
                .filter(f -> f.text().contains("\"tile."))
                .map(MainSourceSet.SourceFile::relativePath)
                .sorted()
                .toList();
        Assertions.assertTrue(offenders.isEmpty(),
                "These files still read a legacy tile.* lang key " + offenders
                        + " — use the block.buildcraftunofficial.* key (the legacy keys no longer exist).");
    }

    @Test
    public void translationPreservesFormatPlaceholders() {
        Map<String, String> en = parse(EN);
        List<String> bad = new ArrayList<>();
        parse(ZH).forEach((key, zhValue) -> {
            String enValue = en.get(key);
            if (enValue == null) {
                return; // reported by translationHasNoStaleKeys
            }
            int expected = countFormatSpecs(enValue);
            int actual = countFormatSpecs(zhValue);
            if (expected != actual) {
                bad.add(key + " (en=" + expected + ", zh=" + actual + ")");
            }
        });
        Assertions.assertTrue(bad.isEmpty(),
                "Translated values disagree with English on placeholder count " + bad
                        + " — placeholders are substituted at runtime, so dropping one hardcodes whatever"
                        + " the argument would have supplied (a keybind, an amount, an item name) and"
                        + " adding one throws at render time.");
    }

    @Test
    public void noDoubleEscapedNewlines() {
        for (String file : new String[] { EN, ZH }) {
            List<String> bad = parse(file).entrySet().stream()
                    // After JSON parsing a real newline escape is an actual '\n' character. A backslash
                    // followed by 'n' surviving here means the source wrote \\n, which renders literally.
                    .filter(e -> e.getValue().contains("\\n"))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            Assertions.assertTrue(bad.isEmpty(),
                    file + " has values containing a literal backslash-n " + bad
                            + " — the source wrote \\\\n where it meant \\n, so the player sees the two"
                            + " characters instead of a line break.");
        }
    }

    @Test
    public void xmlSplicedKeysCarryNoDoubleQuote() {
        // GuideManager assembles the stub guide page as an XML string and re-parses it; XmlPageLoader's
        // attribute reader stops at the first '"'. A translation containing one truncates the chapter
        // heading, and the failure surfaces as a broken guide page rather than as a lang typo.
        String key = "buildcraft.guide.page.wip.title";
        for (String file : new String[] { EN, ZH }) {
            String value = parse(file).get(key);
            if (value != null) {
                Assertions.assertFalse(value.contains("\""),
                        file + ": " + key + " is spliced into an XML attribute and must not contain a"
                                + " double-quote character (use 「」 or （）).");
            }
        }
    }
}
