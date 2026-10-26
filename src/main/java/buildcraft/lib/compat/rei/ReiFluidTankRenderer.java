/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

import javax.annotation.Nullable;

import me.shedaniel.math.Rectangle;
//? if >=26.1 {
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?}
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.common.entry.EntryStack;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.gui.elem.GuiElementFluidTank;

/**
 * Draws a REI fluid slot as a BuildCraft tank: the fluid's still sprite tiled over the whole slot, the way JEI's fluid
 * renderer and BuildCraft's own GUI tanks draw it. REI's fluid renderer from 21.9 on looks the sprite up and then
 * draws nothing, so without this every tank shows empty there. The tooltip (name and amount) stays REI's own.
 *
 * @param <T> the entry's value type (architectury's fluid stack; never read here)
 */
final class ReiFluidTankRenderer<T> implements EntryRenderer<T> {
    private final FluidStack fluid;
    private final EntryStack<T> plain;

    /** @param plain the same entry without this renderer, whose default renderer supplies the tooltip */
    ReiFluidTankRenderer(FluidStack fluid, EntryStack<T> plain) {
        this.fluid = fluid;
        this.plain = plain;
    }

    @Override
    public void render(EntryStack<T> entry, GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY,
            float delta) {
        GuiElementFluidTank.drawTiledFluid(new BCGraphics(graphics), fluid, bounds.x, bounds.y, bounds.width,
                bounds.height);
    }

    @Override
    @Nullable
    public Tooltip getTooltip(EntryStack<T> entry, TooltipContext context) {
        return plain.getRenderer().getTooltip(entry, context);
    }
}
