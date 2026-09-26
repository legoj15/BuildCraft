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

/**
 * Holds the JEI recipe type for the heat exchanger ({@code IRecipeType} on the
 * JEI 20+ nodes, where {@code RecipeType} is deprecated for removal; 1.21.1's
 * JEI 19.x predates it). There is a single type because each JEI recipe is a
 * paired (heatable + coolable) operation — see {@link HeatExchangerRecipePair}.
 */
public final class HeatExchangerRecipeTypes {
    //? if >=1.21.10 {
    public static final IRecipeType<HeatExchangerRecipePair> PAIR = IRecipeType.create(
            "buildcraftunofficial", "heat_exchanger", HeatExchangerRecipePair.class);
    //?} else {
    /*public static final RecipeType<HeatExchangerRecipePair> PAIR = RecipeType.create(
            "buildcraftunofficial", "heat_exchanger", HeatExchangerRecipePair.class);*/
    //?}

    private HeatExchangerRecipeTypes() {}
}
