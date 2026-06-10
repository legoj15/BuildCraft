/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.misc.RegistryKeyUtil;

/** Crafting recipe lookup for the guide book.
 *
 * Uses instanceof filtering for ShapedRecipe/ShapelessRecipe over
 * {@link ClientGuideRecipeCache#getAllRecipeHolders()} — the server-synced recipe store
 * (works in multiplayer), with an integrated-server fallback for singleplayer. */
public enum GuideCraftingRecipes implements IStackRecipes {
    INSTANCE;

    @Override
    public List<GuidePartFactory> getUsages(@Nonnull ItemStack target) {
        List<GuidePartFactory> list = new ArrayList<>();
        for (RecipeHolder<?> holder : getAllRecipes()) {
            if (holder.value() instanceof CraftingRecipe recipe) {
                if (checkRecipeUses(recipe, target)) {
                    GuidePartFactory factory = GuideCraftingFactory.getFactory(recipe);
                    if (factory != null) {
                        list.add(factory);
                    }
                }
            }
        }
        return list;
    }

    @Override
    public List<GuidePartFactory> getRecipes(@Nonnull ItemStack target) {
        List<GuidePartFactory> list = new ArrayList<>();
        for (RecipeHolder<?> holder : getAllRecipes()) {
            if (holder.value() instanceof CraftingRecipe recipe) {
                // Check output by extracting the result from the factory
                GuidePartFactory factory = GuideCraftingFactory.getFactory(recipe);
                if (factory instanceof GuideCraftingFactory gcf && gcf.outputMatches(target)) {
                    list.add(factory);
                }
            }
        }
        return list;
    }

    /** Gather every crafting recipe whose id (ResourceLocation, stringified) contains {@code substring},
     *  in id order. Public so the {@code <recipe_cycle>} tag and a game test can both rely on which
     *  recipes a given match selects. Returns an empty list when no recipe data is available
     *  (outside a world, before the server's recipe sync has arrived). */
    public static List<CraftingRecipe> gatherByIdMatch(String substring) {
        java.util.TreeMap<String, CraftingRecipe> matched = new java.util.TreeMap<>();
        for (RecipeHolder<?> holder : ClientGuideRecipeCache.getAllRecipeHolders()) {
            if (holder.value() instanceof CraftingRecipe crafting
                && RegistryKeyUtil.id(holder.id()).toString().contains(substring)) {
                matched.put(RegistryKeyUtil.id(holder.id()).toString(), crafting);
            }
        }
        return new ArrayList<>(matched.values());
    }

    /** Fold the {@code substring}-matched crafting recipes into one cycling panel via
     *  {@link GuideCraftingFactory#getCyclingFactory}. Backs {@code <recipe_cycle match="…"/>}. */
    @Nullable
    public static GuidePartFactory getCyclingFactoryByIdMatch(String substring) {
        return GuideCraftingFactory.getCyclingFactory(gatherByIdMatch(substring));
    }

    private static boolean checkRecipeUses(CraftingRecipe recipe, @Nonnull ItemStack target) {
        if (recipe instanceof ShapedRecipe shaped) {
            //? if >=1.21.10 {
            for (Optional<Ingredient> opt : shaped.pattern.ingredients()) {
                if (opt.isPresent() && opt.get().test(target)) {
                    return true;
                }
            }
            //?} else {
            /*for (Ingredient ing : shaped.pattern.ingredients()) {
                if (!ing.isEmpty() && ing.test(target)) {
                    return true;
                }
            }*/
            //?}
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            for (Ingredient ing : shapeless.ingredients) {
                if (ing.test(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Iterable<RecipeHolder<?>> getAllRecipes() {
        return ClientGuideRecipeCache.getAllRecipeHolders();
    }
}
