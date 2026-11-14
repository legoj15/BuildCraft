/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import buildcraft.api.core.render.ISprite;
import buildcraft.lib.client.sprite.SpriteNineSliced;
import buildcraft.lib.client.sprite.SpriteRaw;
import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.IInteractionElement;
import buildcraft.lib.gui.elem.ScrollWindow;
import buildcraft.lib.gui.pos.IGuiPosition;

/** Base class for collapsible side panels (ledgers) in the GUI.
 *  Closely mirrors the 1.12.2 Ledger_Neptune rendering and animation behavior. */
@SuppressWarnings("this-escape")
public class Ledger_Neptune implements IGuiElement, IInteractionElement {
    public static final int LEDGER_GAP = 4;
    public static final int CLOSED_WIDTH = 2 + 16 + LEDGER_GAP;   // 22
    public static final int CLOSED_HEIGHT = LEDGER_GAP + 16 + LEDGER_GAP; // 24

    /** Width the panel spends on everything but the text column: border (2) + icon (16) + gap + text + gap + border (2). */
    private static final int OVERHEAD = 2 + 16 + LEDGER_GAP + LEDGER_GAP + 2; // 28

    /** Wrapped lines one wheel notch moves a scrolling ledger by. */
    private static final int LINES_PER_NOTCH = 3;
    private static final int SCROLLBAR_WIDTH = 2;
    private static final int SCROLLBAR_MIN_THUMB = 8;
    private static final int SCROLLBAR_TRACK_COLOUR = 0x66000000;
    private static final int SCROLLBAR_THUMB_COLOUR = 0xDDFFFFFF;

    // 16x16 nine-sliced ledger background sprites (matches 1.12.2 BCLibSprites.LEDGER_LEFT/RIGHT).
    // Scale = 16.0 so the 4/16 = 0.25 normalized border maps to 4 actual pixels.
    private static final ISprite SPRITE_LEFT = new SpriteRaw(
        Identifier.parse("buildcraftunofficial:textures/icons/ledger_left.png"), 0, 0, 1.0, 1.0);
    private static final ISprite SPRITE_RIGHT = new SpriteRaw(
        Identifier.parse("buildcraftunofficial:textures/icons/ledger_right.png"), 0, 0, 1.0, 1.0);
    private static final SpriteNineSliced SPRITE_SPLIT_LEFT =
        new SpriteNineSliced(SPRITE_LEFT, 4.0 / 16, 4.0 / 16, 12.0 / 16, 12.0 / 16, 16.0);
    private static final SpriteNineSliced SPRITE_SPLIT_RIGHT =
        new SpriteNineSliced(SPRITE_RIGHT, 4.0 / 16, 4.0 / 16, 12.0 / 16, 12.0 / 16, 16.0);

    public final BuildCraftGui gui;
    public final int colour;
    public final boolean expandPositive;

    // Position — set by auto-stacking in the constructor
    private final IGuiPosition positionLedgerStart;
    private final IGuiPosition positionLedgerIconStart;
    /** Stable anchor position for width calculation (not offset by -getWidth()). */
    private final IGuiPosition positionAnchor;

    protected double maxWidth = 96, maxHeight = 48;

    protected double currentWidth = CLOSED_WIDTH;
    protected double currentHeight = CLOSED_HEIGHT;
    protected double lastWidth = currentWidth;
    protected double lastHeight = currentHeight;
    protected double interpWidth = lastWidth;
    protected double interpHeight = lastHeight;

    /** Per-frame upward Y shift that keeps an overflowing ledger on screen.
     *  Recomputed every {@link #drawBackground} from the live drawn height (not the cached
     *  max height), and subtracted by EVERY coordinate system — background panel, icon, text,
     *  scissor clip and the {@link #getY()} hit-box — so the whole ledger moves as one rigid
     *  unit and the shift relaxes to 0 as the panel collapses (it glides back to its anchor
     *  instead of detaching). 0 whenever the ledger fits or is closed. */
    private double currentShift = 0;

    protected String title = "unknown";

    /** -1 means shrinking, 0 no change, 1 expanding */
    private int currentDifference = 0;

