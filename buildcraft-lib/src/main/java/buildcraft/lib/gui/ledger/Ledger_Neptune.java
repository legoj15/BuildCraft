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
import net.minecraft.resources.Identifier;
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

    // 9-sliced ledger background sprites (matches 1.12.2 BCLibSprites.LEDGER_LEFT/RIGHT)
    private static final ISprite SPRITE_LEFT = new SpriteRaw(
        Identifier.parse("buildcraftlib:textures/icons/ledger_left.png"), 0, 0, 1.0, 1.0);
    private static final ISprite SPRITE_RIGHT = new SpriteRaw(
        Identifier.parse("buildcraftlib:textures/icons/ledger_right.png"), 0, 0, 1.0, 1.0);
    private static final SpriteNineSliced SPRITE_SPLIT_LEFT =
        new SpriteNineSliced(SPRITE_LEFT, 4.0/16, 4.0/16, 12.0/16, 12.0/16, 1.0);
    private static final SpriteNineSliced SPRITE_SPLIT_RIGHT =
        new SpriteNineSliced(SPRITE_RIGHT, 4.0/16, 4.0/16, 12.0/16, 12.0/16, 1.0);

    public final BuildCraftGui gui;
    public final int colour;
    public final boolean expandPositive;

    // Position — set by auto-stacking in the constructor
    private final IGuiPosition positionLedgerStart;
    private final IGuiPosition positionLedgerIconStart;

    protected double maxWidth = 96, maxHeight = 48;

    protected double currentWidth = CLOSED_WIDTH;
    protected double currentHeight = CLOSED_HEIGHT;
    protected double lastWidth = currentWidth;
    protected double lastHeight = currentHeight;
    protected double interpWidth = lastWidth;
    protected double interpHeight = lastHeight;

    protected String title = "unknown";

    /** -1 means shrinking, 0 no change, 1 expanding */
    private int currentDifference = 0;

    // Text entries for open-panel content
    private final List<TextEntry> textEntries = new ArrayList<>();

    public Ledger_Neptune(BuildCraftGui gui, int colour, boolean expandPositive) {
        this.gui = gui;
        this.colour = colour;
        this.expandPositive = expandPositive;

        if (expandPositive) {
            // Right side — icon at left edge of ledger
            positionLedgerStart = gui.lowerRightLedgerPos;
            // Advance the right-side position downward for the next ledger
            gui.lowerRightLedgerPos = positionLedgerStart.offset(0, () -> this.getHeight() + 5);
            positionLedgerIconStart = positionLedgerStart.offset(2, LEDGER_GAP);
        } else {
            // Left side — ledger grows leftward
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

    public String getTitle() {
        return buildcraft.lib.misc.LocaleUtil.localize(title);
    }

    public int getTitleColour() {
        return 0xFF_E1_C9_2F;
    }

    /** Recalculate the maximum size based on text entries and title. */
    protected void calculateMaxSize() {
        Font font = Minecraft.getInstance().font;
        // Width: icon (16) + gap (4) + max text width + gap padding
        int maxTextWidth = font.width(getTitle());
        for (TextEntry entry : textEntries) {
            String text = entry.getText();
            int w = font.width(text);
            if (w > maxTextWidth) maxTextWidth = w;
        }
        // 2 (border) + 16 (icon) + 4 (gap) + text + 4 (gap) + 2 (border)
        maxWidth = Math.max(CLOSED_WIDTH, 2 + 16 + LEDGER_GAP + maxTextWidth + LEDGER_GAP + 2);
        // 4 (top gap) + title line + entries + 4 (bottom gap)
        int textHeight = font.lineHeight + 3; // title
        for (int i = 0; i < textEntries.size(); i++) {
            textHeight += font.lineHeight + 3;
        }
        maxHeight = Math.max(CLOSED_HEIGHT, LEDGER_GAP + textHeight + LEDGER_GAP);
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

        double startX = getX();
        double startY = getY();
        int x = (int) startX;
        int y = (int) startY;
        int w = (int) interpWidth;
        int h = (int) interpHeight;

        if (w <= 0 || h <= 0) return;

        // Draw 9-sliced ledger background sprite with colour tinting
        // Matches 1.12.2: RenderUtil.setGLColorFromIntPlusAlpha(colour) → split.draw()
        SpriteNineSliced split = expandPositive ? SPRITE_SPLIT_RIGHT : SPRITE_SPLIT_LEFT;
        int tintColour = 0xFF000000 | (colour & 0xFFFFFF);
        split.drawTinted(startX, startY, interpWidth, interpHeight, tintColour);

        // Draw icon (always visible)
        double iconX = positionLedgerIconStart.getX();
        double iconY = positionLedgerIconStart.getY();
        drawIcon(iconX, iconY, graphics);

        // Draw text content if expanded enough
        if (interpWidth > CLOSED_WIDTH + 10) {
            Font font = Minecraft.getInstance().font;
            // Text starts after the icon: icon(16) + gap(4)
            int textX = (int) iconX + 16 + LEDGER_GAP;
            int textY = (int) iconY + 1;

            // Clip to ledger bounds
            int maxTextX = x + w - LEDGER_GAP;

            // Draw title
            if (textX < maxTextX) {
                graphics.drawString(font, getTitle(), textX, textY, getTitleColour() | 0xFF000000, true);
            }
            textY += font.lineHeight + 3;

            // Draw text entries
            for (TextEntry entry : textEntries) {
                if (textY + font.lineHeight > y + interpHeight - LEDGER_GAP) break;
                if (textX < maxTextX) {
                    String text = entry.getText();
                    graphics.drawString(font, text, textX, textY,
                        entry.getColour() | 0xFF000000, entry.dropShadow);
                }
                textY += font.lineHeight + 3;
            }
        }

        // Draw tooltip when ledger is closed/closing and mouse hovers over it
        // Matches 1.12.2 Ledger_Neptune.addToolTips() behavior
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
        return positionLedgerStart.getY();
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
