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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import buildcraft.api.core.render.ISprite;
import buildcraft.lib.client.sprite.SpriteNineSliced;
import buildcraft.lib.client.sprite.SpriteRaw;
import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.IInteractionElement;
import buildcraft.lib.gui.pos.IGuiPosition;

/** Base class for collapsible side panels (ledgers) in the GUI.
 *  Closely mirrors the 1.12.2 Ledger_Neptune rendering and animation behavior. */
public class Ledger_Neptune implements IGuiElement, IInteractionElement {
    public static final int LEDGER_GAP = 4;
    public static final int CLOSED_WIDTH = 2 + 16 + LEDGER_GAP;   // 22
    public static final int CLOSED_HEIGHT = LEDGER_GAP + 16 + LEDGER_GAP; // 24

    // 16x16 nine-sliced ledger background sprites (matches 1.12.2 BCLibSprites.LEDGER_LEFT/RIGHT).
    // Scale = 16.0 so the 4/16 = 0.25 normalized border maps to 4 actual pixels.
    private static final ISprite SPRITE_LEFT = new SpriteRaw(
        Identifier.parse("buildcraftlib:textures/icons/ledger_left.png"), 0, 0, 1.0, 1.0);
    private static final ISprite SPRITE_RIGHT = new SpriteRaw(
        Identifier.parse("buildcraftlib:textures/icons/ledger_right.png"), 0, 0, 1.0, 1.0);
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

    /** Computed upward Y shift when ledger hits the bottom of the screen. */
    private double yShift = 0;

    protected String title = "unknown";

    /** -1 means shrinking, 0 no change, 1 expanding */
    private int currentDifference = 0;

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
        // Recalculate max size for this instance's text content,
        // then clamp current values so they don't exceed the new max
        this.calculateMaxSize();
        this.currentWidth = Math.min(this.currentWidth, this.maxWidth);
        this.currentHeight = Math.min(this.currentHeight, this.maxHeight);
        this.lastWidth = Math.min(this.lastWidth, this.maxWidth);
        this.lastHeight = Math.min(this.lastHeight, this.maxHeight);
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

    /** Clear all text entries. Used by LedgerHelp to dynamically swap content on hover. */
    protected void clearTextEntries() {
        textEntries.clear();
    }

    public String getTitle() {
        return buildcraft.lib.misc.LocaleUtil.localize(title);
    }

    public int getTitleColour() {
        return 0xFF_E1_C9_2F;
    }

    /** Recalculate the maximum size based on text entries and title, with word wrapping. */
    protected void calculateMaxSize() {
        Font font = Minecraft.getInstance().font;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        // Non-text overhead: 2 (border) + 16 (icon) + 4 (gap) ... text ... + 4 (gap) + 2 (border)
        int overhead = 2 + 16 + LEDGER_GAP + LEDGER_GAP + 2;

        // Compute the natural (unwrapped) ledger width from the widest text entry
        int naturalMaxTextWidth = font.width(getTitle());
        for (TextEntry entry : textEntries) {
            int w = font.width(entry.getText());
            if (w > naturalMaxTextWidth) naturalMaxTextWidth = w;
        }
        int naturalWidth = overhead + naturalMaxTextWidth;

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

        // Derive the text area width from the capped maxWidth for wrapping
        int textAreaWidth = Math.max(40, (int) maxWidth - overhead);

        // Height: title (always 1 line) + wrapped text entries
        int textHeight = font.lineHeight + 3; // title
        for (TextEntry entry : textEntries) {
            List<FormattedCharSequence> wrapped = font.split(Component.literal(entry.getText()), textAreaWidth);
            int lineCount = Math.max(1, wrapped.size());
            textHeight += (font.lineHeight + 3) * lineCount;
        }
        maxHeight = Math.max(CLOSED_HEIGHT, LEDGER_GAP + textHeight + LEDGER_GAP);

        // Upward shift: if the ledger bottom would extend past the screen, push it up
        double normalY = positionLedgerStart.getY();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double bottomEdge = normalY + maxHeight;
        if (bottomEdge > screenHeight) {
            yShift = bottomEdge - screenHeight;
        } else {
            yShift = 0;
        }
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
        GuiGraphics graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        interpWidth = interp(lastWidth, currentWidth, partialTicks);
        interpHeight = interp(lastHeight, currentHeight, partialTicks);

        int x = (int) getX();
        int y = (int) getY();
        int w = (int) interpWidth;
        int h = (int) interpHeight;

        if (w <= 0 || h <= 0) return;

        // Draw nine-sliced ledger background with colour tinting.
        // Uses ledger_left.png for left ledgers, ledger_right.png for right,
        // matching 1.12.2 BCLibSprites.LEDGER_LEFT/RIGHT.
        SpriteNineSliced split = expandPositive ? SPRITE_SPLIT_RIGHT : SPRITE_SPLIT_LEFT;
        int tintColour = 0xFF000000 | (colour & 0xFFFFFF);
        split.drawTinted(getX(), getY(), interpWidth, interpHeight, tintColour);

        // Scissor clip all content (icon + text) to the ledger's current animated bounds.
        // Matches 1.12.2's GuiUtil.scissor() which masked content during expand/contract.
        int scissorX = (int) positionLedgerIconStart.getX();
        int scissorY = (int) positionLedgerIconStart.getY();
        int scissorW = (int) (interpWidth - LEDGER_GAP);
        int scissorH = (int) (interpHeight - LEDGER_GAP * 2);
        graphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);

