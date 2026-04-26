package buildcraft.lib.client.guide.parts;

import buildcraft.api.core.render.ISprite;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.gui.GuiIcon;

/** Renders an image in the guide book. */
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
            // Center the image within the page width
            int imgX = x + (width - this.width) / 2;
            int imgY = y + current.pixel;
            GuiIcon.drawAt(sprite, imgX, imgY, this.width, this.height);
            // Picture-frame corner brackets, matching 1.12.2. The four BORDER_* icons
            // are 13×13 sprites already defined in GuiGuide; just blit them at each corner.
            GuiGuide.BORDER_TOP_LEFT.drawAt(imgX, imgY);
            GuiGuide.BORDER_TOP_RIGHT.drawAt(imgX + this.width - GuiGuide.BORDER_TOP_RIGHT.width, imgY);
            GuiGuide.BORDER_BOTTOM_LEFT.drawAt(imgX, imgY + this.height - GuiGuide.BORDER_BOTTOM_LEFT.height);
            GuiGuide.BORDER_BOTTOM_RIGHT.drawAt(
                imgX + this.width - GuiGuide.BORDER_BOTTOM_RIGHT.width,
                imgY + this.height - GuiGuide.BORDER_BOTTOM_RIGHT.height
            );
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
