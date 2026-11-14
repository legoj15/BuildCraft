/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Line wrapper for ledger text. Pure: it knows no Minecraft type, the caller passes the font's width function.
 *
 * <p>Vanilla's {@code Font.split} breaks only at ASCII spaces, so it cut an over-long word mid-letter without a
 * hyphen, let a Chinese line start with a full stop and split a Latin word inside a Chinese sentence. This wrapper
 * keeps the words whole where they fit and follows these rules:
 * <ul>
 * <li>A line feed forces a break; a break at a space drops the space.</li>
 * <li>Between two characters either of which is CJK (Han, kana, Hangul, CJK punctuation, fullwidth forms) a line may
 * break with no space involved. A run of other characters, such as a Latin or Cyrillic word or a number, is one
 * unbreakable word.</li>
 * <li>A line never starts with a closing mark ({@link #CLOSING}) and never ends with an opening mark
 * ({@link #OPENING}).</li>
 * <li>A single word wider than the column is split. When both neighbours of the cut are letters the first part gets a
 * trailing hyphen (which must fit the column), and neither part is left with fewer than two letters when the word
 * allows it.</li>
 * <li>{@code §x} legacy format codes are zero-width and stay whole; the codes active at a break are written at the
 * start of the next line, and {@code §r} clears them.</li>
 * </ul>
 *
 * <p>When the rules cannot be met (a unit of text that no allowed cut can fit) the line is broken anyway: no returned
 * line is empty unless its input line was, none is wider than the column unless it is one character, and the lines
 * joined give the input back minus the hyphens and spaces added or dropped at breaks (and with the re-emitted codes). */
public final class LedgerTextWrap {
    /** Starts a two-character legacy format code. */
    private static final char CODE = '§';

    /** Marks that never start a line: CJK and ASCII closing punctuation, brackets, quotes, dashes, percent. */
    private static final String CLOSING = "，。、；：！？）」』”’》"
        + "〉】…—％〕］｝〗〙〛,.;:!?)]}%";

    /** Marks that never end a line: CJK and ASCII opening brackets and quotes. */
    private static final String OPENING = "（「『“‘《〈【〔［｛〖〘"
        + "〚([{";

    private LedgerTextWrap() {}

    /** Wraps {@code text} to lines no wider than {@code maxWidth}, as measured by {@code widthOf} (callers pass
     * {@code font::width}). The measurer must treat {@code §x} as zero-width, as vanilla's does.
     *
     * @return at least one line; an empty input line gives an empty string */
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> widthOf) {
        List<String> out = new ArrayList<>();
        Wrapper wrapper = new Wrapper(Math.max(1, maxWidth), widthOf, out);
        String source = text == null ? "" : text;
        int start = 0;
        while (true) {
            int lineFeed = source.indexOf('\n', start);
            wrapper.paragraph(lineFeed < 0 ? source.substring(start) : source.substring(start, lineFeed));
            if (lineFeed < 0) break;
            start = lineFeed + 1;
        }
        return out;
    }

    // ------------------------------------------------------------------ character classes

    static boolean isCjk(int cp) {
        return (cp >= 0x1100 && cp <= 0x11FF)        // Hangul jamo
            || (cp >= 0x2E80 && cp <= 0x2FDF)        // CJK and Kangxi radicals
            || (cp >= 0x3000 && cp <= 0x303F)        // CJK symbols and punctuation
            || (cp >= 0x3040 && cp <= 0x30FF)        // Hiragana, Katakana
            || (cp >= 0x3100 && cp <= 0x318F)        // Bopomofo, Hangul compatibility jamo
            || (cp >= 0x31C0 && cp <= 0x33FF)        // CJK strokes, kana extensions, enclosed and compatibility
            || (cp >= 0x3400 && cp <= 0x4DBF)        // Han extension A
            || (cp >= 0x4E00 && cp <= 0x9FFF)        // Han
            || (cp >= 0xAC00 && cp <= 0xD7AF)        // Hangul syllables
            || (cp >= 0xF900 && cp <= 0xFAFF)        // CJK compatibility ideographs
            || (cp >= 0xFE30 && cp <= 0xFE4F)        // CJK compatibility forms
            || (cp >= 0xFF00 && cp <= 0xFFEF)        // fullwidth and halfwidth forms
            || (cp >= 0x20000 && cp <= 0x3134F);     // Han extensions B and later
    }

    static boolean isClosing(int cp) {
        return cp < 0x10000 && CLOSING.indexOf(cp) >= 0;
    }

    static boolean isOpening(int cp) {
        return cp < 0x10000 && OPENING.indexOf(cp) >= 0;
    }

    /** A letter a hyphen may follow or precede; CJK is excluded, it breaks without one. */
    private static boolean isHyphenLetter(int cp) {
        return Character.isLetter(cp) && !isCjk(cp);
    }

    // ------------------------------------------------------------------ parsed text

    /** One visible character with the format codes written directly before it. */
    private record Cell(String codes, int cp, String ch) {
        String text() {
            return codes + ch;
        }
    }

    /** A stretch of text with no break opportunity inside it, and the spaces after it (dropped at a break). */
    private record Unit(List<Cell> cells, List<Cell> trailing) {}

    private static String text(List<Cell> cells, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) sb.append(cells.get(i).text());
        return sb.toString();
    }

    /** The format codes in force, as vanilla's {@code Style.applyLegacyFormat} would leave them. */
    private static final class Active {
        private final List<String> codes = new ArrayList<>();

        void applyAll(String pairs) {
            for (int i = 0; i + 1 < pairs.length(); i += 2) {
                apply(pairs.substring(i, i + 2));
            }
        }

        private void apply(String pair) {
            char c = Character.toLowerCase(pair.charAt(1));
            if (c == 'r') {
                codes.clear();
            } else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                // A colour also switches off every format set before it.
                codes.clear();
                codes.add(pair);
            } else if (c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o') {
                if (!codes.contains(pair)) codes.add(pair);
            }
        }

        String emit() {
            if (codes.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String code : codes) sb.append(code);
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ wrapping

    private static final class Wrapper {
        private final int max;
        private final ToIntFunction<String> widthOf;
        private final List<String> out;
        private final Active active = new Active();

        /** The codes in force at the start of the current line, before its first character's own codes. */
        private String prefix = "";
        /** The current line without {@link #prefix}; it keeps each character's own codes. */
        private final StringBuilder body = new StringBuilder();
        private boolean hasContent;
        /** The codes in force just after the first character's own, and how long those own codes are. Together they
         * replace the first character's codes at the start of a line, so a line after a {@code §r} starts plain. */
        private String firstEmit = "";
        private int firstCodesLength;

        Wrapper(int max, ToIntFunction<String> widthOf, List<String> out) {
            this.max = max;
            this.widthOf = widthOf;
            this.out = out;
        }

        private boolean fits(String extra) {
            return widthOf.applyAsInt(prefix + body + extra) <= max;
        }

        private void append(Cell cell) {
            active.applyAll(cell.codes());
            if (!hasContent) {
                hasContent = true;
                firstEmit = active.emit();
                firstCodesLength = cell.codes().length();
            }
            body.append(cell.codes()).append(cell.ch());
        }

        /** Ends the current line. {@code dropped} are the spaces at the break: gone, but their codes still count. */
        private void endLine(List<Cell> dropped, String tailCodes) {
            if (dropped != null) {
                for (Cell space : dropped) {
                    active.applyAll(space.codes());
                    body.append(space.codes());
                }
            }
            active.applyAll(tailCodes);
            body.append(tailCodes);
            out.add(firstEmit + body.substring(firstCodesLength));
            body.setLength(0);
            hasContent = false;
            prefix = active.emit();
        }

        void paragraph(String para) {
            List<Cell> cells = new ArrayList<>();
            StringBuilder pending = new StringBuilder();
            int i = 0;
            while (i < para.length()) {
                if (para.charAt(i) == CODE) {
                    int len = i + 1 < para.length() ? 2 : 1;
                    pending.append(para, i, i + len);
                    i += len;
                } else {
                    int cp = para.codePointAt(i);
                    int len = Character.charCount(cp);
                    cells.add(new Cell(pending.toString(), cp, para.substring(i, i + len)));
                    pending.setLength(0);
                    i += len;
                }
            }
            String tailCodes = pending.toString();

            if (cells.isEmpty()) {
                if (tailCodes.isEmpty()) {
                    out.add("");
                } else {
                    out.add(prefix + tailCodes);
                    active.applyAll(tailCodes);
                    prefix = active.emit();
                }
                return;
            }

            List<Unit> units = units(cells);
            for (int u = 0; u < units.size(); u++) {
                Unit unit = units.get(u);
                if (hasContent) {
                    List<Cell> gap = units.get(u - 1).trailing();
                    if (fits(text(gap, 0, gap.size()) + text(unit.cells(), 0, unit.cells().size()))) {
                        for (Cell space : gap) append(space);
                        for (Cell cell : unit.cells()) append(cell);
                        continue;
                    }
                    endLine(gap, "");
                }
                place(unit.cells());
            }
            endLine(units.get(units.size() - 1).trailing(), tailCodes);
        }

        /** Puts a unit at the start of an empty line, splitting it over several lines when it is too wide. The last
         * part stays open so the next unit can join it. */
        private void place(List<Cell> cells) {
            int size = cells.size();
            int from = 0;
            while (true) {
                int fit = size - from == 1 ? size : longestFit(cells, from);
                if (fit == size) {
                    for (int i = from; i < size; i++) append(cells.get(i));
                    return;
                }
                int cut = chooseCut(cells, from, fit);
                boolean hyphen = cut < 0;
                if (hyphen) cut = -cut;
                // A space kept inside the unit (before a closing mark, after an opening one) is no place to break, but
                // a forced cut can still land beside one. It goes like any space at a break, so no line begins or ends
                // with it; a stretch of nothing but spaces (an indent) is left alone.
                int end = cut;
                while (end > from && cells.get(end - 1).cp() == ' ') end--;
                if (end == from) end = cut;
                int next = cut;
                while (next < size && cells.get(next).cp() == ' ') next++;
                if (next == size) next = cut;
                for (int i = from; i < end; i++) append(cells.get(i));
                if (hyphen) body.append('-');
                List<Cell> dropped = end == cut && next == cut ? null : new ArrayList<>(cells.subList(end, next));
                endLine(dropped, "");
                from = next;
            }
        }

        /** The largest {@code p} (up to the unit's size) for which {@code cells[from..p)} fits on a line, or
         * {@code from} when not even one cell does. A part never gets narrower by gaining a cell, so this gallops out
         * from {@code from} and then bisects: every measurement is about a column long, however long the word, which
         * keeps splitting a very long unbroken word from going quadratic. */
        private int longestFit(List<Cell> cells, int from) {
            int lo = from;                 // fits (an empty part)
            int hi = cells.size() + 1;     // does not fit (past the end)
            int step = 1;
            while (hi - lo > 1) {
                int probe = Math.min(lo + step, hi - 1);
                if (!fits(text(cells, from, probe))) {
                    hi = probe;
                    break;
                }
                lo = probe;
                step *= 2;
            }
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (fits(text(cells, from, mid))) lo = mid;
                else hi = mid;
            }
            return lo;
        }

        /** The index at which to cut {@code cells[from..]}, negated when the cut takes a hyphen. {@code fit} is
         * {@link #longestFit}: no first part longer than that fits, so the search starts there. */
        private int chooseCut(List<Cell> cells, int from, int fit) {
            int size = cells.size();
            int count = size - from;
            int top = Math.min(fit, size - 1);
            // The preferred cut: the longest first part that obeys every rule.
            for (int p = top; p > from; p--) {
                boolean hyphen = isHyphenLetter(cells.get(p - 1).cp()) && isHyphenLetter(cells.get(p).cp());
                if (hyphen) {
                    if (count >= 4 && (p - from < 2 || size - p < 2)) continue;
                    if (fits(text(cells, from, p) + "-")) return -p;
                } else {
                    if (isClosing(cells.get(p).cp()) || isOpening(cells.get(p - 1).cp())) continue;
                    // A space here was kept inside the unit on purpose: not a break.
                    if (cells.get(p - 1).cp() == ' ' || cells.get(p).cp() == ' ') continue;
                    if (fits(text(cells, from, p))) return p;
                }
            }
            // No allowed cut fits: break anyway, still hyphenating a word when the hyphen fits.
            for (int p = top; p > from; p--) {
                boolean hyphen = isHyphenLetter(cells.get(p - 1).cp()) && isHyphenLetter(cells.get(p).cp());
                if (hyphen && fits(text(cells, from, p) + "-")) return -p;
                if (fits(text(cells, from, p))) return p;
            }
            return from + 1;
        }
    }

    /** Splits a paragraph into units at its break opportunities. */
    private static List<Unit> units(List<Cell> cells) {
        List<Unit> units = new ArrayList<>();
        List<Cell> current = new ArrayList<>();
        List<Cell> spaces = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.cp() == ' ') {
                spaces.add(cell);
            } else if (current.isEmpty()) {
                // Spaces before the first character have nothing to break from: they are plain content.
                current.addAll(spaces);
                spaces.clear();
                current.add(cell);
            } else if (!spaces.isEmpty()) {
                int before = current.get(current.size() - 1).cp();
                if (isClosing(cell.cp()) || isOpening(before)) {
                    // No break here, so the spaces stay in the line.
                    current.addAll(spaces);
                    spaces.clear();
                    current.add(cell);
                } else {
                    units.add(new Unit(current, spaces));
                    current = new ArrayList<>();
                    current.add(cell);
                    spaces = new ArrayList<>();
                }
            } else {
                int before = current.get(current.size() - 1).cp();
                boolean cjk = isCjk(before) || isCjk(cell.cp());
                if (cjk && !isClosing(cell.cp()) && !isOpening(before)) {
                    units.add(new Unit(current, List.of()));
                    current = new ArrayList<>();
                }
                current.add(cell);
            }
        }
        if (current.isEmpty()) {
            current.addAll(spaces); // nothing but spaces
            spaces = new ArrayList<>();
        }
        units.add(new Unit(current, spaces));
        return units;
    }
}
