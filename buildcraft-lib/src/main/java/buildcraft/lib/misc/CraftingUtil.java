/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public final class CraftingUtil {

    private CraftingUtil() {
    }

    @Nullable
    public static RecipeHolder<CraftingRecipe> findMatchingRecipe(CraftingInput input, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.recipeAccess()
            .getRecipeFor(RecipeType.CRAFTING, input, serverLevel)
            .orElse(null);
    }
}

