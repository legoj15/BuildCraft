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
 *  "Triggers"/"Actions" chapters leaked into Sort By Mod (which should show only "BuildCraft")
 *  and Sort Alphabetically (which should be flat). */
class GuideContentsOrderTest {

    // Tag structure of the three real sort orders (titles are passed in, so no lang needed).
    private static final TypeOrder TYPE = new TypeOrder("k.type", ETypeTag.TYPE, ETypeTag.SUB_TYPE);
    private static final TypeOrder MOD = new TypeOrder("k.mod", ETypeTag.MOD, ETypeTag.TYPE);
    private static final TypeOrder ALPHA = new TypeOrder("k.alpha");

    /** A stand-in leaf. A ContentsNode is itself an IContentsNode, so it serves as a placeable
     *  leaf whose location we can assert via getChild() lookups by title. */
    private static IContentsNode leaf(String name) {
        return new ContentsNode(name, 2);
    }

    @Test
    void alphabetical_isFlat_withNoChapters() {
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode entry = leaf("MyEntry");
        GuideManager.placeExtraEntryInOrder(ALPHA, root, "BuildCraft", "Actions", null, entry);

        assertSame(entry, root.getChild("MyEntry"), "leaf sits directly under the root");
        assertNull(root.getChild("Actions"), "no category chapter in alphabetical sort");
        assertNull(root.getChild("BuildCraft"), "no mod chapter in alphabetical sort");
    }

    @Test
    void modSort_collapsesEverythingUnderOneBuildCraftChapter() {
        ContentsNode root = new ContentsNode("root", -1);
        GuideManager.placeExtraEntryInOrder(MOD, root, "BuildCraft", "Triggers", null, leaf("T1"));
        GuideManager.placeExtraEntryInOrder(MOD, root, "BuildCraft", "Actions", null, leaf("A1"));

        // Exactly one top-level node, and it is the mod chapter.
        ContentsNode bc = (ContentsNode) root.getChild("BuildCraft");
        assertNotNull(bc, "mod sort files under a single BuildCraft chapter");
        assertEquals(0, bc.indent, "BuildCraft is a top-level chapter (indent 0)");
        assertNull(root.getChild("Triggers"), "Triggers must not be top-level under Sort By Mod");
        assertNull(root.getChild("Actions"), "Actions must not be top-level under Sort By Mod");

        // Triggers/Actions become sub-headers nested inside BuildCraft.
        ContentsNode triggers = (ContentsNode) bc.getChild("Triggers");
        assertNotNull(triggers, "Triggers nests under BuildCraft");
        assertNotNull(bc.getChild("Actions"), "Actions nests under BuildCraft");
        assertEquals(1, triggers.indent, "sub-header sits at indent 1");
    }

    @Test
    void typeSort_keepsSeparateTopLevelChapters() {
        ContentsNode root = new ContentsNode("root", -1);
        GuideManager.placeExtraEntryInOrder(TYPE, root, "BuildCraft", "Triggers", null, leaf("T1"));
        GuideManager.placeExtraEntryInOrder(TYPE, root, "BuildCraft", "Actions", null, leaf("A1"));

        ContentsNode triggers = (ContentsNode) root.getChild("Triggers");
        ContentsNode actions = (ContentsNode) root.getChild("Actions");
        assertNotNull(triggers, "Triggers is its own top-level chapter under Sort By Type");
        assertNotNull(actions, "Actions is its own top-level chapter under Sort By Type");
        assertNull(root.getChild("BuildCraft"), "no mod chapter under Sort By Type");
        assertEquals(0, triggers.indent, "top-level chapter sits at indent 0");
    }

    @Test
    void typeSort_subtypeNestsUnderItsCategoryChapter() {
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode paint = leaf("Paint");
        GuideManager.placeExtraEntryInOrder(TYPE, root, "BuildCraft", "Actions", "Item Transport", paint);

        ContentsNode actions = (ContentsNode) root.getChild("Actions");
        assertNotNull(actions, "category chapter exists");
        ContentsNode sub = (ContentsNode) actions.getChild("Item Transport");
        assertNotNull(sub, "subtype nests under the category chapter");
        assertEquals(1, sub.indent, "subtype under Sort By Type sits at indent 1");
        assertSame(paint, sub.getChild("Paint"), "leaf lands under the subtype");
    }

    @Test
    void modSort_dropsSubtypeLevel_filingDirectlyUnderCategory() {
        // Sort By Mod's tags are [MOD, TYPE] — no SUB_TYPE — so a category entry's subtype is
        // dropped and it files directly under BuildCraft > Actions, just like a plain action.
        // (Mirrors how regular entries place via getOrdered(mod_type), which has no subtype.)
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode paint = leaf("Paint");
        GuideManager.placeExtraEntryInOrder(MOD, root, "BuildCraft", "Actions", "Item Transport", paint);

        ContentsNode bc = (ContentsNode) root.getChild("BuildCraft");
        assertNotNull(bc, "single BuildCraft chapter");
        ContentsNode actions = (ContentsNode) bc.getChild("Actions");
        assertNotNull(actions, "category sub-header under BuildCraft");
        assertNull(actions.getChild("Item Transport"), "no subtype level under Sort By Mod");
        assertSame(paint, actions.getChild("Paint"), "category page files directly under Actions");
    }

    @Test
    void modSort_plainActionAndCategoryActionAreSiblingsUnderActions() {
        // The reported bug: under Sort By Mod a plain action (Loop) and a pipe-item category
        // action (Paint, subtype "Item Transport") must both land directly under
        // BuildCraft > Actions as siblings — Loop must NOT appear inside an Item Transport group.
        ContentsNode root = new ContentsNode("root", -1);
        IContentsNode loop = leaf("Loop");
        IContentsNode paint = leaf("Paint");
        GuideManager.placeExtraEntryInOrder(MOD, root, "BuildCraft", "Actions", null, loop);
        GuideManager.placeExtraEntryInOrder(MOD, root, "BuildCraft", "Actions", "Item Transport", paint);

        ContentsNode bc = (ContentsNode) root.getChild("BuildCraft");
        ContentsNode actions = (ContentsNode) bc.getChild("Actions");
        assertSame(loop, actions.getChild("Loop"), "plain action sits directly under Actions");
        assertSame(paint, actions.getChild("Paint"), "category action also sits directly under Actions");
        assertNull(actions.getChild("Item Transport"), "no Item Transport sub-group in Sort By Mod");
    }
}
