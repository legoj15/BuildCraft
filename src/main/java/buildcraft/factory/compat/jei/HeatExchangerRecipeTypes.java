/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import mezz.jei.api.recipe.RecipeType;

/**
 * Holds the JEI {@link RecipeType} for the heat exchanger. There is a single
 * type because each JEI recipe is a paired (heatable + coolable) operation —
 * see {@link HeatExchangerRecipePair}.
 */
public final class HeatExchangerRecipeTypes {
    public static final RecipeType<HeatExchangerRecipePair> PAIR = RecipeType.create(
            "buildcraftunofficial", "heat_exchanger", HeatExchangerRecipePair.class);

    private HeatExchangerRecipeTypes() {}
}