    /** Persisted open/closed flag for this ledger. State survives GUI close/reopen and MC restarts.
     *  Key is {@code (parentScreenClass, ledgerClass)}, mirroring 1.12.2's per-GUI per-ledger scoping. */
    private final buildcraft.lib.gui.config.GuiPropertyBoolean openProperty;
    /** One wrapped line of the open panel: the title's lines first, then each entry's lines. {@code entry} is null
     *  for a title line, so the colour and shadow are read live (an entry's colour may be dynamic). */
    private record LayoutLine(String text, TextEntry entry) {}

    /** The wrapped text for one set of texts at one text-column width. Kept until either changes, so sizing and
     *  drawing share one result and nothing is re-split every frame. */
    private record Layout(List<String> texts, int textWidth, List<LayoutLine> lines) {}

    private Layout layout;

    /** Pixels the content is scrolled up inside the panel, 0 to {@link #scrollMax()}. */
    private int scrollOffset;

    /** The room under the viewport the last sizing was made for; when it changes (a recipe viewer put buttons where
     *  the ledger was) the next frame sizes the panel again. */
    private int sizedForAvailable = -1;

    /** True iff the persisted value was open at construction time. Applied on first
     *  {@link #drawBackground} — deferred so subclass {@code appendText} calls land first
     *  and {@link #calculateMaxSize()} sees the full content. */
    private boolean pendingInitialOpen;
    private boolean appliedInitialState;

    /**
     * Copy the full animation state from another ledger instance.
     * Used to seamlessly continue animation across window resizes,
     * where init() destroys and re-creates all ledger instances.
     */
    public void copyAnimationStateFrom(Ledger_Neptune other) {
        this.currentDifference = other.currentDifference;
        this.currentWidth = other.currentWidth;
        this.currentHeight = other.currentHeight;
        this.lastWidth = other.lastWidth;
        this.lastHeight = other.lastHeight;
        this.interpWidth = other.interpWidth;
        this.interpHeight = other.interpHeight;
        // Clamped against this instance's own content the next time it draws.
        this.scrollOffset = other.scrollOffset;
        // Recalculate max size for this instance's text content,
        // then clamp current values so they don't exceed the new max
        this.calculateMaxSize();
        this.currentWidth = Math.min(this.currentWidth, this.maxWidth);
        this.currentHeight = Math.min(this.currentHeight, this.maxHeight);
        this.lastWidth = Math.min(this.lastWidth, this.maxWidth);
        this.lastHeight = Math.min(this.lastHeight, this.maxHeight);
        // Window-resize copy supersedes the persisted-state apply — the in-flight animation
        // from the previous instance is more accurate than the saved boolean.
        this.appliedInitialState = true;
    }

    // Text entries for open-panel content
    private final List<TextEntry> textEntries = new ArrayList<>();

    public Ledger_Neptune(BuildCraftGui gui, int colour, boolean expandPositive) {
        this.gui = gui;
        this.colour = colour;
        this.expandPositive = expandPositive;

        if (expandPositive) {
            // Right side — icon at left edge of ledger
            positionLedgerStart = gui.lowerRightLedgerPos;
            positionAnchor = positionLedgerStart;
            // Advance the right-side position downward for the next ledger
            gui.lowerRightLedgerPos = positionLedgerStart.offset(0, () -> this.getHeight() + 5);
            positionLedgerIconStart = positionLedgerStart.offset(2, LEDGER_GAP);
        } else {
            // Left side — ledger grows leftward
            positionAnchor = gui.lowerLeftLedgerPos; // stable reference for width calc
            positionLedgerStart = gui.lowerLeftLedgerPos.offset(() -> -this.getWidth(), 0);
            // Advance the left-side position downward for the next ledger
            gui.lowerLeftLedgerPos = gui.lowerLeftLedgerPos.offset(0, () -> this.getHeight() + 5);
            positionLedgerIconStart = positionLedgerStart.offset(LEDGER_GAP, LEDGER_GAP);
        }

        // Resolve the persisted open/closed flag. Keyed by parent screen class so each GUI
        // remembers its own ledger state independently (matches 1.12.2 GuiConfigManager semantics).
        String guiId = gui.gui != null ? gui.gui.getClass().getName() : "unknown";
        String propName = this.getClass().getSimpleName() + ".is_open";
        this.openProperty = buildcraft.lib.gui.config.GuiConfigManager.getOrAddBoolean(guiId, propName, false);
        this.pendingInitialOpen = this.openProperty.get();
    }

