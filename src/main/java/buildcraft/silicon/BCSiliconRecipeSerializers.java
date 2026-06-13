/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.silicon.recipe.FacadeSwapRecipe;

/** Custom crafting-recipe serializers for the Silicon subsystem. */
public class BCSiliconRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BCSilicon.MODID);

    public static final Supplier<RecipeSerializer<FacadeSwapRecipe>> FACADE_SWAP =
            RECIPE_SERIALIZERS.register("facade_swap", () -> FacadeSwapRecipe.SERIALIZER);

    public static void init(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
