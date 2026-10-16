/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import buildcraft.lib.client.guide.parts.contents.ContentsNode;
import buildcraft.lib.client.guide.parts.contents.IContentsNode;

/** Pins the per-sort-order placement of auto-iterated guide entries (Triggers/Actions and
 *  consolidated category links) done by {@link GuideManager#placeExtraEntryInOrder}. Regression
 *  guard for the bug where these were filed as a top-level chapter in <em>every</em> order, so
 *  "Triggers"/"Actions" chapters leaked into views that should not have them (Sort
 *  Alphabetically must stay flat; Sort By System must nest them under their game system). */
class GuideContentsOrderTest {

    // Tag structure of the three real sort orders (titles are passed in, so no lang needed).
    private static final TypeOrder SYSTEM = new TypeOrder("k.system", ETypeTag.SYSTEM, ETypeTag.SUB_TYPE);
    private static final TypeOrder TYPE = new TypeOrder("k.type", ETypeTag.TYPE, ETypeTag.SUB_TYPE);
    private static final TypeOrder ALPHA = new TypeOrder("k.alpha");

    private static final int LOGIC_SORT = -42;

    /** A stand-in leaf. A ContentsNode is itself an IContentsNode, so it serves as a placeable
     *  leaf whose location we can assert via getChild() lookups by title. */
    private static IContentsNode leaf(String name) {
        return new ContentsNode(name, 2);
    }

    @Test
    void alphabetical_isFlat_withNoChapters() {
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode entry = leaf("MyEntry");
        GuideManager.placeExtraEntryInOrder(ALPHA, root, "BuildCraft", "Gates and Logic", LOGIC_SORT,
            "Actions", null, entry);

        assertSame(entry, root.getChild("MyEntry"), "leaf sits directly under the root");
        assertNull(root.getChild("Actions"), "no category chapter in alphabetical sort");
        assertNull(root.getChild("Gates and Logic"), "no system chapter in alphabetical sort");
        assertNull(root.getChild("BuildCraft"), "no mod chapter in alphabetical sort");
    }

    @Test
    void systemSort_systemAndSubtype_nestsSystemThenSubtype() {
        // Pipe Signals: system "logic", subtype "wire", filed from the Triggers placement.
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode signals = leaf("Pipe Signals");
        GuideManager.placeExtraEntryInOrder(SYSTEM, root, "BuildCraft", "Gates and Logic", LOGIC_SORT,
            "Triggers", "Wire", signals);

        ContentsNode logic = (ContentsNode) root.getChild("Gates and Logic");
        assertNotNull(logic, "system is the top-level chapter");
        assertEquals(0, logic.indent, "system chapter sits at indent 0");
        ContentsNode wire = (ContentsNode) logic.getChild("Wire");
        assertNotNull(wire, "subtype nests under the system");
        assertEquals(1, wire.indent, "subtype sits at indent 1");
        assertSame(signals, wire.getChild("Pipe Signals"), "leaf lands under the subtype");
        assertNull(logic.getChild("Triggers"), "a real subtype replaces the Triggers level");
        assertNull(root.getChild("Triggers"), "Triggers never leaks to the top level");
    }

    @Test
    void systemSort_systemWithoutSubtype_groupsUnderCategoryChapter() {
        // A flat statement (system "logic", no subtype) still groups as Gates and Logic > Triggers.
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode trigger = leaf("Redstone Signal");
        IContentsNode action = leaf("Loop");
        GuideManager.placeExtraEntryInOrder(SYSTEM, root, "BuildCraft", "Gates and Logic", LOGIC_SORT,
            "Triggers", null, trigger);
        GuideManager.placeExtraEntryInOrder(SYSTEM, root, "BuildCraft", "Gates and Logic", LOGIC_SORT,
            "Actions", null, action);

        ContentsNode logic = (ContentsNode) root.getChild("Gates and Logic");
        assertNotNull(logic, "system chapter exists");
        ContentsNode triggers = (ContentsNode) logic.getChild("Triggers");
        ContentsNode actions = (ContentsNode) logic.getChild("Actions");
        assertNotNull(triggers, "Triggers nests under the system");
        assertNotNull(actions, "Actions nests under the system");
        assertEquals(1, triggers.indent, "category sub-header sits at indent 1");
        assertSame(trigger, triggers.getChild("Redstone Signal"));
        assertSame(action, actions.getChild("Loop"));
        assertNull(root.getChild("Triggers"), "Triggers must not be top-level under Sort By System");
        assertNull(root.getChild("BuildCraft"), "no mod chapter under Sort By System");
    }

