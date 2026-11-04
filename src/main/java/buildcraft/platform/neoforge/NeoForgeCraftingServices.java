/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import buildcraft.platform.CraftingServices;

/** {@link CraftingServices} on NeoForge: {@link DataComponentIngredient}. */
public final class NeoForgeCraftingServices implements CraftingServices {
    @Override
    public Ingredient componentsIngredient(ItemStack stack) {
        // Pass the stack's *patch* (its real overrides) rather than the ItemStack-shaped overload: that one serialises
        // every default component too, and a stack rebuilt from such an ingredient then fails PatchedDataComponentMap
        // equality against the canonical stack (recipe-viewer lookups stop finding the recipe).
        return DataComponentIngredient.of(false,
            //? if >=26.1 {
            stack.getComponentsPatch(), stack.getItem());
            //?} else {
            /*stack.getComponents(), stack.getItem());*/
            //?}
    }

    //? if >=1.21.10 {
    @Override
    public net.minecraft.world.item.crafting.RecipeMap recipeMap(
            net.minecraft.world.item.crafting.RecipeManager manager) {
        return manager.recipeMap();
    }
    //?}
}
//?}
