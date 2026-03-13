/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the BuildCraft debug overlay on the F3 screen.
 * <p>
 * Registered as a GUI layer via {@link net.neoforged.neoforge.client.event.RegisterGuiLayersEvent}.
 * Only renders when the debug screen is visible.
 */
public class DebugOverlayRenderer {

    /**
     * Called by the GUI layer system every frame.
     * Renders debug info lines on the left and right sides of the screen,
     * below the vanilla debug text.
     */
    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }

        List<String> leftLines = DebugOverlayHelper.getLeftLines();
        List<String> rightLines = DebugOverlayHelper.getRightLines();

        if (leftLines.isEmpty() && rightLines.isEmpty()) {
            return;
        }

        Font font = mc.font;
        int lineHeight = font.lineHeight + 2; // 9 + 2 = 11, matching vanilla debug spacing

        // Render left side — start below vanilla debug text
        // Vanilla uses roughly the bottom half area, so we start at a reasonable offset
        // We'll render at the bottom of the left side, going up from 2/3 of screen height
        int leftY = mc.getWindow().getGuiScaledHeight() * 2 / 3;
        for (String line : leftLines) {
            if (line.isEmpty()) {
                leftY += lineHeight;
                continue;
            }
            // Background
            int width = font.width(line);
            guiGraphics.fill(1, leftY - 1, 2 + width + 1, leftY + font.lineHeight, 0x90505050);
            guiGraphics.drawString(font, line, 2, leftY, 0xFFE0E0E0, false);
            leftY += lineHeight;
        }

        // Render right side
        int rightY = mc.getWindow().getGuiScaledHeight() * 2 / 3;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        for (String line : rightLines) {
            if (line.isEmpty()) {
                rightY += lineHeight;
                continue;
            }
            int width = font.width(line);
            int x = screenWidth - 2 - width;
            guiGraphics.fill(x - 1, rightY - 1, x + width + 1, rightY + font.lineHeight, 0x90505050);
            guiGraphics.drawString(font, line, x, rightY, 0xFFE0E0E0, false);
            rightY += lineHeight;
        }
    }
}
