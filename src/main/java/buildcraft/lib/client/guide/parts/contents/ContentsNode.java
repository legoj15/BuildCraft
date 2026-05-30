package buildcraft.lib.client.guide.parts.contents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.parts.GuideChapterWithin;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.GuideText;

public class ContentsNode implements IContentsNode {

    public final String title;
    public final int indent;
    /** When true this node sorts its children purely alphabetically, ignoring TOC sort
     *  weights. Set on the Alphabetical order's root so that view stays a strict A–Z list
     *  even when entries carry weights that reorder the Type / Mod views. */
    private final boolean ignoreSortWeight;
    private final Map<String, IContentsNode> nodes = new HashMap<>();
    private IContentsNode[] sortedNodes = new IContentsNode[0];
    IContentsNode[] visibleNodes = new IContentsNode[0];
    private boolean needsSorting = false;

    public ContentsNode(String title, int indent) {
        this(title, indent, false);
    }

    public ContentsNode(String title, int indent, boolean ignoreSortWeight) {
        this.title = title;
        this.indent = indent;
        this.ignoreSortWeight = ignoreSortWeight;
    }

    @Override
    public String getSearchName() {
        return title;
    }

    @Override
    public int getSortIndex() {
        // A chapter inherits the lowest weight among its members, so it floats to where its
        // earliest-weighted entry would sit. Reads the raw child map (fully populated before
        // sort() runs), so it is independent of sort/visibility state. Empty -> neutral 0.
        int min = Integer.MAX_VALUE;
        for (IContentsNode child : nodes.values()) {
            min = Math.min(min, child.getSortIndex());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    @Override
    public GuidePart createGuidePart(GuiGuide gui) {
        if (indent == 0) {
            return new GuideChapterWithin(gui, ChatFormatting.UNDERLINE + title);
        } else {
            return new GuideText(gui, new PageLine(indent + 1, ChatFormatting.UNDERLINE + title, false));
        }
    }

    @Nullable
    public IContentsNode getChild(String childKey) {
        return nodes.get(childKey);
    }

    @Override
    public void addChild(IContentsNode node) {
        nodes.put(node.getSearchName(), node);
        needsSorting = true;
    }

    @Override
    public IContentsNode[] getVisibleChildren() {
        return visibleNodes;
    }

    @Override
    public boolean isVisible() {
        return visibleNodes.length != 0;
    }

    @Override
    public void sort() {
        if (!needsSorting) return;
        needsSorting = false;
        sortedNodes = nodes.values().toArray(new IContentsNode[0]);
        // Sort by TOC weight first (lower = earlier), then alphabetically within equal weights.
        // With all weights at the 0 default this is exactly the historical alphabetical order.
        // The Alphabetical order's root ignores weights so it stays a strict A–Z list.
        Comparator<IContentsNode> byName =
            Comparator.comparing(IContentsNode::getSearchName, String.CASE_INSENSITIVE_ORDER);
        Arrays.sort(sortedNodes, ignoreSortWeight
            ? byName
            : Comparator.comparingInt(IContentsNode::getSortIndex).thenComparing(byName));
        for (IContentsNode node : sortedNodes) {
            node.sort();
        }
        calcVisibility();
    }

    @Override
    public void calcVisibility() {
        List<IContentsNode> visible = new ArrayList<>();
        for (IContentsNode node : sortedNodes) {
            if (node.isVisible()) {
                visible.add(node);
            }
        }
        visibleNodes = visible.toArray(new IContentsNode[0]);
    }

    @Override
    public void resetVisibility() {
        for (IContentsNode node : sortedNodes) {
            node.resetVisibility();
        }
        calcVisibility();
    }

    @Override
    public void setVisible(Set<PageLink> matches) {
        for (IContentsNode node : sortedNodes) {
            node.setVisible(matches);
        }
        calcVisibility();
    }
}
