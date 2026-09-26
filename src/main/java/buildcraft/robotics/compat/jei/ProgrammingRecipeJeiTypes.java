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

/** Holds the JEI recipe type for the Programming Table; every entry is a {@link ProgrammingRecipeJei}. JEI 20+
 *  (the {@code >=1.21.10} nodes) deprecated {@code RecipeType} for removal in favour of {@code IRecipeType}; the
 *  1.21.1 node's JEI 19.x predates {@code IRecipeType}, so it keeps the class. */
public final class ProgrammingRecipeJeiTypes {
    //? if >=1.21.10 {
    public static final IRecipeType<ProgrammingRecipeJei> PROGRAMMING = IRecipeType.create(
            "buildcraftunofficial", "programming_table", ProgrammingRecipeJei.class);
    //?} else {
    /*public static final RecipeType<ProgrammingRecipeJei> PROGRAMMING = RecipeType.create(
            "buildcraftunofficial", "programming_table", ProgrammingRecipeJei.class);*/
    //?}

    private ProgrammingRecipeJeiTypes() {}
}
