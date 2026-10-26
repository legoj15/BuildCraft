/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

import buildcraft.robotics.compat.viewer.IntegrationRecipeEntry;

/** Holds the JEI recipe type for the Integration Table; every entry is an {@link IntegrationRecipeEntry}. JEI 20+
 *  (the {@code >=1.21.10} nodes) deprecated {@code RecipeType} for removal in favour of {@code IRecipeType}; the
 *  1.21.1 node's JEI 19.x predates {@code IRecipeType}, so it keeps the class. */
public final class IntegrationRecipeJeiTypes {
    //? if >=1.21.10 {
    public static final IRecipeType<IntegrationRecipeEntry> INTEGRATION = IRecipeType.create(
            "buildcraftunofficial", "integration_table", IntegrationRecipeEntry.class);
    //?} else {
    /*public static final RecipeType<IntegrationRecipeEntry> INTEGRATION = RecipeType.create(
            "buildcraftunofficial", "integration_table", IntegrationRecipeEntry.class);*/
    //?}

    private IntegrationRecipeJeiTypes() {}
}
