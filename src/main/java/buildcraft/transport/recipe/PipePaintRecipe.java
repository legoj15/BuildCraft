/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import buildcraft.transport.BCTransportItems;
import buildcraft.transport.item.ItemPipeHolder;

/** Recolours or bleaches pipes in the crafting grid: any number (1–8) of pipes of a single type plus
 *  one dye recolours them ({@link BCTransportItems#PIPE_COLOUR}), or plus one water bucket bleaches them
 *  back to unpainted. Output count equals the number of pipes supplied. Ports the 1.12.2 pipe-colouring
 *  recipe. Stateless — a single instance suffices. */
public class PipePaintRecipe extends CustomRecipe {
    public static final MapCodec<PipePaintRecipe> MAP_CODEC = MapCodec.unit(PipePaintRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PipePaintRecipe> STREAM_CODEC =
            StreamCodec.unit(new PipePaintRecipe());
    public static final RecipeSerializer<PipePaintRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    /** Parsed grid contents: the pipe item, how many were supplied, and the target colour
     *  ({@code null} = bleach to unpainted). */
    private record Parsed(Item pipe, int count, DyeColor colour) {}

    /** Validates the grid and extracts the paint operation, or returns {@code null} if the grid is not a
     *  valid paint/bleach: it must hold ≥1 pipe (all the same type) and exactly one modifier — a dye
     *  (recolour) or a water bucket (bleach) — and nothing else. */
    private static Parsed parse(CraftingInput input) {
        Item pipe = null;
        int pipeCount = 0;
        DyeColor dye = null;
        boolean bleach = false;
        int modifiers = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ItemPipeHolder) {
                if (pipe == null) {
                    pipe = stack.getItem();
                } else if (pipe != stack.getItem()) {
                    return null; // mixed pipe types
                }
                pipeCount++;
                continue;
            }
            DyeColor colour = stack.get(DataComponents.DYE);
            if (colour != null) {
                dye = colour;
                modifiers++;
                continue;
            }
            if (stack.is(Items.WATER_BUCKET)) {
                bleach = true;
                modifiers++;
                continue;
            }
            return null; // foreign item
        }

        if (pipe == null || pipeCount == 0 || modifiers != 1) {
            return null;
        }
        return new Parsed(pipe, pipeCount, bleach ? null : dye);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return parse(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Parsed parsed = parse(input);
        if (parsed == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = new ItemStack(parsed.pipe(), parsed.count());
        if (parsed.colour() == null) {
            out.remove(BCTransportItems.PIPE_COLOUR.get());
        } else {
            out.set(BCTransportItems.PIPE_COLOUR.get(), parsed.colour());
        }
        return out;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        // Keep vanilla crafting remainders, and make a bleaching water bucket leave an empty bucket.
        NonNullList<ItemStack> remaining = CraftingRecipe.defaultCraftingReminder(input);
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).is(Items.WATER_BUCKET) && remaining.get(i).isEmpty()) {
                remaining.set(i, new ItemStack(Items.BUCKET));
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<PipePaintRecipe> getSerializer() {
        return SERIALIZER;
    }
}
