/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;

public class GuiUtil {

    /** Returns a GuiRectangle centered on the current screen. */
    public static IGuiArea moveRectangleToCentre(GuiRectangle rect) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        double x = (screenWidth - rect.getWidth()) / 2.0;
        double y = (screenHeight - rect.getHeight()) / 2.0;
        return new GuiRectangle(x, y, rect.getWidth(), rect.getHeight());
    }

    /** Enables GL scissoring for a region. Returns an AutoCloseable that disables it. */
    public static AutoGlScissor scissor(GuiGraphics graphics, double x, double y, double w, double h) {
        graphics.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
        return new AutoGlScissor(graphics);
    }

    public static class AutoGlScissor implements AutoCloseable {
        private final GuiGraphics graphics;

        public AutoGlScissor(GuiGraphics graphics) {
            this.graphics = graphics;
        }

        @Override
        public void close() {
            graphics.disableScissor();
        }
    }
}
