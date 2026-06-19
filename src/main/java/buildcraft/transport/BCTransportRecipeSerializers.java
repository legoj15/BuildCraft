/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.minecraft.world.item.crafting.ShapelessRecipe;

import buildcraft.transport.recipe.DyedPipeRecipe;
import buildcraft.transport.recipe.HiddenShapelessRecipe;
import buildcraft.transport.recipe.PipePaintRecipe;
import buildcraft.transport.recipe.RfEnabledCondition;

/** Custom crafting-recipe serializers for the Transport subsystem. */
public class BCTransportRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BCTransport.MODID);

    public static final Supplier<RecipeSerializer<PipePaintRecipe>> PIPE_PAINT =
            RECIPE_SERIALIZERS.register("pipe_paint", () -> PipePaintRecipe.SERIALIZER);

    public static final Supplier<RecipeSerializer<DyedPipeRecipe>> DYED_PIPE =
            RECIPE_SERIALIZERS.register("dyed_pipe", () -> DyedPipeRecipe.SERIALIZER);

    /** Shapeless crafting that is invisible to the vanilla recipe book but still shown in JEI — used by the
     *  kinesis&rarr;transport pipe "undo" recipes. */
    public static final Supplier<RecipeSerializer<ShapelessRecipe>> HIDDEN_SHAPELESS =
            RECIPE_SERIALIZERS.register("hidden_shapeless", () -> HiddenShapelessRecipe.SERIALIZER);

    /** Recipe-condition codecs. Conditions filter recipes at datapack load (server-side, synced to
     *  clients). The registration mechanism is node-uniform across every MC line. */
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, BCTransport.MODID);

    /** Drops the RF pipe recipes (forward crafts + reverse downgrades) when {@code disableRfPipe} is set.
     *  The MJ dynamo / RF engine MJ&lt;-&gt;FE converters are intentionally left craftable. Referenced from the
     *  recipe JSON as {@code buildcraftunofficial:rf_enabled}. */
    public static final Supplier<MapCodec<RfEnabledCondition>> RF_ENABLED =
            CONDITION_CODECS.register("rf_enabled", () -> RfEnabledCondition.CODEC);

    public static void init(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
        CONDITION_CODECS.register(modEventBus);
    }
}
