/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.help.ElementHelpInfo.HelpPosition;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.misc.LocaleUtil;

/** Ledger that shows contextual help text for the GUI.
 *  When open, highlights all GUI elements that provide help info (tanks, slots, etc.)
 *  with colored overlays. Hovering over a highlighted element updates the ledger text.
 *  Positioned on the LEFT side, matching 1.12.2 behavior. */
public class LedgerHelp extends Ledger_Neptune {
    private static final Identifier ICON_HELP = Identifier.parse("buildcraftlib:textures/icons/help.png");

    /** Border thickness for the highlight overlays (in pixels). */
    private static final int BORDER = 2;

    private IGuiElement selected = null;
    private boolean foundAny = false;
    private boolean init = false;

    /** Currently displayed help info (for tracking when to recalculate text) */
    private ElementHelpInfo currentHelpInfo = null;

    public LedgerHelp(BuildCraftGui gui, boolean expandPositive) {
        // expandPositive=false → opens to the LEFT side (standard for help)
        super(gui, 0xFF_CC_99_FF, expandPositive);
        this.title = "gui.ledger.help";
        calculateMaxSize();
    }

    @Override
    public void tick() {
        super.tick();
        if (currentWidth == CLOSED_WIDTH && currentHeight == CLOSED_HEIGHT) {
            selected = null;
            currentHelpInfo = null;
        }
    }

    @Override
    protected void drawIcon(double x, double y, GuiGraphicsExtractor graphics) {
        if (!init) {
            init = true;
            List<HelpPosition> elements = new ArrayList<>();
            for (IGuiElement element : gui.shownElements) {
                element.addHelpElements(elements);
            }
            foundAny = !elements.isEmpty();
        }
        // Draw the help icon
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_HELP,
            (int) x, (int) y, 0f, 0f, 16, 16, 16, 16);
    }

    @Override
    public void drawBackground(float partialTicks) {
        // Draw the ledger panel itself (background, icon, text)
        super.drawBackground(partialTicks);

        // Draw the interactive overlays on top of the GUI when the ledger is open
        if (!shouldDrawOpen()) {
            return;
        }

        GuiGraphicsExtractor graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        boolean set = false;
        List<HelpPosition> elements = new ArrayList<>();
        for (IGuiElement element : gui.shownElements) {
            element.addHelpElements(elements);
            foundAny |= !elements.isEmpty();
            for (HelpPosition info : elements) {
                IGuiArea rect = info.target;
                boolean isHovered = rect.contains(gui.mouse);
                if (isHovered && !set) {
                    if (selected != element) {
                        selected = element;
                        // Update ledger text to show the hovered element's help info
                        updateHelpText(info.info);
                    }
                    set = true;
                }
                boolean isSelected = selected == element;
                // Draw colored border overlay around the target area
                drawHighlightBorder(graphics, rect, info.info.colour, isHovered, isSelected);
            }
            elements.clear();
        }
    }

    /** Draw a colored border rectangle around the given area.
     *  Matches the visual effect of 1.12.2's help_split.png 9-slice overlay. */
    private void drawHighlightBorder(GuiGraphicsExtractor graphics, IGuiArea rect, int colour,
                                      boolean isHovered, boolean isSelected) {
        int x = (int) rect.getX();
        int y = (int) rect.getY();
        int w = (int) rect.getWidth();
        int h = (int) rect.getHeight();

        // Adjust alpha based on state:
        //  - Normal/unselected: lighter (more transparent)
        //  - Hovered or selected: more visible
        int alpha;
        if (isHovered && isSelected) {
            alpha = 0xDD;
        } else if (isHovered || isSelected) {
            alpha = 0xBB;
        } else {
            alpha = 0x88;
        }

        // Build the ARGB colour with the adjusted alpha
        int borderColour = (alpha << 24) | (colour & 0x00FFFFFF);

        // Draw 4 border rectangles (top, bottom, left, right)
        // Expand outward by BORDER pixels to frame the element
        int bx = x - BORDER;
        int by = y - BORDER;
        int bw = w + BORDER * 2;
        int bh = h + BORDER * 2;

        // Top border
        graphics.fill(bx, by, bx + bw, by + BORDER, borderColour);
        // Bottom border
        graphics.fill(bx, y + h, bx + bw, y + h + BORDER, borderColour);
        // Left border
        graphics.fill(bx, by + BORDER, bx + BORDER, y + h, borderColour);
        // Right border
        graphics.fill(x + w, by + BORDER, x + w + BORDER, y + h, borderColour);

        // Draw a lighter fill inside for hovered/selected states
        if (isHovered || isSelected) {
            int fillAlpha = isHovered ? 0x33 : 0x22;
            int fillColour = (fillAlpha << 24) | (colour & 0x00FFFFFF);
            graphics.fill(x, y, x + w, y + h, fillColour);
        }
    }

    /** Update the ledger's text content to reflect the given help info. */
    private void updateHelpText(ElementHelpInfo info) {
        if (info == currentHelpInfo) return;
        currentHelpInfo = info;

        // Clear existing text and add the help info's text
        clearTextEntries();

        // Add translated title as a colored header line
        String localizedTitle = LocaleUtil.localize(info.title);
        appendText(localizedTitle, info.colour & 0x00FFFFFF).setDropShadow(true);

        // Add each locale key's text
        for (String key : info.localeKeys) {
            if (key == null) continue;
            String text;
            if (info.isPreTranslated) {
                text = key;
            } else {
                text = LocaleUtil.localize(key);
            }
            if (!text.isEmpty()) {
                appendText(text, 0xFFFFFF);
            }
        }

        calculateMaxSize();
    }
}
