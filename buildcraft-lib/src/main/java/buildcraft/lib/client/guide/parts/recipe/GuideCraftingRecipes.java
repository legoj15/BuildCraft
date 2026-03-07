package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;

/** Crafting recipe lookup for the guide book.
 * 
 * Stubbed — the 1.21 Recipe API changed radically:
 * - Recipe no longer has getIngredients()/getResultItem() on the base interface
 * - RecipeManager no longer has getAllRecipesFor()
 * - Ingredient no longer has getItems() (uses items() -> Stream)
 * 
 * TODO: Rewrite using 1.21 RecipeManager.getRecipes() + RecipeHolder + ShapedRecipePattern */
public enum GuideCraftingRecipes implements IStackRecipes {
    INSTANCE;

    @Override
    public List<GuidePartFactory> getUsages(@Nonnull ItemStack target) {
        // Stubbed — needs 1.21 Recipe API rewrite
        return ImmutableList.of();
    }

    @Override
    public List<GuidePartFactory> getRecipes(@Nonnull ItemStack target) {
        // Stubbed — needs 1.21 Recipe API rewrite
        return ImmutableList.of();
    }

    public void generateIndices() {
        // Stubbed — index generation needs 1.21 Recipe API rewrite
    }
}
