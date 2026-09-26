/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

/**
 * Holds the JEI recipe type for the Assembly Table ({@code IRecipeType} on the
 * JEI 20+ nodes, where {@code RecipeType} is deprecated for removal; 1.21.1's
 * JEI 19.x predates it). Every entry in the category — chipsets, gates, lenses,
 * plugs, gate copier, and the unified cycling facade entry — is an
 * {@link AssemblyRecipeJei}.
 */
public final class AssemblyRecipeJeiTypes {
    //? if >=1.21.10 {
    public static final IRecipeType<AssemblyRecipeJei> ASSEMBLY = IRecipeType.create(
            "buildcraftunofficial", "assembly_table", AssemblyRecipeJei.class);
    //?} else {
    /*public static final RecipeType<AssemblyRecipeJei> ASSEMBLY = RecipeType.create(
            "buildcraftunofficial", "assembly_table", AssemblyRecipeJei.class);*/
    //?}

    private AssemblyRecipeJeiTypes() {}
}
