/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;

import buildcraft.lib.compat.viewer.FluidContainerAliases;

/** JEI's half of {@link FluidContainerAliases}: adds a fluid slot's item forms to a recipe layout as invisible
 *  ingredients, so U/R on a bucket or a fragile shard of the fluid finds the recipe. */
public final class JeiFluidAliases {
    private JeiFluidAliases() {}

    /** Adds every item form of {@code stack} to {@code builder} as invisible ingredients with the given role. No-op for
     *  empty fluids. */
    public static void addAliases(IRecipeLayoutBuilder builder, FluidStack stack, RecipeIngredientRole role) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        IIngredientAcceptor<?> slot = builder.addInvisibleIngredients(role);
        for (ItemStack alias : FluidContainerAliases.itemForms(stack)) {
            //? if >=1.21.10 {
            slot.add(alias);
            //?} else {
            /*slot.addItemStack(alias);*/
            //?}
        }
    }
}
