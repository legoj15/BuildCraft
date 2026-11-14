/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.renderer.Rect2i;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.ledger.Ledger_Neptune;

import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.runtime.IClickableIngredient;

/**
 * Tells JEI about BuildCraft GUIs: the bounding rectangles of open ledgers
 * (so the ingredient list is pushed out of the way) and the fluid currently
 * shown in any {@link GuiElementFluidTank} (so JEI's R/U recipe lookup keys
 * accept hover-over-a-tank as a fluid focus).
 */
public class BCGuiContainerHandler implements IGuiContainerHandler<GuiBC8<?>> {
    /** JEI's fluid ingredient type for this loader, so a tank's fluid crosses without naming the loader's stack. */
    private final IPlatformFluidHelper<?> fluidHelper;

    public BCGuiContainerHandler(IPlatformFluidHelper<?> fluidHelper) {
        this.fluidHelper = fluidHelper;
    }


    @Override
    public List<Rect2i> getGuiExtraAreas(GuiBC8<?> screen) {
        List<Rect2i> areas = new ArrayList<>();
        for (IGuiElement element : screen.mainGui.shownElements) {
            if (element instanceof Ledger_Neptune ledger) {
                // Left edges floor and right edges ceil, so the rectangle always covers the whole drawn panel.
                int left = (int) Math.floor(ledger.getX());
                int top = (int) Math.floor(ledger.getY());
                int right = (int) Math.ceil(ledger.getX() + ledger.getWidth());
                int bottom = (int) Math.ceil(ledger.getY() + ledger.getHeight());
                // JEI 26.2 and 26.3 count an exclusion area only when 0 < x, and a ledger at its width cap starts
                // at x = 0, so it would be ignored. Start at 1 instead; the right edge, which is what pushes the
                // ingredient list, stays where it is.
                left = Math.max(1, left);
                int w = right - left;
                int h = bottom - top;
                if (w > 0 && h > 0) {
                    areas.add(new Rect2i(left, top, w, h));
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

            BCFluidStack stack = tank.getFluidStack(0);
            long amount = tank.getAmountMb(0);
            if (stack.isEmpty() || amount <= 0) continue;

            return clickable(builder, fluidHelper, stack,
                    (int) x, (int) y, (int) Math.ceil(w), (int) Math.ceil(h));
        }
        return Optional.empty();
    }

    private static <T> Optional<IClickableIngredient<T>> clickable(IClickableIngredientFactory builder,
            IPlatformFluidHelper<T> helper, BCFluidStack stack, int x, int y, int w, int h) {
        T ingredient = helper.create(stack.getFluidHolder(), stack.getAmount(), stack.getComponentsPatch());
        return builder.createBuilder(helper.getFluidIngredientType(), ingredient).buildWithArea(x, y, w, h);
    }
}
