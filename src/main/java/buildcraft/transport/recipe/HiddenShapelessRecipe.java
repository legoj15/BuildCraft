/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * A shapeless crafting recipe that behaves <em>exactly</em> like a vanilla {@code minecraft:crafting_shapeless}
 * (identical JSON shape, matching and result) but is flagged {@link #isSpecial() special} so it is never added to
 * the vanilla recipe book. The recipe book is unlock-driven, and vanilla auto-learns a recipe on craft only when
 * it is <em>not</em> special (see {@code RecipeCraftingHolder.awardUsedRecipes}); marking it special closes that
 * path, and dropping it from the unlock advancement closes the other. It nonetheless remains a real
 * {@code RecipeType.CRAFTING} recipe with the inherited (non-empty) {@code display()}/{@code getIngredients()}, so
 * JEI still lists it.
 *
 * <p>BuildCraft uses it for the kinesis&rarr;transport "undo" conversions (power pipe back into an item pipe):
 * valid crafts, but pure clutter in the recipe book.
 *
 * <p>Implementation: subclasses {@link ShapelessRecipe} and reuses vanilla's shapeless codecs verbatim (via
 * {@code xmap}/{@code map} over a copy constructor), so there is no bespoke serialization format to keep in sync
 * across MC lines — only the constructor wiring differs per node.
 */
public class HiddenShapelessRecipe extends ShapelessRecipe {

    //? if >=26.1 {
    /** Re-wraps a freshly decoded vanilla shapeless recipe as a hidden one. */
    public HiddenShapelessRecipe(ShapelessRecipe base) {
        super(new net.minecraft.world.item.crafting.Recipe.CommonInfo(base.showNotification()),
                new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(base.category(), base.group()),
                base.result, base.ingredients);
    }

    public static final MapCodec<ShapelessRecipe> MAP_CODEC =
            ShapelessRecipe.MAP_CODEC.<ShapelessRecipe>xmap(HiddenShapelessRecipe::new, r -> r);
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> STREAM_CODEC =
            ShapelessRecipe.STREAM_CODEC.<ShapelessRecipe>map(HiddenShapelessRecipe::new, r -> r);
    public static final RecipeSerializer<ShapelessRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);
    //?} elif >=1.21.10 {
    /*public HiddenShapelessRecipe(ShapelessRecipe base) {
        super(base.group(), base.category(), base.result, base.ingredients);
    }

    public static final MapCodec<ShapelessRecipe> MAP_CODEC =
            RecipeSerializer.SHAPELESS_RECIPE.codec().<ShapelessRecipe>xmap(HiddenShapelessRecipe::new, r -> r);
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> STREAM_CODEC =
            RecipeSerializer.SHAPELESS_RECIPE.streamCodec().<ShapelessRecipe>map(HiddenShapelessRecipe::new, r -> r);
    public static final RecipeSerializer<ShapelessRecipe> SERIALIZER = new Serializer();

    public static final class Serializer implements RecipeSerializer<ShapelessRecipe> {
        @Override public MapCodec<ShapelessRecipe> codec() { return MAP_CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> streamCodec() { return STREAM_CODEC; }
    }*/
    //?} else {
    /*// 1.21.1: pre-recipe-display book; the group getter is still getGroup() and ingredients are a NonNullList,
    // but the vanilla codecs (reached via the registered serializer) absorb those differences for us.
    public HiddenShapelessRecipe(ShapelessRecipe base) {
        super(base.getGroup(), base.category(), base.result, base.ingredients);
    }

    public static final MapCodec<ShapelessRecipe> MAP_CODEC =
            RecipeSerializer.SHAPELESS_RECIPE.codec().<ShapelessRecipe>xmap(HiddenShapelessRecipe::new, r -> r);
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> STREAM_CODEC =
            RecipeSerializer.SHAPELESS_RECIPE.streamCodec().<ShapelessRecipe>map(HiddenShapelessRecipe::new, r -> r);
    public static final RecipeSerializer<ShapelessRecipe> SERIALIZER = new Serializer();

    public static final class Serializer implements RecipeSerializer<ShapelessRecipe> {
        @Override public MapCodec<ShapelessRecipe> codec() { return MAP_CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> streamCodec() { return STREAM_CODEC; }
    }*/
    //?}

    /** Keeps the recipe out of the vanilla recipe book (and prevents auto-learning it on craft). */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<ShapelessRecipe> getSerializer() {
        return SERIALIZER;
    }
}