    /** Append a static text line to the ledger. */
    public TextEntry appendText(String text, int colour) {
        TextEntry entry = new TextEntry(() -> text, () -> colour);
        textEntries.add(entry);
        return entry;
    }

    /** Append a dynamic text line to the ledger. */
    public TextEntry appendText(Supplier<String> textSupplier, int colour) {
        TextEntry entry = new TextEntry(textSupplier, () -> colour);
        textEntries.add(entry);
        return entry;
    }

    /** Append a dynamic text line with dynamic colour. */
    public TextEntry appendText(Supplier<String> textSupplier, IntSupplier colour) {
        TextEntry entry = new TextEntry(textSupplier, colour);
        textEntries.add(entry);
        return entry;
    }

    /** Clear all text entries. Used by LedgerHelp to dynamically swap content on hover. The new content starts at
     *  its top, so the scroll position is dropped too, and so is the cached layout: its lines point at the old entries,
     *  so entries added again with the same text but another colour or shadow must not be drawn from it. */
    protected void clearTextEntries() {
        textEntries.clear();
        layout = null;
        scrollOffset = 0;
    }

    public String getTitle() {
        return buildcraft.lib.misc.LocaleUtil.localize(title);
    }

    public int getTitleColour() {
        return 0xFF_E1_C9_2F;
    }

    /** The title followed by every entry's current text: what the layout is built from, and how a change in a
     *  dynamic entry is noticed. */
    private List<String> snapshotTexts() {
        List<String> texts = new ArrayList<>(textEntries.size() + 1);
        texts.add(getTitle());
        for (TextEntry entry : textEntries) {
            String text = entry.getText();
            texts.add(text == null ? "" : text);
        }
        return texts;
    }

    /** Pixel width of the column the text wraps to at the current {@link #maxWidth}. */
    private int textColumnWidth() {
        return Math.max(1, (int) maxWidth - OVERHEAD);
    }

    private static int lineStep(Font font) {
        return font.lineHeight + 3;
    }

    /** Wraps the title, then each entry, to {@code textWidth} with {@link LedgerTextWrap}. Cached: a call with the
     *  same texts and width returns the earlier result. */
    private Layout layoutFor(List<String> texts, int textWidth) {
        Layout cached = layout;
        if (cached != null && cached.textWidth() == textWidth && cached.texts().equals(texts)) return cached;

        Font font = Minecraft.getInstance().font;
        List<LayoutLine> lines = new ArrayList<>();
        for (String line : LedgerTextWrap.wrap(texts.get(0), textWidth, font::width)) {
            lines.add(new LayoutLine(line, null));
        }
        int entries = Math.min(textEntries.size(), texts.size() - 1);
        for (int i = 0; i < entries; i++) {
            TextEntry entry = textEntries.get(i);
            for (String line : LedgerTextWrap.wrap(texts.get(i + 1), textWidth, font::width)) {
                lines.add(new LayoutLine(line, entry));
            }
        }
        layout = new Layout(texts, textWidth, lines);
        return layout;
    }

    /** GUI pixels of screen height this ledger may use: all of it, less the strip a recipe viewer keeps at the
     *  bottom of the left side (see {@link LedgerViewport}). Right-side ledgers reserve nothing.
     *
     *  <p>The viewer is asked with the left edge the ledger has at its FULL width, not the animated one: the width is
     *  fixed by the text (see {@link #calculateMaxSize()}, which sets it before the height), so the answer cannot flip
     *  while the panel opens and closes and drag the height back and forth. */
    private int availableHeight() {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (expandPositive) return Math.max(0, screenHeight);
        int fullWidthLeftX = (int) Math.floor(positionAnchor.getX() - maxWidth);
        return Math.max(0, screenHeight - LedgerViewport.leftBottomReserve(fullWidthLeftX));
    }

