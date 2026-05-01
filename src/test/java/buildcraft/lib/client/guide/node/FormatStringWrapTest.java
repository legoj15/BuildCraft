package buildcraft.lib.client.guide.node;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import buildcraft.lib.client.guide.font.IFontRenderer;

/** Reproducer test for the "or a" duplication bug observed in the Set Pipe Direction
 *  guide page. Walks the FormatString.split + wrap chain over a line that mirrors what
 *  XmlPageLoader produces for two adjacent inline links, and dumps every wrapped row.
 *  The test asserts that the total visible-character count across all rows equals the
 *  visible-character count of the input — anything else means wrap dropped or duplicated
 *  characters. */
public class FormatStringWrapTest {

    /** Stub font with a fixed per-character width and a configurable §-code stripper. */
    private static final class FixedWidthFont implements IFontRenderer {
        private final int charWidth;

        FixedWidthFont(int charWidth) {
            this.charWidth = charWidth;
        }

        @Override
        public int getStringWidth(String text) {
            // Strip §X codes (each 2 chars, 0 visible width).
            int visible = 0;
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '§' && i + 1 < text.length()) {
                    i += 2;
                    continue;
                }
                visible++;
                i++;
            }
            return visible * charWidth;
        }

        @Override public int getFontHeight(String text) { return 9; }
        @Override public int getMaxFontHeight() { return 9; }
        @Override public int drawString(String t, int x, int y, int c, boolean s, boolean cn, float sc) { return 0; }
        @Override public List<String> wrapString(String t, int w, boolean s, float sc) { return List.of(); }
    }

    private static int visibleLen(String s) {
        int v = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i += 2;
                continue;
            }
            v++;
            i++;
        }
        return v;
    }

    /** Wraps a formatted string to the given width and returns one entry per row containing
     *  that row's formatted text. */
    private static List<String> wrapAll(String formattedText, IFontRenderer font, int maxWidth) {
        List<String> rows = new ArrayList<>();
        FormatString next = FormatString.split(formattedText);
        // Cap iterations so a wrap-loop bug that produces non-shrinking remainders can't
        // hang the test.
        for (int safety = 0; safety < 200 && next != null; safety++) {
            FormatString[] parts = next.wrap(font, maxWidth);
            rows.add(parts[0].getFormatted());
            next = parts.length == 1 ? null : parts[1];
        }
        return rows;
    }

    @Test
    public void wrapPreservesVisibleCharsAcrossRows() {
        // Mirror what XmlPageLoader emits for the Set Pipe Direction line:
        //   "Pipes ... like an §r§n§9Iron Transport Pipe§r or a §r§n§9Wooden Transport Pipe§r with multiple ..."
        String reset = ChatFormatting.RESET.toString();
        String underline = ChatFormatting.UNDERLINE.toString();
        String blue = ChatFormatting.BLUE.toString();
        String formatted = "Pipes that have their functionality linked to what direction they're facing, like an "
            + reset + underline + blue + "Iron Transport Pipe" + reset
            + " or a "
            + reset + underline + blue + "Wooden Transport Pipe" + reset
            + " with multiple extraction sources, can have their working direction changed with a connected gate.";

        int expectedVisible = visibleLen(formatted);

        // Try various widths to exercise different break points.
        for (int maxWidth : new int[] { 100, 110, 120, 130, 140, 150 }) {
            FixedWidthFont font = new FixedWidthFont(4);  // 4px per visible char
            List<String> rows = wrapAll(formatted, font, maxWidth);

            int totalVisible = 0;
            StringBuilder reconstructed = new StringBuilder();
            for (String row : rows) {
                totalVisible += visibleLen(row);
                // Strip §-codes for content reconstruction.
                int i = 0;
                while (i < row.length()) {
                    char c = row.charAt(i);
                    if (c == '§' && i + 1 < row.length()) {
                        i += 2;
                        continue;
                    }
                    reconstructed.append(c);
                    i++;
                }
            }

            // Visible char count must be preserved.
            if (totalVisible != expectedVisible) {
                StringBuilder diag = new StringBuilder();
                diag.append("\nmaxWidth=").append(maxWidth).append('\n');
                diag.append("Expected visible chars: ").append(expectedVisible).append('\n');
                diag.append("Got visible chars: ").append(totalVisible).append('\n');
                diag.append("Rows (").append(rows.size()).append("):\n");
                for (int r = 0; r < rows.size(); r++) {
                    diag.append("  [").append(r).append("] visible=").append(visibleLen(rows.get(r)))
                        .append(" raw=\"").append(rows.get(r).replace("§", "\\u00a7")).append("\"\n");
                }
                diag.append("Reconstructed: ").append(reconstructed).append('\n');
                throw new AssertionError(diag.toString());
            }
        }
    }

    /** Pin the i==1 wrap branch directly — even if my per-page wrap test above doesn't
     *  trigger it, this confirms whether the suspected bug at FormatString.java:142 is real. */
    @Test
    public void wrapHandlesTightFitBoundaryWithoutDuplicating() {
        String reset = ChatFormatting.RESET.toString();
        String underline = ChatFormatting.UNDERLINE.toString();
        String blue = ChatFormatting.BLUE.toString();
        // Compact line that, at the right maxWidth, forces s2 (" or a ") to be the segment
        // that doesn't fit even at i=2 (because previous segments filled the row exactly).
        String formatted = reset + underline + blue + "AAA" + reset + " bb " + reset + underline + blue + "CCC";

        // Width that fits "AAA" exactly with no room for any " bb " sub-prefix.
        // "AAA" = 3 visible chars × 4 = 12px. maxWidth = 12.
        FixedWidthFont font = new FixedWidthFont(4);
        List<String> rows = wrapAll(formatted, font, 12);

        // Reconstruct visible text from rows; should equal "AAA bb CCC".
        StringBuilder reconstructed = new StringBuilder();
        for (String row : rows) {
            int i = 0;
            while (i < row.length()) {
                char c = row.charAt(i);
                if (c == '§' && i + 1 < row.length()) {
                    i += 2;
                    continue;
                }
                reconstructed.append(c);
                i++;
            }
        }

        // Diagnostic dump if the visible content doesn't match.
        String got = reconstructed.toString();
        if (!"AAA bb CCC".equals(got)) {
            StringBuilder diag = new StringBuilder("\nVisible-char loss/duplication detected\n");
            diag.append("Expected: \"AAA bb CCC\"\n");
            diag.append("Got:      \"").append(got).append("\"\n");
            diag.append("Rows (").append(rows.size()).append("):\n");
            for (int r = 0; r < rows.size(); r++) {
                diag.append("  [").append(r).append("] visible=").append(visibleLen(rows.get(r)))
                    .append(" raw=\"").append(rows.get(r).replace("§", "\\u00a7")).append("\"\n");
            }
            throw new AssertionError(diag.toString());
        }
    }
}