        // Draw icon (always visible)
        double iconX = positionLedgerIconStart.getX();
        double iconY = positionLedgerIconStart.getY();
        drawIcon(iconX, iconY, graphics);

        // Draw text content if expanded enough
        if (interpWidth > CLOSED_WIDTH + 10) {
            Font font = Minecraft.getInstance().font;
            int textAreaWidth = (int) maxWidth - 2 - 16 - LEDGER_GAP - LEDGER_GAP - 2;
            // Text starts after the icon: icon(16) + gap(4)
            int textX = (int) iconX + 16 + LEDGER_GAP;
            int textY = (int) iconY + 1;

            // Draw title (not wrapped — titles are always short)
            graphics.drawString(font, getTitle(), textX, textY, getTitleColour() | 0xFF000000, true);
            textY += font.lineHeight + 3;

            // Draw text entries with word wrapping
            for (TextEntry entry : textEntries) {
                int entryColour = entry.getColour() | 0xFF000000;
                List<FormattedCharSequence> wrapped = font.split(
                    Component.literal(entry.getText()), textAreaWidth);
                for (FormattedCharSequence line : wrapped) {
                    graphics.drawString(font, line, textX, textY, entryColour, entry.dropShadow);
                    textY += font.lineHeight + 3;
                }
            }
        }

        graphics.disableScissor();

        // Draw tooltip when ledger is closed/closing and mouse hovers over it
        if (!shouldDrawOpen() && contains(gui.mouse.getX(), gui.mouse.getY())) {
            Font font2 = Minecraft.getInstance().font;
            var titleComp = net.minecraft.network.chat.Component.literal(getTitle());
            var tooltipLine = net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(titleComp.getVisualOrderText());
            graphics.renderTooltip(font2,
                java.util.List.of(tooltipLine),
                (int) gui.mouse.getX(), (int) gui.mouse.getY(),
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                null);
        }
    }


    /** Override in subclasses to draw a 16x16 icon. */
    protected void drawIcon(double x, double y, GuiGraphics graphics) {
        // Default: nothing
    }

    @Override
    public void onMouseClicked(int button) {
        double mouseX = gui.mouse.getX();
        double mouseY = gui.mouse.getY();
        if (contains(mouseX, mouseY)) {
            if (currentDifference == 1) {
                currentDifference = -1;
            } else {
                currentDifference = 1;
                calculateMaxSize();
            }
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
        // Apply upward shift when ledger would extend past screen bottom.
        // Only shift when actually expanding (not when closed).
        double shift = (currentDifference != 0 || currentHeight > CLOSED_HEIGHT) ? yShift : 0;
        return positionLedgerStart.getY() - shift;
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
