/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;

import buildcraft.silicon.recipe.FacadeSwapRecipe;
import buildcraft.platform.registry.BCRegistrar;

/** Custom crafting-recipe serializers for the Silicon subsystem. */
public class BCSiliconRecipeSerializers {
    public static final BCRegistrar<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            BCRegistrar.create(Registries.RECIPE_SERIALIZER, BCSilicon.MODID);

    public static final Supplier<RecipeSerializer<FacadeSwapRecipe>> FACADE_SWAP =
            RECIPE_SERIALIZERS.register("facade_swap", () -> FacadeSwapRecipe.SERIALIZER);

    public static void init(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.subscribe();
    }
}
