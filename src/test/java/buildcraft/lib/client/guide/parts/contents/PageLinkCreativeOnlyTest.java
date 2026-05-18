package buildcraft.lib.client.guide.parts.contents;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.parts.GuidePageFactory;

/** Tests the {@code creative_only} visibility gate on {@link PageLink}.
 *
 *  The gate exists so guide entries documenting items with no survival recipe
 *  (creative engine, debugger, volume box) stay hidden from survival players
 *  but remain visible to anyone who can spawn them (creative, OP, host-with-cheats).
 *
 *  In a unit-test JVM {@code Minecraft.getInstance()} returns null because the
 *  client never bootstraps, so {@link PageLink#canAccessCreativeOnlyContent()}
 *  short-circuits to {@code false}. That's exactly the "no permissions" branch
 *  we want to assert against — a creative-only entry stays invisible no matter
 *  what {@code startVisible} or the search-match set says. */
public class PageLinkCreativeOnlyTest {

    private static PageLink make(boolean startVisible, boolean creativeOnly) {
        return new TestPageLink(new PageLine(0, "test", false), startVisible, creativeOnly);
    }

    @Test
    public void canAccessCreativeOnlyContent_returnsFalseWithoutClient() {
        // No Minecraft client in this JVM → the gate denies access. The whole
        // feature rides on this being false for non-cheating players, so it's
        // worth pinning down as its own assertion.
        assertFalse(PageLink.canAccessCreativeOnlyContent());
    }

    @Test
    public void nonCreativeOnlyEntry_resetVisibilityRestoresStartVisible() {
        PageLink visibleLink = make(true, false);
        visibleLink.resetVisibility();
        assertTrue(visibleLink.isVisible(),
            "non-creative entry with startVisible=true should be visible after reset");

        PageLink hiddenLink = make(false, false);
        hiddenLink.resetVisibility();
        assertFalse(hiddenLink.isVisible(),
            "non-creative entry with startVisible=false should stay hidden after reset");
    }

    @Test
    public void creativeOnlyEntry_isHiddenAfterResetEvenIfStartVisible() {
        PageLink link = make(true, true);
        link.resetVisibility();
        assertFalse(link.isVisible(),
            "creative-only entry should be hidden after reset when the gate denies access, "
                + "regardless of startVisible");
    }

    @Test
    public void creativeOnlyEntry_isHiddenFromSearchEvenIfMatched() {
        PageLink link = make(true, true);
        // Simulate a search hit that includes this entry — without the gate, the
        // entry would become visible. With the gate, it stays hidden because the
        // user can't obtain the item anyway.
        link.setVisible(Set.of(link));
        assertFalse(link.isVisible(),
            "creative-only entry matched by search should still be hidden when gate denies access");
    }

    @Test
    public void nonCreativeOnlyEntry_visibilityFollowsSearchMatchSet() {
        PageLink link = make(true, false);
        link.setVisible(Set.of(link));
        assertTrue(link.isVisible(), "non-creative entry in match set should be visible");

        link.setVisible(Collections.emptySet());
        assertFalse(link.isVisible(), "non-creative entry not in match set should be hidden");
    }

    @Test
    public void creativeOnlyFlag_isExposedAsPublicField() {
        // Public field doubles as the persistence-from-JSON sink (PageEntry → here)
        // and the dev-facing way to know the gate applies. Pin it so the API
        // doesn't drift back to a private field.
        assertTrue(make(true, true).creativeOnly);
        assertFalse(make(true, false).creativeOnly);
    }

    /** Minimal concrete {@link PageLink} for testing the abstract base. */
    private static final class TestPageLink extends PageLink {
        TestPageLink(PageLine text, boolean startVisible, boolean creativeOnly) {
            super(text, startVisible, creativeOnly);
        }

        @Override
        public GuidePageFactory getFactoryLink() {
            return null;
        }
    }
}
