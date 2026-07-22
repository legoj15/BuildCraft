/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.ArrayList;
import java.util.List;

import buildcraft.lib.gui.BCGraphics;
//? if >=1.21.10 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
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
 *  Positioned on the LEFT side, matching 1.12.2 behavior.
 *
 *  <p>When the parent screen registers NO help entries at all the ledger switches to a warning
 *  presentation (see {@link #foundAny}). 1.12.2 did this by swapping the icon sprite for
 *  {@code BCLibSprites.WARNING_MINOR}; that texture was never ported, so the warning is expressed
 *  in code instead — an amber badge behind the icon, an amber title, a distinct title string and an
 *  explanatory body line. The point is diagnostic: a screen whose help wiring is broken must not
 *  present a normal, inviting help icon that opens onto a blank panel, because that is
 *  indistinguishable from the player simply mis-hovering.
 *
 *  <p>As of writing no shipped screen reaches this state — the two screens with no help wiring
 *  ({@code GuiAdvancedCraftingTable}, {@code GuiAutoCraftItems}) both return {@code false} from
 *  {@code shouldAddHelpLedger()}, so they never get this ledger. That is the intent: the warning is
 *  a regression alarm, not routine decoration. */
@SuppressWarnings("this-escape")
public class LedgerHelp extends Ledger_Neptune {
    private static final Identifier ICON_HELP = Identifier.parse("buildcraftunofficial:textures/icons/help.png");

    /** Border thickness for the highlight overlays (in pixels). */
    private static final int BORDER = 2;

    /** Badge, title and body colour used when the screen registers no help entries.
     *  Amber rather than red: this flags a gap, it is not an error the player caused.
     *  Fully opaque — it is painted as a backplate under the (black) help glyph. */
    private static final int COLOUR_WARNING = 0xFF_FF_AA_00;

    private IGuiElement selected = null;
    /** True once at least one element has offered a help entry. Drives the warning presentation.
     *  Latching (never cleared back to false) is deliberate — see {@link #drawIcon}. */
    private boolean foundAny = false;
    private boolean init = false;
    /** Whether the warning title/body are currently applied. Lets {@link #setWarningPresentation}
     *  be idempotent, so the normal (help-present) path never touches the text entries and cannot
     *  stomp the hover text written by {@link #updateHelpText}. */
    private boolean warningApplied = false;

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

    /** Probes the screen's help entries once, on the first draw, and renders the icon.
     *
     *  <p>Probing once is safe because {@code gui.shownElements} is complete and frozen for this
     *  ledger's whole lifetime: {@code GuiBC8.init()} clears the list, runs
     *  {@code initGuiElements()}, and only THEN constructs and appends the LedgerHelp — and every
     *  {@code shownElements.add} in the codebase runs from {@code initGuiElements()} (directly, or
     *  from an element constructor it invokes). A window resize re-runs {@code init()} and builds a
     *  FRESH LedgerHelp, so the probe re-runs rather than going stale. Nothing removes elements.
     *
     *  <p>It is deferred to the first draw rather than done in the constructor on purpose: some
     *  {@code addHelpElements} implementations dereference live tile state (e.g. ScreenDynamoMJ
     *  reads {@code menu.tile}), and the first draw is the later, safer point to touch that.
     *
     *  <p>If a future screen DOES populate {@code shownElements} lazily, the latching check in
     *  {@link #drawBackground} recovers the normal presentation as soon as the ledger is opened;
     *  the icon would merely carry the warning badge until then. */
    @Override
    protected void drawIcon(double x, double y, BCGraphics graphics) {
        if (!init) {
            init = true;
            List<HelpPosition> elements = new ArrayList<>();
            for (IGuiElement element : gui.shownElements) {
                element.addHelpElements(elements);
            }
            foundAny = !elements.isEmpty();
            setWarningPresentation(!foundAny);
        }
        // 1.12.2 swapped the sprite for BCLibSprites.WARNING_MINOR here; that texture was never
        // ported. A colour tint can't stand in for it either — help.png is a PURE BLACK glyph on
        // transparency, and a multiplicative tint of black is still black. So the warning is drawn
        // as an amber badge BEHIND the glyph: black-on-amber reads unmistakably as a warning, and
        // it needs no new art. fill() is node-uniform in BCGraphics, so it takes no directive.
        if (!foundAny) {
            graphics.fill((int) x, (int) y, (int) x + 16, (int) y + 16, COLOUR_WARNING);
        }
        // Draw the help icon
        //? if >=1.21.10 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_HELP,
            (int) x, (int) y, 0f, 0f, 16, 16, 16, 16);
        //?} else {
        /*graphics.blit(ICON_HELP,
            (int) x, (int) y, 0f, 0f, 16, 16, 16, 16);*/
        //?}
    }

    /** Swap the ledger between its normal and warning presentation, keeping title and body in step
     *  with the icon badge. Idempotent, so the latching net in {@link #drawBackground} can call it
     *  every frame. The warning body line matters as much as the icon: without it, opening the
     *  ledger still yields the blank panel that hid broken help wiring in the first place. */
    private void setWarningPresentation(boolean warn) {
        if (warn == warningApplied) return;
        warningApplied = warn;
        clearTextEntries();
        if (warn) {
            title = "gui.ledger.help.none";
            appendText(LocaleUtil.localize("gui.ledger.help.none.desc"), COLOUR_WARNING);
        } else {
            title = "gui.ledger.help";
        }
        calculateMaxSize();
    }

    /** Amber while the screen has offered no help entries, so the warning reads from the title bar
     *  too and not just the 16x16 icon. {@code init} guards the pre-probe frame; in practice
     *  {@link #drawIcon} runs before the title is drawn within the same
     *  {@link Ledger_Neptune#drawBackground} pass, so the very first frame is already resolved. */
    @Override
    public int getTitleColour() {
        return (init && !foundAny) ? COLOUR_WARNING : super.getTitleColour();
    }

    @Override
    public void drawBackground(float partialTicks) {
        // Draw the ledger panel itself (background, icon, text)
        super.drawBackground(partialTicks);

        // Draw the interactive overlays on top of the GUI when the ledger is open
        if (!shouldDrawOpen()) {
            return;
        }

        BCGraphics graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        boolean set = false;
        List<HelpPosition> elements = new ArrayList<>();
        for (IGuiElement element : gui.shownElements) {
            element.addHelpElements(elements);
            // Latching safety net for the one-shot probe in drawIcon(): if help entries appear
            // after the first draw, this un-warns the whole presentation (icon, title and body).
            // Only reachable while open, which is enough — the player has already engaged with the
            // ledger by then. No-op on every normal screen, where the probe already found help.
            if (!elements.isEmpty() && !foundAny) {
                foundAny = true;
                setWarningPresentation(false);
            }
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
    private void drawHighlightBorder(BCGraphics graphics, IGuiArea rect, int colour,
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
