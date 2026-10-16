/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

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
 *  equal weights, a chapter inherits its lowest member's weight unless it carries an explicit
 *  index (the Sort By System chapters, which follow a fixed curriculum order), and the
 *  Alphabetical root ignores weights entirely. */
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

    @Test
    void explicitSortIndexWinsOverChildrenMinimum() {
        ContentsNode chapter = new ContentsNode("chapter", 0);
        chapter.addChild(new TestLeaf("x", 500));
        assertEquals(500, chapter.getSortIndex(), "without an explicit index the chapter takes its min");
        chapter.setSortIndex(-7);
        chapter.addChild(new TestLeaf("y", -100));
        assertEquals(-7, chapter.getSortIndex(), "an explicit index ignores every child weight");
    }

    private static ContentsNode systemChapter(String rawSystem, String title) {
        ContentsNode node = new ContentsNode(title, 0);
        node.setSortIndex(GuideManager.systemChapterSortIndex(ETypeTag.SYSTEM.preText + rawSystem));
        node.addChild(new TestLeaf(title + " entry", 0));
        return node;
    }

    @Test
    void systemChaptersFollowTheFixedListThenUnknownsAlphabetically() {
        ContentsNode root = new ContentsNode("root", -1);
        // Added in scrambled order, with titles whose A-Z order disagrees with the fixed list.
        root.addChild(systemChapter("zzz_addon", "Zzz Addon"));
        root.addChild(systemChapter("robots", "Robots"));
        root.addChild(systemChapter("transport", "Transport"));
        root.addChild(systemChapter("aaa_addon", "Aaa Addon"));
        root.addChild(systemChapter("getting_started", "Getting Started"));
        root.addChild(systemChapter("power", "Power"));
        root.addChild(systemChapter("logic", "Gates and Logic"));
        root.addChild(systemChapter("building", "Building"));
        root.addChild(systemChapter("mining_fluids", "Mining and Fluids"));
        root.addChild(systemChapter("silicon", "Silicon"));
        // A non-system root chapter (the search-only Other Items) keeps its member weight.
        ContentsNode other = new ContentsNode("Other Items", 0);
        other.addChild(new TestLeaf("Stone", 0));
        root.addChild(other);

        assertEquals(List.of(
            "Getting Started", "Power", "Transport", "Gates and Logic", "Building", "Mining and Fluids",
            "Silicon", "Robots", "Aaa Addon", "Zzz Addon", "Other Items"), sortedNames(root));
    }
}
