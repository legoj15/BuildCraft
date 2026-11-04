/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;

import net.minecraft.world.item.crafting.ShapelessRecipe;

import buildcraft.transport.recipe.DyedPipeRecipe;
import buildcraft.transport.recipe.HiddenShapelessRecipe;
import buildcraft.transport.recipe.PipePaintRecipe;
import buildcraft.platform.Platform;
import buildcraft.platform.registry.BCRegistrar;

/** Custom crafting-recipe serializers for the Transport subsystem. */
public class BCTransportRecipeSerializers {
    public static final BCRegistrar<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            BCRegistrar.create(Registries.RECIPE_SERIALIZER, BCTransport.MODID);

    public static final Supplier<RecipeSerializer<PipePaintRecipe>> PIPE_PAINT =
            RECIPE_SERIALIZERS.register("pipe_paint", () -> PipePaintRecipe.SERIALIZER);

    public static final Supplier<RecipeSerializer<DyedPipeRecipe>> DYED_PIPE =
            RECIPE_SERIALIZERS.register("dyed_pipe", () -> DyedPipeRecipe.SERIALIZER);

    /** Shapeless crafting that is invisible to the vanilla recipe book but still shown in JEI — used by the
     *  kinesis&rarr;transport pipe "undo" recipes. */
    public static final Supplier<RecipeSerializer<ShapelessRecipe>> HIDDEN_SHAPELESS =
            RECIPE_SERIALIZERS.register("hidden_shapeless", () -> HiddenShapelessRecipe.SERIALIZER);

    /** The recipe condition that drops the RF pipe recipes (forward crafts + reverse downgrades) when
     *  {@code disableRfPipe} is set; see {@link BCTransportConfig#rfPipeRecipesEnabled}. Conditions filter
     *  recipes at datapack load (server-side, synced to clients). Referenced from the recipe JSON as
     *  {@code buildcraftunofficial:rf_enabled}. */
    public static final Identifier RF_ENABLED = Identifier.fromNamespaceAndPath(BCTransport.MODID, "rf_enabled");

    public static void init() {
        RECIPE_SERIALIZERS.subscribe();
        Platform.data().registerCondition(RF_ENABLED, BCTransportConfig::rfPipeRecipesEnabled);
    }
}
