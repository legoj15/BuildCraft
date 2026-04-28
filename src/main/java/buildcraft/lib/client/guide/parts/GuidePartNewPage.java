package buildcraft.lib.client.guide.parts;

import buildcraft.lib.client.guide.GuiGuide;

public class GuidePartNewPage extends GuidePart {
    /** Minimum cursor-pixel offset required for the break to fire. When the cursor
     *  is closer to the top of the current page than this value, the break is skipped
     *  — the previous content already pushed the cursor onto a fresh-or-nearly-fresh
     *  page, and forcing another advance would waste it as a blank.
     *
     *  <p>Use 0 (the no-arg constructor) for the unconditional behaviour expected by
     *  the {@code <new_page/>} markdown tag — author explicitly asked for the break.
     *  Use a small positive value (~30 px ≈ a chapter heading's worth) for
     *  programmatic "breathing room" breaks like the ones inserted before recipe
     *  and group sections, where the break is a layout suggestion rather than a
     *  hard requirement. */
    private final int minPixelThreshold;

    public GuidePartNewPage(GuiGuide gui) {
        this(gui, 0);
    }

    public GuidePartNewPage(GuiGuide gui, int minPixelThreshold) {
        super(gui);
        this.minPixelThreshold = minPixelThreshold;
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        if (current.pixel < minPixelThreshold) {
            return current;
        }
        return current.newPage();
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        if (current.pixel < minPixelThreshold) {
            return current;
        }
        return current.newPage();
    }
}
