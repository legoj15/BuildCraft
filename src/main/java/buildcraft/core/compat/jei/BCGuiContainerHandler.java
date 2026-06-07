/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.renderer.Rect2i;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.ledger.Ledger_Neptune;

import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IClickableIngredient;

/**
 * Tells JEI about BuildCraft GUIs: the bounding rectangles of open ledgers
 * (so the ingredient list is pushed out of the way) and the fluid currently
 * shown in any {@link GuiElementFluidTank} (so JEI's R/U recipe lookup keys
 * accept hover-over-a-tank as a fluid focus).
 */
public class BCGuiContainerHandler implements IGuiContainerHandler<GuiBC8<?>> {

    @Override
    public List<Rect2i> getGuiExtraAreas(GuiBC8<?> screen) {
        List<Rect2i> areas = new ArrayList<>();
        for (IGuiElement element : screen.mainGui.shownElements) {
            if (element instanceof Ledger_Neptune ledger) {
                int x = (int) ledger.getX();
                int y = (int) ledger.getY();
                int w = (int) Math.ceil(ledger.getWidth());
                int h = (int) Math.ceil(ledger.getHeight());
                if (w > 0 && h > 0) {
                    areas.add(new Rect2i(x, y, w, h));
                }
            }
        }
        return areas;
    }

    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
            IClickableIngredientFactory builder,
            GuiBC8<?> screen,
            double mouseX,
            double mouseY
    ) {
        for (IGuiElement element : screen.mainGui.shownElements) {
            if (!(element instanceof GuiElementFluidTank tankElem)) continue;

            double x = tankElem.getX();
            double y = tankElem.getY();
            double w = tankElem.getWidth();
            double h = tankElem.getHeight();
            if (mouseX < x || mouseY < y || mouseX >= x + w || mouseY >= y + h) continue;

            BCFluidTank tank = tankElem.getTank();
            if (tank == null || tank.size() == 0) continue;

            FluidStack stack = tank.getFluidStack(0);
            long amount = tank.getAmountMb(0);
            if (stack.isEmpty() || amount <= 0) continue;

            return builder.createBuilder(NeoForgeTypes.FLUID_STACK, stack)
                    .buildWithArea((int) x, (int) y, (int) Math.ceil(w), (int) Math.ceil(h));
        }
        return Optional.empty();
    }
}