    /** Lowers {@link #maxHeight} to {@link #availableHeight()} (never below the closed height); the content past it
     *  scrolls. Called by {@link #calculateMaxSize()}; a subclass that sizes itself may call it after its own sizing. */
    protected final void applyHeightCap() {
        int available = availableHeight();
        sizedForAvailable = available;
        maxHeight = Math.max(CLOSED_HEIGHT, Math.min(maxHeight, available));
    }

    /** How far the content can scroll: its wrapped height less the viewport the open panel leaves for it. */
    private int scrollMax() {
        Layout current = layout;
        if (current == null) return 0;
        int contentHeight = current.lines().size() * lineStep(Minecraft.getInstance().font);
        return Math.max(0, contentHeight - viewportHeight());
    }

    /** Height of the part of the open panel that shows text: the panel less the gap above and below. */
    private int viewportHeight() {
        return (int) maxHeight - LEDGER_GAP * 2;
    }

    /** The layout for the current texts. Sizes the panel again first when the texts, or the room under the viewport,
     *  changed since the last sizing, so a dynamic entry that grows while open still fits. */
    private Layout currentLayout() {
        List<String> texts = snapshotTexts();
        int available = availableHeight();
        Layout cached = layout;
        if (cached == null || available != sizedForAvailable || !cached.texts().equals(texts)) {
            sizedForAvailable = available;
            calculateMaxSize();
        }
        return layoutFor(texts, textColumnWidth());
    }

    /** Recalculate the maximum size from the title and text entries: the width that fits the longest line (up to the
     *  gap beside the window), then the height of the text wrapped to that width (up to the screen height; the
     *  rest scrolls). */
    protected void calculateMaxSize() {
        Font font = Minecraft.getInstance().font;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        List<String> texts = snapshotTexts();

        // Compute the natural (unwrapped) ledger width from the widest line of the title and the entries
        int naturalMaxTextWidth = 0;
        for (String text : texts) {
            for (String line : text.split("\n", -1)) {
                naturalMaxTextWidth = Math.max(naturalMaxTextWidth, font.width(line));
            }
        }
        int naturalWidth = OVERHEAD + naturalMaxTextWidth;

        // Cap the total ledger width so it doesn't extend past the screen edge.
        // Use the stable anchor position (not affected by -getWidth() offset).
        int maxAllowedWidth;
        if (expandPositive) {
            // Right-side: ledger starts at anchor X and grows rightward
            maxAllowedWidth = Math.max(CLOSED_WIDTH, screenWidth - (int) positionAnchor.getX());
        } else {
            // Left-side: ledger grows leftward from anchor X toward screen left (x=0)
            maxAllowedWidth = Math.max(CLOSED_WIDTH, (int) positionAnchor.getX());
        }

        maxWidth = Math.min(naturalWidth, maxAllowedWidth);
        maxWidth = Math.max(CLOSED_WIDTH, maxWidth);

        // Height: the title and entries wrapped to the text column this width leaves. drawBackground draws from the
        // same layout, so what is measured here is exactly what is drawn.
        Layout wrapped = layoutFor(texts, textColumnWidth());
        maxHeight = Math.max(CLOSED_HEIGHT, LEDGER_GAP + wrapped.lines().size() * lineStep(font) + LEDGER_GAP);
        applyHeightCap();
        // The on-screen shift is no longer derived here (from the static max height) — it is
        // recomputed every frame from the live drawn height in drawBackground(), via
        // computeUpShift(), so it stays correct throughout the open/collapse animation.
    }

    /** Compute the upward shift that keeps a ledger of the given drawn height on screen.
     *  Returns 0 when the ledger fits within the screen below its anchor; otherwise the
     *  number of pixels it overflows past the screen bottom, CLAMPED so the top never rises
     *  above the screen (content taller than the whole screen still overflows downward — the
     *  intended/accepted behaviour). Pure function of its inputs so it is unit-testable. */
    static double computeUpShift(double anchorY, double drawnHeight, int screenHeight) {
        double overflow = (anchorY + drawnHeight) - screenHeight;
        if (overflow <= 0) return 0;
        return Math.min(overflow, Math.max(0, anchorY));
    }

