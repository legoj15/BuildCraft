/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;

/**
 * An item tooltip about to be drawn ({@link ClientHooks#onTooltipPre}). The position is the one handed to the
 * positioner, not the tooltip's final origin: run {@link #positioner()} for that.
 */
public interface BCTooltipPreContext {

    /** The stack the tooltip describes. */
    ItemStack stack();

    //? if >=26.1 {
    /** The GUI graphics the tooltip is drawn with. */
    net.minecraft.client.gui.GuiGraphicsExtractor graphics();
    //?} else {
    /*/^* The GUI graphics the tooltip is drawn with. *^/
    net.minecraft.client.gui.GuiGraphics graphics();*/
    //?}

    /** The x the positioner is given (usually the mouse x). */
    int x();

    /** The y the positioner is given (usually the mouse y). */
    int y();

    int screenWidth();

    int screenHeight();

    Font font();

    /** The tooltip's lines and components, in draw order. */
    List<ClientTooltipComponent> components();

    ClientTooltipPositioner positioner();
}
