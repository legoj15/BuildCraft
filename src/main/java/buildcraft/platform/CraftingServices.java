/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Recipe-ingredient types the loader supplies, and the server's recipes by type. */
public interface CraftingServices {
    /** An ingredient matching the stack's item and its overridden data components (not the default ones). */
    Ingredient componentsIngredient(ItemStack stack);

    //? if >=1.21.10 {
    /** The manager's recipes by type (NeoForge patches an accessor in; vanilla keeps the map private). */
    net.minecraft.world.item.crafting.RecipeMap recipeMap(net.minecraft.world.item.crafting.RecipeManager manager);
    //?}
}
