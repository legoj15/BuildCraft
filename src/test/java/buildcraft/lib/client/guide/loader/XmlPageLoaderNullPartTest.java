package buildcraft.lib.client.guide.loader;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.google.common.collect.ImmutableList;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.GuidePart.PagePosition;
import buildcraft.lib.client.guide.parts.GuidePartFactory;

/** Guards the fix for GH-27: clicking a guide contents entry crashed with a
 * {@code NullPointerException at index N} inside {@code ImmutableList.copyOf}.
 *
 * <p>A {@link GuidePartFactory} legitimately returns {@code null} at render time when it can't
 * resolve — a deferred {@code <link>} to a page that turned out not to exist is the common case.
 * {@link XmlPageLoader}'s page-materialising loop used to add that null straight into the part
 * list, which the {@link buildcraft.lib.client.guide.parts.GuidePage} constructor then fed to
 * {@link ImmutableList#copyOf}, throwing on the first null element. {@link
 * XmlPageLoader#instantiateParts} now skips null results, matching the null-guarding already
 * done elsewhere. This is a pure list path, so it needs no Minecraft bootstrap. */
public class XmlPageLoaderNullPartTest {

    /** A minimal render-less GuidePart double. GuidePart's constructor only stores the gui
     *  reference (never dereferences it), so a null gui is fine for this list-shaping test. */
    private static GuidePart dummyPart() {
        return new GuidePart((GuiGuide) null) {
            @Override
            public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
                return current;
            }

            @Override
            public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
                int mouseX, int mouseY) {
                return current;
            }
        };
    }

    @Test
    public void skipsFactoriesThatReturnNull() {
        GuidePart a = dummyPart();
        GuidePart b = dummyPart();
        List<GuidePartFactory> factories = List.of(
            gui -> a,
            gui -> null, // e.g. a deferred <link> to a page that doesn't exist
            gui -> b
        );

        List<GuidePart> parts = XmlPageLoader.instantiateParts(factories, null);

        assertEquals(2, parts.size(), "the null factory result should be skipped, not added");
        // The list must be null-free — the crash was ImmutableList.copyOf choking on a null.
        assertDoesNotThrow(() -> ImmutableList.copyOf(parts), "materialised parts must contain no nulls");
    }

    @Test
    public void keepsAllNonNullParts() {
        GuidePart a = dummyPart();
        GuidePart b = dummyPart();
        List<GuidePartFactory> factories = List.of(gui -> a, gui -> b);
        assertEquals(2, XmlPageLoader.instantiateParts(factories, null).size());
    }
}
