/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.NonNullList;
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

import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.item.ItemPipeHolder;

/** Recolours or bleaches pipes in the crafting grid: any number (1–8) of pipes of a single type plus a
 *  charged paintbrush recolours them ({@link BCTransportItems#PIPE_COLOUR}), consuming one brush use per
 *  pipe (so the brush's dye efficiency carries into crafting); or plus a water bucket bleaches them back
 *  to unpainted. Output count equals the number of pipes supplied. The brush is returned with its uses
 *  decremented (reverting to a clean brush when spent); the water bucket is returned empty. Stateless. */
public class PipePaintRecipe extends CustomRecipe {
    public static final MapCodec<PipePaintRecipe> MAP_CODEC = MapCodec.unit(PipePaintRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PipePaintRecipe> STREAM_CODEC =
            StreamCodec.unit(new PipePaintRecipe());
    public static final RecipeSerializer<PipePaintRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    /** Parsed grid contents: the pipe item, how many were supplied, and the target colour
     *  ({@code null} = bleach to unpainted). */
    private record Parsed(Item pipe, int count, DyeColor colour) {}

    /** Validates the grid and extracts the paint operation, or returns {@code null} if it is not a valid
     *  paint/bleach: it must hold ≥1 pipe (all the same type) and exactly one modifier — a <em>charged</em>
     *  paintbrush with enough uses to cover every pipe (recolour), or a water bucket (bleach) — and
     *  nothing else. */
    private static Parsed parse(CraftingInput input) {
        Item pipe = null;
        int pipeCount = 0;
        DyeColor colour = null;
        int brushUses = 0;
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
            if (stack.getItem() instanceof ItemPaintbrush_BC8 brush) {
                ItemPaintbrush_BC8.Brush data = brush.getBrushFromStack(stack);
                if (data.colour == null || data.usesLeft <= 0) {
                    return null; // a clean/empty brush can't paint
                }
                colour = data.colour;
                brushUses = data.usesLeft;
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
        if (!bleach && brushUses < pipeCount) {
            return null; // not enough charge to paint every pipe (one use each)
        }
        return new Parsed(pipe, pipeCount, bleach ? null : colour);
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
        NonNullList<ItemStack> remaining = CraftingRecipe.defaultCraftingReminder(input);
        Parsed parsed = parse(input);
        int painted = parsed != null ? parsed.count() : 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof ItemPaintbrush_BC8) {
                // Charge one use per pipe painted; a spent brush comes back clean.
                remaining.set(i, ItemPaintbrush_BC8.withUsesConsumed(stack, painted));
            } else if (stack.is(Items.WATER_BUCKET) && remaining.get(i).isEmpty()) {
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
