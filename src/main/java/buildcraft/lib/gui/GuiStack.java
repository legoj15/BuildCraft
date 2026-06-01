/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import buildcraft.lib.gui.BCGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * An {@link ISimpleDrawable} that renders an {@link ItemStack} at a given position.
 * <p>
 * In 1.21, item rendering requires a {@link BCGraphics}; this class threads it
 * in via a static field set by the surrounding screen (same pattern as
 * {@link buildcraft.lib.client.guide.font.MinecraftFont} and {@link GuiIcon}).
 */
public class GuiStack implements ISimpleDrawable {
    private final ItemStack stack;

    /** The BCGraphics context — set by the surrounding Screen each frame. */
    private static BCGraphics currentGraphics;

    /** Set the BCGraphics context for all GuiStack rendering. */
    public static void setGuiGraphics(BCGraphics graphics) {
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
        // fakeItem (null holder) keeps dynamic models (clock/compass) at their static frame.
        currentGraphics.fakeItem(stack, (int) x, (int) y);
    }
}
