package buildcraft.lib.client.guide.parts.contents;

import java.util.Set;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePart;

public interface IContentsNode {
    String getSearchName();

    /** TOC sort weight (lower sorts earlier; ties fall back to alphabetical by search name).
     *  Leaves return their own weight; a {@link ContentsNode} returns the lowest weight among
     *  its members, so a chapter floats to where its earliest-weighted entry would sit. */
    int getSortIndex();

    boolean isVisible();
    void calcVisibility();
    void resetVisibility();
    void setVisible(Set<PageLink> matches);
    void sort();
    IContentsNode[] getVisibleChildren();
    void addChild(IContentsNode node);
    GuidePart createGuidePart(GuiGuide gui);
}