    @Override
    public void tick() {
        lastWidth = currentWidth;
        lastHeight = currentHeight;

        double targetWidth, targetHeight;
        if (currentDifference == 1) {
            targetWidth = maxWidth;
            targetHeight = maxHeight;
        } else if (currentDifference == -1) {
            targetWidth = CLOSED_WIDTH;
            targetHeight = CLOSED_HEIGHT;
        } else {
            return;
        }

        // 1.12.2 animation speed: proportional to ledger size, clamped 1..15
        double maxDiff = Math.max(maxWidth - CLOSED_WIDTH, maxHeight - CLOSED_HEIGHT);
        double ldgDiff = Mth.clamp(maxDiff / 5, 1, 15);

        currentWidth = approach(currentWidth, targetWidth, ldgDiff);
        currentHeight = approach(currentHeight, targetHeight, ldgDiff);
    }

    private static double approach(double current, double target, double speed) {
        if (current < target) return Math.min(current + speed, target);
        if (current > target) return Math.max(current - speed, target);
        return target;
    }

    private static double interp(double past, double current, float partialTicks) {
        if (past == current) return current;
        if (partialTicks <= 0) return past;
        if (partialTicks >= 1) return current;
        return past * (1 - partialTicks) + current * partialTicks;
    }

    public final boolean shouldDrawOpen() {
        return currentWidth > CLOSED_WIDTH || currentHeight > CLOSED_HEIGHT;
    }

    @Override
    public void drawBackground(float partialTicks) {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        // First-frame snap from persisted state. Done here (rather than in the constructor)
        // because subclass appendText() calls run after super(), so this is the earliest point
        // calculateMaxSize() sees the full content. Snapping rather than animating means the
        // restored ledger appears already-open from frame zero — no startup-flicker.
        if (!appliedInitialState) {
            appliedInitialState = true;
            if (pendingInitialOpen) {
                calculateMaxSize();
                currentDifference = 1;
                currentWidth = maxWidth;
                currentHeight = maxHeight;
                lastWidth = maxWidth;
                lastHeight = maxHeight;
            }
        }

        interpWidth = interp(lastWidth, currentWidth, partialTicks);
        interpHeight = interp(lastHeight, currentHeight, partialTicks);

        // Keep an overflowing ledger on screen by shifting the WHOLE ledger up by one shared
        // amount, derived from the height we're actually drawing this frame (interpHeight) and
        // the height available to this side (the screen less any reserved strip; maxHeight is
        // already capped to it, so a capped ledger sits exactly between y=0 and that height).
        // Background, icon, text, scissor and the getY() hit-box all
        // subtract this same value, so they move rigidly together; and because it tracks the
        // animated height it relaxes to 0 as the ledger collapses. anchorY is the raw, unshifted
        // anchor (positionLedgerStart never routes through our shifted getY()).
        currentShift = computeUpShift(positionLedgerStart.getY(), interpHeight, availableHeight());

        // Compute draw X directly from interpWidth (not via getX()→getWidth()) to
        // guarantee both rawX and w use the same interpolation value.
        // If getX() were used, it calls getWidth()→gui.getLastPartialTicks() which can
        // differ from the partialTicks argument by a tiny epsilon; that epsilon causes
        // ceil(interpWidth + (rawX - floor(rawX))) to jump ±1 across frames, flickering
        // the rightmost 1–2px of the ledger during animation.
        //
        // Left ledgers:  rawX = anchorX - interpWidth  (fractional; floor pulls left edge out)
        //                w    = anchorX - floor(rawX)  (guaranteed right edge = anchorX exactly)
        // Right ledgers: rawX = start position (integer anchor, no width dependency)
        //                w    = ceil(interpWidth)       (always covers full pixel width)
        double rawX, rawY;
        if (expandPositive) {
            rawX = positionLedgerStart.getX();         // integer anchor; no width dependency
        } else {
            rawX = positionAnchor.getX() - interpWidth; // consistent with interpWidth
        }
        rawY = getY(); // Y never depends on width; no drift issue
        int x = (int) Math.floor(rawX);
        int y = (int) Math.floor(rawY);
        int w, h;
        if (expandPositive) {
            // Right-side: anchor at left, grows rightward — ceil so right edge covers fully
            w = (int) Math.ceil(interpWidth);
        } else {
            // Left-side: anchor at right — pin the right edge exactly to anchorX to avoid flicker
            w = (int) positionAnchor.getX() - x;
        }
        h = (int) Math.ceil(interpHeight + (rawY - y));

        if (w <= 0 || h <= 0) return;

        // Draw nine-sliced ledger background with colour tinting.
        // Uses ledger_left.png for left ledgers, ledger_right.png for right,
        // matching 1.12.2 BCLibSprites.LEDGER_LEFT/RIGHT.
        SpriteNineSliced split = expandPositive ? SPRITE_SPLIT_RIGHT : SPRITE_SPLIT_LEFT;
        int tintColour = 0xFF000000 | (colour & 0xFFFFFF);
        split.drawTinted(x, y, w, h, tintColour);

        // Scissor clip all content (icon + text) to the ledger's current animated bounds.
        // Matches 1.12.2's GuiUtil.scissor() which masked content during expand/contract.
        int scissorX = (int) positionLedgerIconStart.getX();
        int scissorY = (int) (positionLedgerIconStart.getY() - currentShift);
        int scissorW = (int) (interpWidth - LEDGER_GAP);
        int scissorH = (int) (interpHeight - LEDGER_GAP * 2);
        graphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);

