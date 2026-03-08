/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.IRecipeViewable;

/** Factory for creating crafting recipe display parts.
 *
 * Uses Access Transformer-opened fields: ShapedRecipe.result, ShapedRecipe.pattern,
 * ShapelessRecipe.result, ShapelessRecipe.ingredients. */
public class GuideCraftingFactory implements GuidePartFactory {

    private final ChangingItemStack[][] input;
    private final ChangingItemStack output;
    private final int hash;

    public GuideCraftingFactory(ChangingItemStack[][] input, ChangingItemStack output) {
        this.input = input;
        this.output = output;
        int h = 0;
        for (ChangingItemStack[] row : input) {
            for (ChangingItemStack stack : row) {
                h = h * 31 + stack.hashCode();
            }
        }
        this.hash = h * 31 + output.hashCode();
    }

    /** Check if this factory's output matches a given stack. */
    public boolean outputMatches(ItemStack target) {
        return output.matches(target);
    }

    /** Create a factory from a 1.21 CraftingRecipe.
     * Handles ShapedRecipe, ShapelessRecipe, and IRecipeViewable. */
    @Nullable
    public static GuidePartFactory getFactory(CraftingRecipe recipe) {
        if (recipe instanceof IRecipeViewable) {
            return getFactoryFromViewable((IRecipeViewable) recipe);
        }

        if (recipe instanceof ShapedRecipe shaped) {
            return getFactoryFromShaped(shaped);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            return getFactoryFromShapeless(shapeless);
        }

        return null;
    }

    private static GuidePartFactory getFactoryFromShaped(ShapedRecipe recipe) {
        // AT-opened: ShapedRecipe.pattern (by NeoForge), ShapedRecipe.result (by us)
        ItemStack output = recipe.result;
        if (output.isEmpty()) return null;

        int width = recipe.getWidth();
        int height = recipe.getHeight();
        List<Optional<Ingredient>> ingredients = recipe.pattern.ingredients();
        int offsetX = width == 1 ? 1 : 0;
        int offsetY = height == 1 ? 1 : 0;

        ChangingItemStack[][] matrix = new ChangingItemStack[3][3];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x < offsetX || y < offsetY) {
                    matrix[x][y] = new ChangingItemStack(ItemStack.EMPTY);
                    continue;
                }
                int i = (x - offsetX) + (y - offsetY) * width;
                if (i >= ingredients.size() || (x - offsetX) >= width) {
                    matrix[x][y] = new ChangingItemStack(ItemStack.EMPTY);
                } else {
                    Optional<Ingredient> opt = ingredients.get(i);
                    matrix[x][y] = opt.map(GuideCraftingFactory::ingredientToChanging)
                        .orElse(new ChangingItemStack(ItemStack.EMPTY));
                }
            }
        }
        return new GuideCraftingFactory(matrix, new ChangingItemStack(output));
    }

    private static GuidePartFactory getFactoryFromShapeless(ShapelessRecipe recipe) {
        // AT-opened: ShapelessRecipe.result & ShapelessRecipe.ingredients (by us)
        ItemStack output = recipe.result;
        List<Ingredient> ingredients = recipe.ingredients;
        if (ingredients.isEmpty() || output.isEmpty()) return null;

        ChangingItemStack[][] matrix = new ChangingItemStack[3][3];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int i = x + y * 3;
                if (i < ingredients.size()) {
                    matrix[x][y] = ingredientToChanging(ingredients.get(i));
                } else {
                    matrix[x][y] = new ChangingItemStack(ItemStack.EMPTY);
                }
            }
        }
        return new GuideCraftingFactory(matrix, new ChangingItemStack(output));
    }

    @Nullable
    private static GuidePartFactory getFactoryFromViewable(IRecipeViewable viewable) {
        ChangingItemStack[] inputs = viewable.getRecipeInputs();
        ChangingItemStack outputs = viewable.getRecipeOutputs();
        if (inputs == null || outputs == null) return null;

        int width = 3, height = 3;
        if (viewable instanceof IRecipeViewable.IViewableGrid grid) {
            width = grid.getRecipeWidth();
            height = grid.getRecipeHeight();
        }

        ChangingItemStack[][] matrix = new ChangingItemStack[3][3];
        int offsetX = width == 1 ? 1 : 0;
        int offsetY = height == 1 ? 1 : 0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x < offsetX || y < offsetY) {
                    matrix[x][y] = new ChangingItemStack(ItemStack.EMPTY);
                    continue;
                }
                int i = (x - offsetX) + (y - offsetY) * width;
                if (i < inputs.length && (x - offsetX) < width) {
                    matrix[x][y] = inputs[i];
                } else {
                    matrix[x][y] = new ChangingItemStack(ItemStack.EMPTY);
                }
            }
        }
        return new GuideCraftingFactory(matrix, outputs);
    }

    /** Convert an Ingredient to a ChangingItemStack for display.
     * In 1.21, Ingredient.items() returns Stream&lt;Holder&lt;Item&gt;&gt;. */
    static ChangingItemStack ingredientToChanging(Ingredient ingredient) {
        List<Holder<Item>> holders = ingredient.items().toList();
        if (holders.isEmpty()) {
            return new ChangingItemStack(ItemStack.EMPTY);
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Holder<Item> holder : holders) {
            stacks.add(new ItemStack(holder.value()));
        }
        return new ChangingItemStack(stacks);
    }

    @Override
    public GuidePart createNew(GuiGuide gui) {
        return new GuideCrafting(gui, input, output);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        GuideCraftingFactory other = (GuideCraftingFactory) obj;
        if (hash != other.hash) return false;
        if (input.length != other.input.length) return false;
        for (int x = 0; x < input.length; x++) {
            if (input[x].length != other.input[x].length) return false;
            for (int y = 0; y < input[x].length; y++) {
                if (!input[x][y].equals(other.input[x][y])) return false;
            }
        }
        return output.equals(other.output);
    }
}
