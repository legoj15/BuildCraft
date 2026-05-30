package buildcraft.lib.client.guide.loader;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.profiling.InactiveProfiler;

import buildcraft.lib.client.guide.parts.GuidePartFactory;

/** Tests that {@link XmlPageLoader} drops trailing blank-line text parts.
 *
 * <p>Every empty source line in a guide markdown file becomes a {@code GuideTextFactory(" ")}
 * that renders as an invisible full-height line. A trailing run of those advances the layout
 * cursor onto a fresh page with nothing visible on it, which surfaced in-game as a phantom blank
 * final page (and an odd page count) on the longer entries — the Logic Gates page being the one
 * that exposed it. The parser now trims that trailing run while keeping interior blank lines,
 * which are real paragraph spacing. This is a pure text path, so it needs no Minecraft
 * bootstrap (no tags → no registry/recipe-manager access). */
public class XmlPageLoaderTrailingBlankTest {

    private static List<GuidePartFactory> parse(String src) {
        try {
            return XmlPageLoader.loadParts(new BufferedReader(new StringReader(src)), InactiveProfiler.INSTANCE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void trimsTrailingBlankLines() {
        // One visible line + a trailing run of blank lines → only the visible line survives.
        assertEquals(1, parse("Hello\n\n").size(), "one trailing blank line should be trimmed");
        assertEquals(1, parse("Hello\n\n\n\n").size(), "a run of trailing blank lines should be trimmed");
        // No trailing blank → list is unchanged.
        assertEquals(1, parse("Hello").size(), "a page with no trailing blank is untouched");
    }

    @Test
    public void keepsInteriorBlankLines() {
        // A blank line BETWEEN two visible lines is paragraph spacing and must be kept:
        // "A", "" (spacer), "B" → three parts.
        assertEquals(3, parse("A\n\nB").size(), "interior blank lines are paragraph spacing, kept");
    }

    @Test
    public void allBlankCollapsesToEmpty() {
        assertTrue(parse("\n\n\n").isEmpty(), "an all-blank page collapses to no parts");
        assertTrue(parse("").isEmpty(), "an empty page has no parts");
    }
}
