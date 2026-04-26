/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.sprite.SpriteNineSliced;
import buildcraft.lib.gui.pos.GuiRectangle;

public abstract class GuideChapter extends GuidePart {
    public static final int[] COLOURS = { 0x9dd5c0, 0xfac174, 0x27a4dd };
    public static final int MAX_HOWEVER_PROGRESS = 5;
    public static final int MAX_HOVER_DISTANCE = 20;

    private static final boolean FOLLOW_SIDE = false;

    public final PageLine chapter;
    public final int level;

    @Nullable
    protected GuideChapter parent;
    protected final List<GuideChapter> children = new ArrayList<>();
    protected int colourIndex = -1;
    protected EnumGuiSide lastDrawn = null;

    private int hoverProgress = 0, hoverProgressLast = 0;
    private boolean expanded = false;

    public enum EnumGuiSide { LEFT, RIGHT }

    public GuideChapter(GuiGuide gui, String chapter) {
        this(gui, 0, chapter);
    }

    public GuideChapter(GuiGuide gui, int level, String text) {
        super(gui);
        this.level = Math.max(0, level);
        this.chapter = new PageLine(null, null, this.level + 1, text, false);
    }

    private int getColour() {
        if (colourIndex < 0) {
            return chapter.text.hashCode();
        }
        return COLOURS[colourIndex % COLOURS.length];
    }

