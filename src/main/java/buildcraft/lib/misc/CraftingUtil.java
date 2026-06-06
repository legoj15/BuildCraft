/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
//? if >=1.21.10 {
import net.minecraft.util.context.ContextMap;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
//? if >=1.21.10 {
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
//?}
import net.minecraft.world.level.Level;

import buildcraft.lib.tile.item.ItemHandlerSimple;

@SuppressWarnings("deprecation")
public final class CraftingUtil {

    private CraftingUtil() {
    }

    @Nullable
    public static RecipeHolder<CraftingRecipe> findMatchingRecipe(CraftingInput input, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        //? if >=1.21.10 {
        return serverLevel.recipeAccess()
            .getRecipeFor(RecipeType.CRAFTING, input, serverLevel)
            .orElse(null);
        //?} else {
        /*return serverLevel.getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, serverLevel)
            .orElse(null);*/
        //?}
    }

    /**
     * Places a crafting recipe's ingredients into a 3x3 phantom blueprint inventory.
     * Uses AT-opened fields (ShapelessRecipe.ingredients) which are only accessible
     * from buildcraft-lib where the AT is declared.
     *
     * @param recipe The crafting recipe to place
     * @param blueprint A 9-slot (3x3) phantom inventory to populate
     * @param level The (server) level whose registry access resolves tag-based ingredients
     */
    public static void placeRecipeInBlueprint(CraftingRecipe recipe, ItemHandlerSimple blueprint, Level level) {
        // Clear blueprint
        for (int i = 0; i < blueprint.getSlots(); i++) {
            blueprint.setStackInSlot(i, ItemStack.EMPTY);
        }

        //? if >=1.21.10 {
        // Context for resolving ingredient SlotDisplays. fromLevel() supplies
        // SlotDisplayContext.REGISTRIES (+ FUEL_VALUES); without REGISTRIES a TagSlotDisplay —
        // i.e. every tag-based ingredient like #c:ingots/iron or #c:gears/stone — resolves to an
        // empty stream and the phantom slot stays blank. Runs server-side, so the registry source
        // is the Level, not Minecraft.getInstance().
        ContextMap ctx = SlotDisplayContext.fromLevel(level);

        if (recipe instanceof ShapedRecipe shaped) {
            // For shaped recipes, respect the pattern width/height
            int recipeWidth = shaped.getWidth();
            int recipeHeight = shaped.getHeight();
            List<Optional<Ingredient>> ingredients = shaped.pattern.ingredients();

            for (int row = 0; row < recipeHeight && row < 3; row++) {
                for (int col = 0; col < recipeWidth && col < 3; col++) {
                    int ingredientIdx = col + row * recipeWidth;
                    int blueprintIdx = col + row * 3;
                    if (ingredientIdx < ingredients.size()) {
                        Optional<Ingredient> optIngredient = ingredients.get(ingredientIdx);
                        optIngredient.ifPresent(ingredient -> {
                            ItemStack stack = ingredientToStack(ingredient, ctx);
                            if (!stack.isEmpty()) {
                                blueprint.setStackInSlot(blueprintIdx, stack);
                            }
                        });
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            // Shapeless — AT-opened field
            List<Ingredient> ingredients = shapeless.ingredients;
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                ItemStack stack = ingredientToStack(ingredients.get(i), ctx);
                if (!stack.isEmpty()) {
                    blueprint.setStackInSlot(i, stack);
                }
            }
        } else {
            // Fallback for custom or wrapped shapeless recipes
            buildcraft.api.core.BCLog.logger.info("[CraftingUtil] Fallback for unknown recipe class: " + recipe.getClass().getName());
            try {
                List<net.minecraft.world.item.crafting.display.RecipeDisplay> displays = recipe.display();
                if (!displays.isEmpty()) {
                    net.minecraft.world.item.crafting.display.RecipeDisplay d = displays.get(0);
                    if (d instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapelessDisplay) {
                        for (int i = 0; i < shapelessDisplay.ingredients().size() && i < 9; ++i) {
                            net.minecraft.world.item.crafting.display.SlotDisplay slotDisplay = shapelessDisplay.ingredients().get(i);
                            List<ItemStack> stacks = slotDisplay.resolveForStacks(ctx);
                            if (!stacks.isEmpty()) {
                                blueprint.setStackInSlot(i, stacks.get(0).copy());
                            }
                        }
                    } else if (d instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shapedDisplay) {
                        int w = shapedDisplay.width();
                        int h = shapedDisplay.height();
                        for (int r = 0; r < h && r < 3; r++) {
                            for (int c = 0; c < w && c < 3; c++) {
                                int idx = c + r * w;
                                if (idx < shapedDisplay.ingredients().size()) {
                                    net.minecraft.world.item.crafting.display.SlotDisplay slotDisplay = shapedDisplay.ingredients().get(idx);
                                    List<ItemStack> stacks = slotDisplay.resolveForStacks(ctx);
                                    if (!stacks.isEmpty()) {
                                        blueprint.setStackInSlot(c + r * 3, stacks.get(0).copy());
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                buildcraft.api.core.BCLog.logger.error("[CraftingUtil] Error fallback parsing display", e);
            }
        }
        //?} else {
        /*// 1.21.1 has no SlotDisplay/RecipeDisplay system — ingredients are read directly off the recipe.
        if (recipe instanceof ShapedRecipe shaped) {
            int recipeWidth = shaped.getWidth();
            int recipeHeight = shaped.getHeight();
            net.minecraft.core.NonNullList<Ingredient> ingredients = shaped.pattern.ingredients();
            for (int row = 0; row < recipeHeight && row < 3; row++) {
                for (int col = 0; col < recipeWidth && col < 3; col++) {
                    int ingredientIdx = col + row * recipeWidth;
                    int blueprintIdx = col + row * 3;
                    if (ingredientIdx < ingredients.size()) {
                        ItemStack stack = ingredientToStack(ingredients.get(ingredientIdx));
                        if (!stack.isEmpty()) {
                            blueprint.setStackInSlot(blueprintIdx, stack);
                        }
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            net.minecraft.core.NonNullList<Ingredient> ingredients = shapeless.ingredients;
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                ItemStack stack = ingredientToStack(ingredients.get(i));
                if (!stack.isEmpty()) {
                    blueprint.setStackInSlot(i, stack);
                }
            }
        } else {
            buildcraft.api.core.BCLog.logger.info("[CraftingUtil] Fallback for unknown recipe class: " + recipe.getClass().getName());
            net.minecraft.core.NonNullList<Ingredient> ingredients = recipe.getIngredients();
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                ItemStack stack = ingredientToStack(ingredients.get(i));
                if (!stack.isEmpty()) {
                    blueprint.setStackInSlot(i, stack);
                }
            }
        }*/
        //?}
    }

    /** Convert an Ingredient to the first matching ItemStack.
     * Uses {@code Ingredient.display().resolveForStacks(ctx)} (not {@code Ingredient.items()})
     * so DataComponentIngredient patches survive into the phantom blueprint slot. JEI's "+"
     * button copies a recipe layout into the Advanced Crafting Table's blueprint grid via
     * this path, and recipes that match on data components (e.g. the gate-modifier-upgrade
     * crafting recipes that need a specific gate variant as input) would otherwise drop the
     * variant patch and pre-fill the slot with a default-variant stack — clicking "+" on the
     * Iron+Lapis recipe would populate the gate slot with a bare CLAY_BRICK "Basic Gate"
     * instead of the Iron AND Gate the recipe actually requires. */
    //? if >=1.21.10 {
    private static ItemStack ingredientToStack(Ingredient ingredient, ContextMap ctx) {
        for (ItemStack candidate : ingredient.display().resolveForStacks(ctx)) {
            if (!candidate.isEmpty() && candidate.getItem() != net.minecraft.world.item.Items.AIR) {
                return candidate.copy();
            }
        }
        return ItemStack.EMPTY;
    }
    //?} else {
    /*// 1.21.1: Ingredient.getItems() returns the matching stacks (incl. DataComponentIngredient patches).
    private static ItemStack ingredientToStack(Ingredient ingredient) {
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty() && candidate.getItem() != net.minecraft.world.item.Items.AIR) {
                return candidate.copy();
            }
        }
        return ItemStack.EMPTY;
    }*/
    //?}
}
