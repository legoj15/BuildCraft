/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadeInstance;

/** Toggles a facade between its solid and hollow forms: a single facade pluggable placed alone in the
 *  crafting grid yields the same facade with {@link FacadeInstance#isHollow} flipped, preserving its
 *  block state(s). Ports the 1.12.2 {@code FacadeSwapRecipe}. Stateless — a single instance suffices. */
public class FacadeSwapRecipe extends CustomRecipe {
    // Stateless: nothing to serialize. The 26.1 stream codec must use a no-op encoder + fresh-instance
    // decoder, NOT StreamCodec.unit(instance) — unit() compares each synced recipe against a captured
    // instance by identity (CustomRecipe has no equals), throwing "Can't encode" during recipe sync on
    // join. The sub-26.1 branches dodge this: their Serializers serialize the CraftingBookCategory field.
    //? if >=26.1 {
    public static final MapCodec<FacadeSwapRecipe> MAP_CODEC = MapCodec.unit(FacadeSwapRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeSwapRecipe> STREAM_CODEC =
            StreamCodec.of((buf, recipe) -> {}, buf -> new FacadeSwapRecipe());
    public static final RecipeSerializer<FacadeSwapRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);
    //?} elif >=1.21.10 {
    /*// Sub-26.1 CustomRecipe is built from a CraftingBookCategory; its Serializer derives the codecs
    // from that one field, so MAP_CODEC/STREAM_CODEC are unneeded here.
    public static final RecipeSerializer<FacadeSwapRecipe> SERIALIZER =
            new CustomRecipe.Serializer<>(FacadeSwapRecipe::new);

    public FacadeSwapRecipe(net.minecraft.world.item.crafting.CraftingBookCategory category) { super(category); }*/
    //?} else {
    /*public static final RecipeSerializer<FacadeSwapRecipe> SERIALIZER =
            new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(FacadeSwapRecipe::new);

    public FacadeSwapRecipe(net.minecraft.world.item.crafting.CraftingBookCategory category) { super(category); }*/
    //?}

    /** Returns the single non-empty stack in the grid, or {@link ItemStack#EMPTY} if there is not
     *  exactly one. */
    private static ItemStack soleStack(CraftingInput input) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (!found.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                found = stack;
            }
        }
        return found;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack sole = soleStack(input);
        return !sole.isEmpty() && sole.getItem() == BCSiliconItems.PLUG_FACADE.get();
    }

    //? if >=26.1 {
    @Override
    public ItemStack assemble(CraftingInput input) {
    //?} else {
    /*@Override
    public ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider provider) {*/
    //?}
        ItemStack sole = soleStack(input);
        if (sole.isEmpty() || sole.getItem() != BCSiliconItems.PLUG_FACADE.get()) {
            return ItemStack.EMPTY;
        }
        FacadeInstance swapped = ItemPluggableFacade.getStates(sole).withSwappedIsHollow();
        return BCSiliconItems.PLUG_FACADE.get().createItemStack(swapped);
    }

    @Override
    public RecipeSerializer<FacadeSwapRecipe> getSerializer() {
        return SERIALIZER;
    }

    //? if <1.21.10 {
    /*@Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1; // a single facade pluggable suffices
    }*/
    //?}
}
