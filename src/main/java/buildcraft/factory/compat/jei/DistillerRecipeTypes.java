/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import mezz.jei.api.recipe.types.IRecipeType;

import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;

/**
 * Holds the JEI {@link IRecipeType} for the distiller. Each registered
 * {@link IDistillationRecipe} maps 1:1 to a JEI entry — no wrapper record is
 * needed because the recipe interface already carries every field the
 * category needs (one input fluid, two output fluids, MJ cost).
 */
public final class DistillerRecipeTypes {
    public static final IRecipeType<IDistillationRecipe> DISTILLER = IRecipeType.create(
            "buildcraftunofficial", "distiller", IDistillationRecipe.class);

    private DistillerRecipeTypes() {}
}
