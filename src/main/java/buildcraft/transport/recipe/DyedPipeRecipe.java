/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.recipe;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StainedGlassBlock;

import buildcraft.transport.BCTransportItems;

/** Crafts a pre-dyed pipe in one step: the same {@code [material, glass, material]} pattern as the base
 *  pipe recipes but with <em>stained</em> glass in the centre yields 8 pipes carrying the matching
 *  {@link BCTransportItems#PIPE_COLOUR}. Ports the 16 per-colour variants 1.12.2 registered for every
 *  glass-using pipe. The material→pipe table is maintained here (in Java) rather than as ~224 static
 *  JSONs; if a base pipe recipe's material ingredient changes, the matching entry below must be updated
 *  to match. The pipe-paint recipe ({@link PipePaintRecipe}) remains the general dye-any-pipe path. */
public class DyedPipeRecipe extends CustomRecipe {
    //? if >=26.1 {
    public static final MapCodec<DyedPipeRecipe> MAP_CODEC = MapCodec.unit(DyedPipeRecipe::new);
    // Stateless: nothing to serialize. A no-op encoder + fresh-instance decoder is required here —
    // StreamCodec.unit(instance) compares each synced recipe against a captured instance by identity
    // (CustomRecipe has no equals), throwing "Can't encode" during the recipe-content sync on join.
    public static final StreamCodec<RegistryFriendlyByteBuf, DyedPipeRecipe> STREAM_CODEC =
            StreamCodec.of((buf, recipe) -> {}, buf -> new DyedPipeRecipe());
    public static final RecipeSerializer<DyedPipeRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);
    //?} elif >=1.21.10 {
    /*// Sub-26.1 CustomRecipe is built from a CraftingBookCategory; its Serializer derives the codecs
    // from that one field, so MAP_CODEC/STREAM_CODEC are unneeded here.
    public static final RecipeSerializer<DyedPipeRecipe> SERIALIZER =
            new CustomRecipe.Serializer<>(DyedPipeRecipe::new);

    public DyedPipeRecipe(net.minecraft.world.item.crafting.CraftingBookCategory category) { super(category); }*/
    //?} else {
    /*public static final RecipeSerializer<DyedPipeRecipe> SERIALIZER =
            new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(DyedPipeRecipe::new);

    public DyedPipeRecipe(net.minecraft.world.item.crafting.CraftingBookCategory category) { super(category); }*/
    //?}

    /** One pipe's recipe materials (left/right of the glass; order-insensitive, mirroring the base
     *  shaped recipes) and the pipe item they produce. */
    private record Entry(Ingredient a, Ingredient b, Supplier<? extends ItemLike> pipe) {}

    private record Match(ItemLike pipe, DyeColor colour) {}

    // Built lazily on first craft attempt: tag-backed Ingredients can't be resolved at class-init
    // (item tags aren't bound until datapack load).
    private static volatile List<Entry> entries;

    private static List<Entry> entries() {
        List<Entry> local = entries;
        if (local == null) {
            local = List.of(
                    sym("#minecraft:planks", BCTransportItems.PIPE_WOOD_ITEM),
                    sym("#c:cobblestones/normal", BCTransportItems.PIPE_COBBLE_ITEM),
                    sym("#c:stones", BCTransportItems.PIPE_STONE_ITEM),
                    sym(Items.QUARTZ_BLOCK, BCTransportItems.PIPE_QUARTZ_ITEM),
                    sym("#c:ingots/iron", BCTransportItems.PIPE_IRON_ITEM),
                    sym("#c:ingots/gold", BCTransportItems.PIPE_GOLD_ITEM),
                    sym(Items.CLAY, BCTransportItems.PIPE_CLAY_ITEM),
                    sym("#c:sandstone/blocks", BCTransportItems.PIPE_SANDSTONE_ITEM),
                    sym(Items.OBSIDIAN, BCTransportItems.PIPE_OBSIDIAN_ITEM),
                    sym("#c:gems/diamond", BCTransportItems.PIPE_DIAMOND_ITEM),
                    sym(Items.LAPIS_BLOCK, BCTransportItems.PIPE_LAPIS_ITEM),
                    asym(Ingredient.of(Items.LAPIS_BLOCK), tag("#c:gems/diamond"), BCTransportItems.PIPE_DAIZULI_ITEM),
                    asym(tag("#minecraft:planks"), tag("#c:gems/diamond"), BCTransportItems.PIPE_DIAMOND_WOOD_ITEM),
                    asym(tag("#c:dyes/black"), tag("#c:dusts/redstone"), BCTransportItems.PIPE_VOID_ITEM));
            entries = local;
        }
        return local;
    }

    private static Entry sym(String tagId, Supplier<? extends ItemLike> pipe) {
        Ingredient ing = tag(tagId);
        return new Entry(ing, ing, pipe);
    }

    private static Entry sym(ItemLike item, Supplier<? extends ItemLike> pipe) {
        Ingredient ing = Ingredient.of(item);
        return new Entry(ing, ing, pipe);
    }

    private static Entry asym(Ingredient a, Ingredient b, Supplier<? extends ItemLike> pipe) {
        return new Entry(a, b, pipe);
    }

    private static Ingredient tag(String tagId) {
        TagKey<Item> key = TagKey.create(Registries.ITEM, Identifier.parse(tagId.substring(1)));
        //? if >=1.21.10 {
        return Ingredient.of(BuiltInRegistries.ITEM.get(key).orElseThrow());
        //?} else {
        /*// 1.21.1: pre-HolderSet Ingredient takes the TagKey directly (no registry resolution).
        return Ingredient.of(key);*/
        //?}
    }

    /** The dye colour of a stained-glass <em>block</em> item, or {@code null} for anything else
     *  (notably colourless glass, which routes to the base pipe recipe instead). */
    private static DyeColor stainedGlassColour(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof StainedGlassBlock glass) {
            return glass.getColor();
        }
        return null;
    }

    /** Matches the horizontal {@code [material, stained-glass, material]} layout (after edge-trim) and
     *  resolves which pipe + colour it makes, or {@code null} if it is not a valid dyed-pipe craft. */
    private static Match resolve(CraftingInput input) {
        if (input.width() != 3 || input.height() != 1) {
            return null;
        }
        ItemStack left = input.getItem(0);
        ItemStack right = input.getItem(2);
        DyeColor colour = stainedGlassColour(input.getItem(1));
        if (colour == null || left.isEmpty() || right.isEmpty()) {
            return null;
        }
        for (Entry entry : entries()) {
            if ((entry.a().test(left) && entry.b().test(right)) || (entry.a().test(right) && entry.b().test(left))) {
                return new Match(entry.pipe().get(), colour);
            }
        }
        return null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return resolve(input) != null;
    }

    //? if >=26.1 {
    @Override
    public ItemStack assemble(CraftingInput input) {
    //?} else {
    /*@Override
    public ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider provider) {*/
    //?}
        Match match = resolve(input);
        if (match == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = new ItemStack(match.pipe(), 8);
        out.set(BCTransportItems.PIPE_COLOUR.get(), match.colour());
        return out;
    }

    @Override
    public RecipeSerializer<DyedPipeRecipe> getSerializer() {
        return SERIALIZER;
    }

    //? if <1.21.10 {
    /*@Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3; // [material, stained-glass, material] horizontal layout
    }*/
    //?}
}
