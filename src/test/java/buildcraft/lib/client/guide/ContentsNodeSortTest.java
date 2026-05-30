package buildcraft.lib.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.contents.ContentsNode;
import buildcraft.lib.client.guide.parts.contents.IContentsLeaf;
import buildcraft.lib.client.guide.parts.contents.IContentsNode;
import buildcraft.lib.client.guide.parts.contents.PageLink;

/** Pins {@link ContentsNode}'s TOC sort: weight first (lower = earlier), alphabetical within
 *  equal weights, a chapter inherits its lowest member's weight, and the Alphabetical root
 *  ignores weights entirely. */
class ContentsNodeSortTest {

    /** Minimal always-visible leaf carrying a name + sort weight, so the test needs no
     *  PageLine / Minecraft client state. */
    private static final class TestLeaf implements IContentsLeaf {
        private final String name;
        private final int sort;

        TestLeaf(String name, int sort) {
            this.name = name;
            this.sort = sort;
        }

        @Override public String getSearchName() { return name; }
        @Override public int getSortIndex() { return sort; }
        @Override public boolean isVisible() { return true; }
        @Override public void resetVisibility() {}
        @Override public void setVisible(Set<PageLink> matches) {}
        @Override public GuidePart createGuidePart(GuiGuide gui) { return null; }
    }

    private static List<String> sortedNames(ContentsNode root) {
        root.sort();
        return Stream.of(root.getVisibleChildren()).map(IContentsNode::getSearchName).toList();
    }

    @Test
    void lowerWeightSortsFirst() {
        ContentsNode root = new ContentsNode("root", -1);
        root.addChild(new TestLeaf("a", 20));
        root.addChild(new TestLeaf("b", 10));
        root.addChild(new TestLeaf("c", 0));
        // Names are reverse-alphabetical, so only the weight can produce c, b, a.
        assertEquals(List.of("c", "b", "a"), sortedNames(root));
    }

    @Test
    void equalWeightFallsBackToAlphabetical() {
        ContentsNode root = new ContentsNode("root", -1);
        root.addChild(new TestLeaf("c", 5));
        root.addChild(new TestLeaf("a", 5));
        root.addChild(new TestLeaf("b", 5));
        assertEquals(List.of("a", "b", "c"), sortedNames(root));
    }

    @Test
    void defaultWeightsAreFullyAlphabetical() {
        ContentsNode root = new ContentsNode("root", -1);
        root.addChild(new TestLeaf("pipes", 0));
        root.addChild(new TestLeaf("actions", 0));
        root.addChild(new TestLeaf("blocks", 0));
        assertEquals(List.of("actions", "blocks", "pipes"), sortedNames(root));
    }

    @Test
    void chapterInheritsLowestMemberWeight() {
        ContentsNode root = new ContentsNode("root", -1);
        ContentsNode zebra = new ContentsNode("zebra", 0);
        zebra.addChild(new TestLeaf("z1", 0));
        zebra.addChild(new TestLeaf("z2", 50));
        ContentsNode apple = new ContentsNode("apple", 0);
        apple.addChild(new TestLeaf("a1", 100));
        root.addChild(zebra);
        root.addChild(apple);

        // A group floats to its lowest-weighted member: zebra (min 0) beats apple (min 100)
        // even though "apple" < "zebra" alphabetically.
        assertEquals(0, zebra.getSortIndex());
        assertEquals(100, apple.getSortIndex());
        assertEquals(List.of("zebra", "apple"), sortedNames(root));
    }

    @Test
    void alphabeticalRootIgnoresWeights() {
        ContentsNode root = new ContentsNode("root", -1, true);
        root.addChild(new TestLeaf("a", 100));
        root.addChild(new TestLeaf("b", 0));
        // With ignoreSortWeight set, the high-weighted "a" still sorts before "b" by name.
        assertEquals(List.of("a", "b"), sortedNames(root));
    }
}
