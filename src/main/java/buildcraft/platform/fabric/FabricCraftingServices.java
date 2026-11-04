/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import buildcraft.platform.CraftingServices;

/^* {@link CraftingServices} on Fabric: Fabric API's component-matching custom ingredient. ^/
public final class FabricCraftingServices implements CraftingServices {
    @Override
    public Ingredient componentsIngredient(ItemStack stack) {
        return DefaultCustomIngredients.components(Ingredient.of(stack.getItem()), stack.getComponentsPatch());
    }
}
*///?}
