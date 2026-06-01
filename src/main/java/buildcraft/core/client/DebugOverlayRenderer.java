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
import buildcraft.lib.gui.BCGraphics;

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
    public static void render(BCGraphics BCGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.debugEntries == null || !mc.debugEntries.isOverlayVisible()) {
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
        // Try to start at the halfway point, but if the text is too long,
        // shift it up so it doesn't clip off the bottom of the screen.
        int leftHeight = leftLines.size() * lineHeight;
        int leftY = mc.getWindow().getGuiScaledHeight() / 2;
        if (leftY + leftHeight > mc.getWindow().getGuiScaledHeight()) {
            leftY = Math.max(5, mc.getWindow().getGuiScaledHeight() - leftHeight - 5);
        }
        for (String line : leftLines) {
            if (line.isEmpty()) {
                leftY += lineHeight;
                continue;
            }
            // Background
            int width = font.width(line);
            BCGraphics.fill(1, leftY - 1, 2 + width + 1, leftY + font.lineHeight, 0x90505050);
            BCGraphics.text(font, line, 2, leftY, 0xFFE0E0E0, false);
            leftY += lineHeight;
        }

        // Render right side
        int rightHeight = rightLines.size() * lineHeight;
        int rightY = mc.getWindow().getGuiScaledHeight() / 2;
        if (rightY + rightHeight > mc.getWindow().getGuiScaledHeight()) {
            rightY = Math.max(5, mc.getWindow().getGuiScaledHeight() - rightHeight - 5);
        }
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        for (String line : rightLines) {
            if (line.isEmpty()) {
                rightY += lineHeight;
                continue;
            }
            int width = font.width(line);
            int x = screenWidth - 2 - width;
            BCGraphics.fill(x - 1, rightY - 1, x + width + 1, rightY + font.lineHeight, 0x90505050);
            BCGraphics.text(font, line, x, rightY, 0xFFE0E0E0, false);
            rightY += lineHeight;
        }
    }
}
