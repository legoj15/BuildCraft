/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.pos.IGuiArea;

/** Base class for collapsible side panels (ledgers) in the GUI.
 *  Renders as a colored rectangle with title and text entries. */
public class Ledger_Neptune implements IGuiElement {
    protected final BuildCraftGui gui;
    protected final int colour;
    protected final boolean expandPositive;
    protected String title = "";

    private final List<TextEntry> textEntries = new ArrayList<>();

    // Animation state
    private boolean isOpen = false;
    private double currentWidth = 24;
    private double currentHeight = 24;
    private double targetWidth = 24;
    private double targetHeight = 24;

    private static final double MIN_WIDTH = 24;
    private static final double MIN_HEIGHT = 24;
    private static final double ANIMATION_SPEED = 4.0;

    // Position — set by the GUI framework
    private double posX, posY;

    public Ledger_Neptune(BuildCraftGui gui, int colour, boolean expandPositive) {
        this.gui = gui;
        this.colour = colour;
        this.expandPositive = expandPositive;
    }

    public void setPosition(double x, double y) {
        this.posX = x;
        this.posY = y;
    }

    /** Append a static text line to the ledger. */
    public TextEntry appendText(String text, int colour) {
        TextEntry entry = new TextEntry(text, null, colour);
        textEntries.add(entry);
        return entry;
    }

    /** Append a dynamic text line to the ledger. */
    public TextEntry appendText(Supplier<String> textSupplier, int colour) {
        TextEntry entry = new TextEntry(null, textSupplier, colour);
        textEntries.add(entry);
        return entry;
    }

    /** Recalculate the maximum size based on text entries. */
    protected void calculateMaxSize() {
        Font font = Minecraft.getInstance().font;
        int maxTextWidth = font.width(title);
        for (TextEntry entry : textEntries) {
            String text = entry.getText();
            int w = font.width(text);
            if (w > maxTextWidth) maxTextWidth = w;
        }
        targetWidth = Math.max(MIN_WIDTH, maxTextWidth + 24);
        targetHeight = Math.max(MIN_HEIGHT, 8 + (textEntries.size() + 1) * (font.lineHeight + 2) + 8);
    }

    @Override
    public void tick() {
        if (isOpen) {
            currentWidth = approach(currentWidth, targetWidth, ANIMATION_SPEED);
            currentHeight = approach(currentHeight, targetHeight, ANIMATION_SPEED);
        } else {
            currentWidth = approach(currentWidth, MIN_WIDTH, ANIMATION_SPEED);
            currentHeight = approach(currentHeight, MIN_HEIGHT, ANIMATION_SPEED);
        }
    }

    private static double approach(double current, double target, double speed) {
        if (current < target) return Math.min(current + speed, target);
        if (current > target) return Math.max(current - speed, target);
        return target;
    }

    @Override
    public void drawBackground(float partialTicks) {
        // Drawing is done in drawWithGraphics — elements need a GuiGraphics param
    }

    /** Draw this ledger. Called by GuiBC8 after rendering. */
    public void drawWithGraphics(GuiGraphics graphics) {
        int x = (int) getX();
        int y = (int) getY();
        int w = (int) currentWidth;
        int h = (int) currentHeight;

        if (w <= 0 || h <= 0) return;

        // Draw background rectangle
        int bgColour = (0xCC << 24) | (colour & 0xFFFFFF);
        graphics.fill(x, y, x + w, y + h, bgColour);

        // Draw border
        int borderColour = (0xFF << 24) | (colour & 0xFFFFFF);
        graphics.fill(x, y, x + w, y + 1, borderColour);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColour);
        graphics.fill(x, y, x + 1, y + h, borderColour);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColour);

        // Only draw text if expanded enough
        if (currentWidth > MIN_WIDTH + 10) {
            Font font = Minecraft.getInstance().font;
            int textX = x + 4;
            int textY = y + 4;

            // Draw title
            graphics.drawString(font, title, textX, textY, 0xFFFFFF | (colour & 0xFFFF00));
            textY += font.lineHeight + 2;

            // Draw text entries
            for (TextEntry entry : textEntries) {
                if (textY + font.lineHeight > y + currentHeight - 4) break;
                String text = entry.getText();
                graphics.drawString(font, text, textX, textY, entry.colour | 0xFF000000);
                textY += font.lineHeight + 2;
            }
        }
    }

    /** Toggle the ledger open/closed when clicked. */
    public boolean handleClick(double mouseX, double mouseY) {
        if (contains(mouseX, mouseY)) {
            isOpen = !isOpen;
            if (isOpen) {
                calculateMaxSize();
            }
            return true;
        }
        return false;
    }

    @Override
    public double getX() {
        if (expandPositive) {
            return posX;
        } else {
            return posX - currentWidth;
        }
    }

    @Override
    public double getY() {
        return posY;
    }

    @Override
    public double getWidth() {
        return currentWidth;
    }

    @Override
    public double getHeight() {
        return currentHeight;
    }

    /** Represents a single text entry in the ledger. */
    public static class TextEntry {
        public final String staticText;
        public final Supplier<String> dynamicText;
        public final int colour;
        public boolean dropShadow = false;

        public TextEntry(String staticText, Supplier<String> dynamicText, int colour) {
            this.staticText = staticText;
            this.dynamicText = dynamicText;
            this.colour = colour;
        }

        public TextEntry setDropShadow(boolean shadow) {
            this.dropShadow = shadow;
            return this;
        }

        public String getText() {
            if (dynamicText != null) return dynamicText.get();
            return staticText != null ? staticText : "";
        }
    }
}
