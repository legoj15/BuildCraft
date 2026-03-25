/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import net.minecraft.world.item.ItemStack;

/**
 * An {@link ISimpleDrawable} that renders an {@link ItemStack} at a given position.
 * <p>
 * In 1.21, item rendering requires a {@link net.minecraft.client.gui.GuiGraphicsExtractor} context
 * which is not available through the simple {@code drawAt(x, y)} API. This class is currently
 * a no-op stub and will be implemented when GuiGuide passes its GuiGraphicsExtractor context down.
 */
public class GuiStack implements ISimpleDrawable {
    private final ItemStack stack;

    public GuiStack(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public void drawAt(double x, double y) {
        // Rendering stub — in 1.21, item rendering requires GuiGraphicsExtractor.renderItem().
        // Will be implemented when the guide GUI passes its GuiGraphicsExtractor context.
    }
}