        // Draw icon (always visible)
        double iconX = positionLedgerIconStart.getX();
        double iconY = positionLedgerIconStart.getY() - currentShift;
        drawIcon(iconX, iconY, graphics);

        // The wrapped text, shared with calculateMaxSize(); sizes the panel again first if a dynamic entry changed.
        // Taken after drawIcon on purpose: a subclass may swap its title and body there (LedgerHelp does on its first
        // icon draw), and the first frame of a ledger restored open must draw the new text, not the one it started with.
        Layout content = currentLayout();

        // Draw text content if expanded enough
        if (interpWidth > CLOSED_WIDTH + 10) {
            drawContent(graphics, content, (int) iconX, (int) iconY, scissorY, scissorY + scissorH);
        }

        graphics.disableScissor();

        // Draw tooltip when ledger is closed/closing and mouse hovers over it
        if (!shouldDrawOpen() && contains(gui.mouse.getX(), gui.mouse.getY())) {
            var titleComp = net.minecraft.network.chat.Component.literal(getTitle());
            graphics.setTooltipForNextFrame(titleComp,
                (int) gui.mouse.getX(), (int) gui.mouse.getY());
        }
    }

    /** Draws the wrapped title and entries, shifted up by the scroll offset, inside the caller's scissor, and the
     *  scroll bar when the content is taller than the viewport. {@code iconX}/{@code iconY} are the icon's
     *  top-left (the text column starts beside it); {@code viewTop}/{@code viewBottom} bound the visible rows so
     *  lines scrolled out of sight are not submitted at all. */
    private void drawContent(BCGraphics graphics, Layout content, int iconX, int iconY, int viewTop, int viewBottom) {
        Font font = Minecraft.getInstance().font;
        int step = lineStep(font);
        // Text starts after the icon: icon(16) + gap(4)
        int textX = iconX + 16 + LEDGER_GAP;

        int scrollMax = scrollMax();
        scrollOffset = Mth.clamp(scrollOffset, 0, scrollMax);

        int textY = iconY + 1 - scrollOffset;
        for (LayoutLine line : content.lines()) {
            if (textY + font.lineHeight > viewTop && textY < viewBottom) {
                TextEntry entry = line.entry();
                if (entry == null) {
                    graphics.text(font, line.text(), textX, textY, getTitleColour() | 0xFF000000, true);
                } else {
                    graphics.text(font, line.text(), textX, textY, entry.getColour() | 0xFF000000, entry.dropShadow);
                }
            }
            textY += step;
        }

        if (scrollMax > 0) {
            // Along the inner edge of the text column, in the margin the panel keeps there anyway.
            int barX = textX + textColumnWidth() + 1;
            // The thumb's size comes from the settled viewport, but the track only runs as far as the panel is drawn,
            // so a half-open panel never shows a bar taller than itself, nor a thumb outside its track.
            int viewport = viewportHeight();
            int trackHeight = Math.min(viewport, viewBottom - iconY);
            if (trackHeight <= 0) return;
            graphics.fill(barX, iconY, barX + SCROLLBAR_WIDTH, iconY + trackHeight, SCROLLBAR_TRACK_COLOUR);
            int contentHeight = viewport + scrollMax;
            int thumbHeight = Math.min(trackHeight,
                    Math.max(SCROLLBAR_MIN_THUMB, trackHeight * viewport / contentHeight));
            int thumbTop = iconY + (trackHeight - thumbHeight) * scrollOffset / scrollMax;
            graphics.fill(barX, thumbTop, barX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, SCROLLBAR_THUMB_COLOUR);
        }
    }

    /** True while the panel is open far enough to show text and its content is taller than its viewport. */
    protected final boolean canScroll() {
        return currentDifference == 1 && interpWidth > CLOSED_WIDTH + 10 && scrollMax() > 0;
    }

    /** Scrolls the content by {@value #LINES_PER_NOTCH} lines per wheel notch; positive {@code amountY} (wheel up)
     *  moves toward the top. */
    protected final void scrollByWheel(double amountY) {
        int step = lineStep(Minecraft.getInstance().font);
        scrollOffset = Mth.clamp(scrollOffset + ScrollWindow.wheelRows(amountY) * LINES_PER_NOTCH * step, 0, scrollMax());
    }

    /** The wheel scrolls an open ledger whose content is taller than its viewport while the mouse is over it. A wheel
     *  event with no vertical movement scrolls nothing, so it is left for whatever sits beneath. */
    @Override
    public boolean onMouseScrolled(double amountY) {
        if (amountY == 0 || !canScroll() || !contains(gui.mouse)) return false;
        scrollByWheel(amountY);
        return true;
    }

    /** Override in subclasses to draw a 16x16 icon. */
    protected void drawIcon(double x, double y, BCGraphics graphics) {
        // Default: nothing
    }

    @Override
    public void onMouseClicked(int button) {
        double mouseX = gui.mouse.getX();
        double mouseY = gui.mouse.getY();
        if (contains(mouseX, mouseY)) {
            boolean nowOpen;
            if (currentDifference == 1) {
                currentDifference = -1;
                scrollOffset = 0;
                nowOpen = false;
            } else {
                currentDifference = 1;
                calculateMaxSize();
                nowOpen = true;
            }
            openProperty.set(nowOpen);
        }
    }

    @Override
    public void onMouseDragged(int button, long timeSinceLastClick) {}

    @Override
    public void onMouseReleased(int button) {}

    @Override
    public double getX() {
        return positionLedgerStart.getX();
    }

    @Override
    public double getY() {
        // Subtract the same per-frame shift the panel/icon/text use (see drawBackground), so the
        // hit-box always coincides with the visible ledger. currentShift is naturally 0 when the
        // ledger fits or is closed, so no animation-phase guard is needed.
        return positionLedgerStart.getY() - currentShift;
    }

    @Override
    public double getWidth() {
        float partialTicks = gui.getLastPartialTicks();
        if (lastWidth == currentWidth) return currentWidth;
        else if (partialTicks <= 0) return lastWidth;
        else if (partialTicks >= 1) return currentWidth;
        else return lastWidth * (1 - partialTicks) + currentWidth * partialTicks;
    }

    @Override
    public double getHeight() {
        float partialTicks = gui.getLastPartialTicks();
        if (lastHeight == currentHeight) return currentHeight;
        else if (partialTicks <= 0) return lastHeight;
        else if (partialTicks >= 1) return currentHeight;
        else return lastHeight * (1 - partialTicks) + currentHeight * partialTicks;
    }

    /** Represents a single text entry in the ledger. */
    public static class TextEntry {
        public final Supplier<String> textSupplier;
        public final IntSupplier colourSupplier;
        public boolean dropShadow = false;

        public TextEntry(Supplier<String> textSupplier, IntSupplier colourSupplier) {
            this.textSupplier = textSupplier;
            this.colourSupplier = colourSupplier;
        }

        public TextEntry setDropShadow(boolean shadow) {
            this.dropShadow = shadow;
            return this;
        }

        public String getText() {
            return textSupplier.get();
        }

        public int getColour() {
            return colourSupplier.getAsInt();
        }
    }
}