    @Test
    void systemSort_noSystemNoSubtype_filesUnderCategoryAtRoot() {
        // The search-only "Other Items" chapter carries neither a system nor a subtype.
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode stone = leaf("Stone");
        GuideManager.placeExtraEntryInOrder(SYSTEM, root, "BuildCraft", null, LOGIC_SORT,
            "Other Items", null, stone);

        ContentsNode other = (ContentsNode) root.getChild("Other Items");
        assertNotNull(other, "Other Items survives as a root chapter");
        assertEquals(0, other.indent, "Other Items sits at indent 0");
        assertSame(stone, other.getChild("Stone"));
    }

    @Test
    void systemSort_systemChapterCarriesItsFixedSortIndex() {
        ContentsNode root = new ContentsNode("root", -1);
        GuideManager.placeExtraEntryInOrder(SYSTEM, root, "BuildCraft", "Gates and Logic", LOGIC_SORT,
            "Triggers", null, leaf("Redstone Signal"));
        GuideManager.placeExtraEntryInOrder(SYSTEM, root, "BuildCraft", null, LOGIC_SORT,
            "Other Items", null, leaf("Stone"));

        assertEquals(LOGIC_SORT, root.getChild("Gates and Logic").getSortIndex(),
            "the system chapter takes the fixed index, not its children's minimum");
        assertEquals(0, root.getChild("Other Items").getSortIndex(),
            "a non-system chapter keeps the min-of-children weight");
    }

    @Test
    void typeSort_keepsSeparateTopLevelChapters() {
        ContentsNode root = new ContentsNode("root", -1);
        GuideManager.placeExtraEntryInOrder(TYPE, root, "BuildCraft", "Gates and Logic", LOGIC_SORT,
            "Triggers", null, leaf("T1"));
        GuideManager.placeExtraEntryInOrder(TYPE, root, "BuildCraft", "Gates and Logic", LOGIC_SORT,
            "Actions", null, leaf("A1"));

        ContentsNode triggers = (ContentsNode) root.getChild("Triggers");
        ContentsNode actions = (ContentsNode) root.getChild("Actions");
        assertNotNull(triggers, "Triggers is its own top-level chapter under Sort By Type");
        assertNotNull(actions, "Actions is its own top-level chapter under Sort By Type");
        assertNull(root.getChild("BuildCraft"), "no mod chapter under Sort By Type");
        assertNull(root.getChild("Gates and Logic"), "no system chapter under Sort By Type");
        assertEquals(0, triggers.indent, "top-level chapter sits at indent 0");
    }

    @Test
    void typeSort_subtypeNestsUnderItsCategoryChapter() {
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode paint = leaf("Paint");
        GuideManager.placeExtraEntryInOrder(TYPE, root, "BuildCraft", "Transport", 0,
            "Actions", "Item Transport", paint);

        ContentsNode actions = (ContentsNode) root.getChild("Actions");
        assertNotNull(actions, "category chapter exists");
        ContentsNode sub = (ContentsNode) actions.getChild("Item Transport");
        assertNotNull(sub, "subtype nests under the category chapter");
        assertEquals(1, sub.indent, "subtype under Sort By Type sits at indent 1");
        assertSame(paint, sub.getChild("Paint"), "leaf lands under the subtype");
    }

    @Test
    void typeSort_plainActionSitsDirectlyUnderActions() {
        // Under Sort By Type a statement without a subtype stays a direct child of its category
        // chapter; the system-order fallback (subtype := category) must not leak into this view.
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode loop = leaf("Loop");
        GuideManager.placeExtraEntryInOrder(TYPE, root, "BuildCraft", "Gates and Logic", LOGIC_SORT,
            "Actions", null, loop);

        ContentsNode actions = (ContentsNode) root.getChild("Actions");
        assertSame(loop, actions.getChild("Loop"), "plain action sits directly under Actions");
        assertNull(actions.getChild("Actions"), "no doubled Actions > Actions level");
    }
}
