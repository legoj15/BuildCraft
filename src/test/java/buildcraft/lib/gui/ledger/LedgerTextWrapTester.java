/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.ledger;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Line fitting and break rules of {@link LedgerTextWrap}, the wrapper behind every ledger's title and body.
 *
 * <p>Plain JUnit with a fake measurer (6 px per character, 9 px per CJK character, 0 for a section-sign code), so
 * the numbers below are exact: a column of 60 px holds ten Latin letters or six CJK glyphs. A later change to the
 * wrapper could silently start cutting words mid-letter, opening a line with a full stop, or dropping a colour at a
 * break; each of those fails here with a wrong line, not a missing method. */
public class LedgerTextWrapTester {

    /** A character the fake measurer treats as wide: Han, kana, Hangul, CJK punctuation and fullwidth forms. */
    private static boolean wide(int cp) {
        return (cp >= 0x3000 && cp <= 0x9FFF) || (cp >= 0xAC00 && cp <= 0xD7AF) || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    private static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length();) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                i++; // the code letter that follows the section sign is zero-width too
                continue;
            }
            w += wide(cp) ? 9 : 6;
        }
        return w;
    }

    private static List<String> wrap(String text, int maxPx) {
        return LedgerTextWrap.wrap(text, maxPx, LedgerTextWrapTester::width);
    }

    /** The text with colour codes, spaces, hyphens and line feeds removed: what must survive any wrapping. */
    private static String plain(String s) {
        return s.replaceAll("§.", "").replace(" ", "").replace("-", "").replace("\n", "");
    }

    private static int visibleChars(String line) {
        String visible = line.replaceAll("§.", "");
        return visible.codePointCount(0, visible.length());
    }

    /** Every line is non-empty unless the input line it came from was, fits the column unless it is one character,
     * and the lines together hold exactly the input. */
    private static void assertWellFormed(String input, List<String> lines, int maxPx) {
        long emptyInputLines = Arrays.stream(input.split("\n", -1)).filter(String::isEmpty).count();
        Assertions.assertEquals(emptyInputLines, lines.stream().filter(String::isEmpty).count(),
            "empty lines for \"" + input + "\" at " + maxPx + " px: " + lines);
        for (String line : lines) {
            if (width(line) > maxPx) {
                Assertions.assertEquals(1, visibleChars(line),
                    "only a single character may overflow, got \"" + line + "\" at " + maxPx + " px in " + lines);
            }
        }
        Assertions.assertEquals(plain(input), plain(String.join("", lines)),
            "the lines must hold the input and nothing else, at " + maxPx + " px: " + lines);
    }

    private static final String ENGLISH = "The quick brown fox jumps over the lazy dog";
    private static final String RUSSIAN = "Электростанция питает двигатель через трубопровод";
    private static final String CHINESE = "建造者会按照蓝图放置方块，然后挖掘周围的泥土；最后“完成”。";
    private static final String MIXED = "请使用Redstone引擎为机器供电，每秒产生10MJ(最大值)。";
    private static final String CODED = "§cRed text §lthat wraps§r and then some more plain words here";
    private static final String LINES = "first line is long enough to wrap\n\nthird line";

    @Test
    public void plainEnglishWrapsAtSpaces() {
        Assertions.assertEquals(List.of("The quick", "brown fox", "jumps over", "the lazy", "dog"), wrap(ENGLISH, 60));
        // The space at a break is dropped, never left dangling on either line.
        for (String line : wrap(ENGLISH, 60)) {
            Assertions.assertEquals(line.strip(), line, "no leading or trailing space on \"" + line + "\"");
        }
    }

    @Test
    public void longRussianWordIsSplitWithAHyphen() {
        String word = "Электростанция"; // 14 letters, 84 px
        List<String> lines = wrap(word, 60);
        Assertions.assertEquals(2, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).endsWith("-"), "first part ends with a hyphen: " + lines);
        Assertions.assertFalse(lines.get(1).endsWith("-"), lines.toString());
        String head = lines.get(0).substring(0, lines.get(0).length() - 1);
        Assertions.assertTrue(head.length() >= 2 && lines.get(1).length() >= 2, "both halves hold 2+ letters: " + lines);
        Assertions.assertEquals(word, head + lines.get(1));
        Assertions.assertTrue(width(lines.get(0)) <= 60, "the hyphen must fit the column: " + lines);
        // The same holds when the tail would otherwise be a single letter: 11 letters in a 10-letter column.
        List<String> eleven = wrap("Электростан", 60);
        Assertions.assertEquals(List.of("Электрост-", "ан"), eleven);
        // Inside a sentence only the word that cannot fit is split.
        for (int px = 36; px <= 120; px += 6) {
            assertWellFormed(RUSSIAN, wrap(RUSSIAN, px), px);
        }
        Assertions.assertEquals(List.of("Reds-", "tone"), wrap("Redstone", 30));
    }

    @Test
    public void chineseLinesNeverOpenWithClosingMarksOrCloseWithOpeningOnes() {
        for (int px = 27; px <= 117; px += 9) {
            List<String> lines = wrap(CHINESE, px);
            assertWellFormed(CHINESE, lines, px);
            for (String line : lines) {
                Assertions.assertFalse("。，；”、：！？".indexOf(line.charAt(0)) >= 0,
                    "line starts with a closing mark: \"" + line + "\" at " + px + " px in " + lines);
                Assertions.assertFalse("“‘（「".indexOf(line.charAt(line.length() - 1)) >= 0,
                    "line ends with an opening mark: \"" + line + "\" at " + px + " px in " + lines);
                Assertions.assertFalse(line.contains("-") || line.contains(" "), "CJK breaks add no hyphen: " + line);
            }
        }
        Assertions.assertEquals(List.of("你好，", "世界。"), wrap("你好，世界。", 27));
        Assertions.assertEquals(List.of("你", "好，", "世", "界。"), wrap("你好，世界。", 18));
        // An ASCII comma closes a clause just as a fullwidth one does.
        Assertions.assertEquals(List.of("你", "好,", "世界"), wrap("你好,世界", 18));
    }

    @Test
    public void latinWordInsideChineseStaysWholeWhenItFits() {
        List<String> lines = wrap("请使用Redstone引擎为机器供电", 72);
        Assertions.assertEquals(List.of("请使用", "Redstone引擎", "为机器供电"), lines);
        for (int px = 54; px <= 117; px += 9) {
            List<String> mixed = wrap(MIXED, px);
            assertWellFormed(MIXED, mixed, px);
            Assertions.assertEquals(1, mixed.stream().filter(l -> l.contains("Redstone")).count(),
                "Redstone is split at " + px + " px: " + mixed);
        }
    }

    @Test
    public void colourCodesCarryOverBreaks() {
        Assertions.assertEquals(List.of("§cabc", "§cdef", "§cghi"), wrap("§cabc def ghi", 24));
        // A reset clears them: the line that starts after it is plain, not re-coloured.
        Assertions.assertEquals(List.of("§cabc", "def", "ghi"), wrap("§cabc §rdef ghi", 24));
        // Codes stack in order, and a split inside a word re-emits what is active at the split.
        Assertions.assertEquals(List.of("§c§labc", "§c§ldef"), wrap("§c§labc def", 24));
        Assertions.assertEquals(List.of("ab§cd-", "§cefgh"), wrap("ab§cdefgh", 24));
        // They are zero-width: a code never pushes a word onto the next line.
        Assertions.assertEquals(List.of("§cabc§r def"), wrap("§cabc§r def", 60));
    }

    @Test
    public void everySampleFitsAndRoundTripsAtEveryWidth() {
        for (String sample : new String[] {ENGLISH, RUSSIAN, CHINESE, MIXED, CODED, LINES, "  indented text", "a"}) {
            for (int px = 6; px <= 150; px += 3) {
                assertWellFormed(sample, wrap(sample, px), px);
            }
        }
    }

    @Test
    public void lineFeedsForceBreaksAndEmptyLinesSurvive() {
        Assertions.assertEquals(List.of("ab", "", "cd"), wrap("ab\n\ncd", 600));
        Assertions.assertEquals(List.of(""), wrap("", 60));
        // A colour set before a line feed is still set after it.
        Assertions.assertEquals(List.of("§ca", "§cb"), wrap("§ca\nb", 60));
    }
}
