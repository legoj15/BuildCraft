/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * An {@link ISimpleDrawable} that renders an {@link ItemStack} at a given position.
 * <p>
 * In 1.21, item rendering requires a {@link GuiGraphicsExtractor}; this class threads it
 * in via a static field set by the surrounding screen (same pattern as
 * {@link buildcraft.lib.client.guide.font.MinecraftFont} and {@link GuiIcon}).
 */
public class GuiStack implements ISimpleDrawable {
    private final ItemStack stack;

    /** The GuiGraphicsExtractor context — set by the surrounding Screen each frame. */
    private static GuiGraphicsExtractor currentGraphics;

    /** Set the GuiGraphicsExtractor context for all GuiStack rendering. */
    public static void setGuiGraphics(GuiGraphicsExtractor graphics) {
        currentGraphics = graphics;
    }

    public GuiStack(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public void drawAt(double x, double y) {
        if (currentGraphics == null || stack == null || stack.isEmpty()) {
            return;
        }
        currentGraphics.item(stack, (int) x, (int) y);
    }
}
