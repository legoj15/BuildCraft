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
    public static final MapCodec<FacadeSwapRecipe> MAP_CODEC = MapCodec.unit(FacadeSwapRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeSwapRecipe> STREAM_CODEC =
            StreamCodec.unit(new FacadeSwapRecipe());
    public static final RecipeSerializer<FacadeSwapRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

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

    @Override
    public ItemStack assemble(CraftingInput input) {
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
}
