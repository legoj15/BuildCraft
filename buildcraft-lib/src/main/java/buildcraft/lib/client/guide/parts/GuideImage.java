package buildcraft.lib.client.guide.parts;

import buildcraft.api.core.render.ISprite;

import buildcraft.lib.client.guide.GuiGuide;

/** Renders an image in the guide book.
 * Stubbed — needs GuiSpriteScaled and GuiGuide.BORDER_* constants for proper rendering. */
public class GuideImage extends GuidePart {
    public static final int PIXEL_HEIGHT = 42;
    final ISprite sprite;
    final int width, height;

    public GuideImage(GuiGuide gui, ISprite sprite, int srcWidth, int srcHeight, int width, int height) {
        super(gui);
        this.sprite = sprite;
        this.width = width <= 0 ? srcWidth : width;
        this.height = height <= 0 ? srcHeight : height;
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        if (height - current.pixel < this.height) {
            current = current.nextPage();
        }
        if (index == current.page) {
            // Rendering stubbed — needs GuiSpriteScaled and GuiGuide border constants
        }
        return current.nextLine(this.height + 1, height);
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        if (height - current.pixel < this.height) {
            current = current.nextPage();
        }
        return current.nextLine(this.height + 1, height);
    }
}