    /** Convert a 0xRRGGBB colour into 0xAARRGGBB with full opacity for GuiGraphics tinting. */
    private static int asARGB(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    public void reset() {
        lastDrawn = FOLLOW_SIDE ? EnumGuiSide.RIGHT : EnumGuiSide.LEFT;
    }

    public boolean hasParent() { return parent != null; }
    public boolean hasChildren() { return !children.isEmpty(); }

    void assignChildIndices() {
        int cIdx = 0;
        for (GuideChapter child : children) {
            if (cIdx % COLOURS.length == colourIndex) cIdx++;
            child.colourIndex = cIdx++ % COLOURS.length;
            if (child.hasChildren()) child.assignChildIndices();
        }
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        if (getFontRenderer() != null) {
            current = current.guaranteeSpace(getFontRenderer().getMaxFontHeight() * 4, height);
        }

        int colour = getColour();

        if (current.page == index) {
            int _x = x + 12;
            int _y = y + current.pixel;
            PagePosition n2 = renderLine(current, chapter, x, y, width, height, -1);
            int _height = n2.pixel - current.pixel;
            GuiGuide.CHAPTER_MARKER_9.drawTinted(_x - 5, _y - 4, width - 24, _height, asARGB(colour));
        }

        PagePosition n = renderLine(current, chapter, x, y, width, height, index);
        int halfIndex = index / 2;
        if (n.page / 2 < halfIndex) {
            lastDrawn = EnumGuiSide.LEFT;
        } else if (halfIndex == current.page / 2) {
            lastDrawn = FOLLOW_SIDE ? null : EnumGuiSide.LEFT;
        }
        if (lastDrawn != null && parent != null) {
            GuideChapter p = parent;
            while (p != null) {
                if (p.lastDrawn == null) {
                    p.lastDrawn = lastDrawn;
                }
                p = p.parent;
            }
        }
        return n;
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        if (getFontRenderer() != null) {
            current = current.guaranteeSpace(getFontRenderer().getMaxFontHeight() * 4, height);
        }
        return renderLine(current, chapter, x, y, width, height, -1);
    }

    /** @return The additional number of chapter segments drawn. */
    public int draw(int yIndex, float partialTicks, boolean drawCentral) {
        int drawnCount = 1;

        IFontRenderer font = gui.getCurrentFont();
        if (font == null) return drawnCount;

        String text = chapter.text;
        float hoverWidth = getHoverWidth(partialTicks);
        int colour = getColour();
        int argb = asARGB(colour);

        int baseY = drawCentral ? ((int) GuiGuide.FLOATING_CHAPTER_MENU.getY() + 6) : gui.minY;
        int y = baseY + (font.getMaxFontHeight() + 8) * (yIndex + 1);
        boolean hasChildren = !children.isEmpty();
        // `width` is the text+hover footprint used for positioning the LEFT-side
        // tab so its right edge aligns with the book spine. `_width` is the wider
        // sprite-render footprint that adds 12px of internal padding plus a 16px
        // arrow gutter for parent chapters. In 1.12.2 these were separate; the
        // 26.1 port collapsed them into just `_width`, causing LEFT-side tabs to
        // visually drift ~12-28px further left than their click hitbox.
        float width = font.getStringWidth(text) + hoverWidth;
        float _width = font.getStringWidth(text) + 12 + hoverWidth + (hasChildren ? 16 : 0);
        int fullHeight = font.getFontHeight(text) + 6;
        int childHeight = 0;

        if (hasChildren && expanded) {
            childHeight = fullHeight + getChildrenFullHeight();
        }

        if (drawCentral || lastDrawn == EnumGuiSide.RIGHT) {
            float x;
            if (drawCentral) {
                x = (float) GuiGuide.FLOATING_CHAPTER_MENU.getX() + 4 + hoverWidth;
                _width -= hoverWidth;
                hoverWidth = 0;
            } else {
                x = gui.minX + GuiGuide.PAGE_LEFT.width + GuiGuide.PAGE_RIGHT.width - 11;
            }
            x += level * 14;
            GuideChapter p = parent;
            while (p != null) {
                x += p.getHoverWidth(partialTicks);
                p = p.parent;
            }

            SpriteNineSliced icon = drawCentral ? GuiGuide.CHAPTER_MARKER_9 : GuiGuide.CHAPTER_MARKER_9_RIGHT;
            if (childHeight > 0) {
                icon.drawTinted(x + 10, y + fullHeight - 12, _width - 16, childHeight, argb);
            }
            icon.drawTinted(x, y - 4, _width, fullHeight, argb);
            if (hasChildren) {
                (expanded ? GuiGuide.EXPANDED_ARROW : GuiGuide.CLOSED_ARROW).drawAt(x + hoverWidth, y - 4);
                x += 16;

                if (expanded) {
                    for (GuideChapter child : children) {
                        EnumGuiSide old = child.lastDrawn;
                        child.lastDrawn = lastDrawn;
                        drawnCount += child.draw(yIndex + drawnCount, partialTicks, drawCentral);
                        child.lastDrawn = old;
                    }
                }
            }

            font.drawString(text, (int) (x + 6 + hoverWidth), y, 0xFF000000);
        } else if (lastDrawn == EnumGuiSide.LEFT) {
            float x = gui.minX - width + 5;
            if (hasChildren) {
                x -= 16;
            }

            if (childHeight > 0) {
                GuiGuide.CHAPTER_MARKER_9_LEFT.drawTinted(x + 10, y + fullHeight - 12, _width - 16, childHeight, argb);
            }
            GuiGuide.CHAPTER_MARKER_9_LEFT.drawTinted(x - 6, y - 4, _width, fullHeight, argb);

            if (hasChildren) {
                (expanded ? GuiGuide.EXPANDED_ARROW : GuiGuide.CLOSED_ARROW).drawAt(x - 6, y - 4);
                x += 16;

                if (expanded) {
                    for (GuideChapter child : children) {
                        EnumGuiSide old = child.lastDrawn;
                        child.lastDrawn = lastDrawn;
                        drawnCount += child.draw(yIndex + drawnCount, partialTicks, drawCentral);
                        child.lastDrawn = old;
                    }
                }
            }

            font.drawString(text, (int) x, y, 0xFF000000);
        }
        return drawnCount;
    }

    private int getChildrenFullHeight() {
        if (!expanded) return 0;
        int fullHeight = 0;
        IFontRenderer font = gui.getCurrentFont();
        if (font == null) return 0;
        for (GuideChapter c : children) {
            fullHeight += font.getFontHeight(c.chapter.text) + 6;
            fullHeight += c.getChildrenFullHeight();
            fullHeight += 2;
        }
        return fullHeight;
    }

    /** @return 0 for not hovered, 1 for the main chapter, or 2 for the arrow (if present). */
    protected int getMousePart() {
        IFontRenderer font = gui.getCurrentFont();
        if (font == null) return 0;
        String text = chapter.text;
        float hoverWidth = getHoverWidth(0);
        final float realHoverWidth = hoverWidth;
        int width = (int) (font.getStringWidth(text) + hoverWidth) + (children.isEmpty() ? 0 : 16);

        int chapterIndex = 0;

        GuideChapter p = parent;
        while (p != null) {
            if (!p.expanded) return 0;
            p = p.parent;
        }

        for (GuideChapter c : gui.getChapters()) {
            chapterIndex++;
            if (c == this) break;
            p = c.parent;
            while (p != null) {
                if (!p.expanded) {
                    chapterIndex--;
                    break;
                }
                p = p.parent;
            }
        }

        boolean isCentral = gui.isSmallScreen();
        int baseY = isCentral ? ((int) GuiGuide.FLOATING_CHAPTER_MENU.getY() + 6) : gui.minY;
        int y = baseY + (font.getMaxFontHeight() + 8) * chapterIndex;

        if (isCentral || lastDrawn == EnumGuiSide.RIGHT) {
            int x;
            if (isCentral) {
                x = (int) GuiGuide.FLOATING_CHAPTER_MENU.getX() + 4 + (int) hoverWidth;
                width -= hoverWidth;
                hoverWidth = 0;
            } else {
                x = gui.minX + GuiGuide.PAGE_LEFT.width + GuiGuide.PAGE_RIGHT.width - 11;
            }
            x += level * 14;
            p = parent;
            while (p != null) {
                x += p.getHoverWidth(0);
                p = p.parent;
            }

            GuiRectangle drawRect = new GuiRectangle(x - realHoverWidth, y - 4, width + 16, 16);
            if (drawRect.contains(gui.mouse)) {
                GuiRectangle arrowRect = new GuiRectangle(x - realHoverWidth, y - 4, 24 + realHoverWidth, 16);
                if (hasChildren() && arrowRect.contains(gui.mouse)) {
                    return 2;
                }
                return 1;
            }
        } else if (lastDrawn == EnumGuiSide.LEFT) {
            int x = gui.minX - width - 5;
            GuiRectangle drawRect = new GuiRectangle(x, y - 4, width + 16, 16);
            if (drawRect.contains(gui.mouse)) {
                if (hasChildren() && new GuiRectangle(x, y - 4, 24, 16).contains(gui.mouse)) {
                    return 2;
                }
                return 1;
            }
        }
        return 0;
    }

    private float getHoverWidth(float partialTicks) {
        float prog = partialTicks * hoverProgress + (1 - partialTicks) * hoverProgressLast;
        return (prog * MAX_HOVER_DISTANCE) / MAX_HOWEVER_PROGRESS;
    }

    public int handleClick() {
        int part = getMousePart();
        if (part == 1) {
            return onClick() ? 1 : 0;
        } else if (part == 2) {
            expanded = !expanded;
            return 2;
        }
        return 0;
    }

    protected abstract boolean onClick();

    @Override
    public void updateScreen() {
        hoverProgressLast = hoverProgress;
        if (getMousePart() != 0) {
            hoverProgress += 2;
            if (hoverProgress > MAX_HOWEVER_PROGRESS) {
                hoverProgress = MAX_HOWEVER_PROGRESS;
            }
        } else {
            hoverProgress--;
            if (hoverProgress < 0) {
                hoverProgress = 0;
            }
        }
    }
}
