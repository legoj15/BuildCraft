/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;

/**
 * Holds the JEI recipe type for the distiller ({@code IRecipeType} on the
 * JEI 20+ nodes, where {@code RecipeType} is deprecated for removal; 1.21.1's
 * JEI 19.x predates it). Each registered {@link IDistillationRecipe} maps 1:1
 * to a JEI entry — no wrapper record is needed because the recipe interface
 * already carries every field the category needs (one input fluid, two output
 * fluids, MJ cost).
 */
public final class DistillerRecipeTypes {
    //? if >=1.21.10 {
    public static final IRecipeType<IDistillationRecipe> DISTILLER = IRecipeType.create(
            "buildcraftunofficial", "distiller", IDistillationRecipe.class);
    //?} else {
    /*public static final RecipeType<IDistillationRecipe> DISTILLER = RecipeType.create(
            "buildcraftunofficial", "distiller", IDistillationRecipe.class);*/
    //?}

    private DistillerRecipeTypes() {}
}
