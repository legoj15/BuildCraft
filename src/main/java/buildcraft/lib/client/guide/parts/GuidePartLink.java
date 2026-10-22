package buildcraft.lib.client.guide.parts;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.contents.PageLink;

public class GuidePartLink extends GuidePart {

    public final PageLink link;

    public GuidePartLink(GuiGuide gui, PageLink link) {
        super(gui);
        this.link = link;
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        return renderLine(current, link.text, x, y, width, height, index);
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        // The hit test only answers for segments drawn on the clicked page, so the END position is not consulted: a
        // link that wraps over a page break is clickable on both of its pages.
        PagePosition pos = hitTestLine(current, link.text, x, y, width, height, index);
        if (wasHovered()) {
            GuidePageFactory factory = link.getFactoryLink();
            if (factory != null) {
                gui.openPage(factory.createNew(gui));
            }
        }
        return pos;
    }
}
