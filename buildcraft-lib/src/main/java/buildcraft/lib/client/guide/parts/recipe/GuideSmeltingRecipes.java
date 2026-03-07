package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;

/** Smelting recipe lookup for the guide book.
 * 
 * Stubbed — the 1.21 Recipe API changed radically (see GuideCraftingRecipes for details).
 * 
 * TODO: Rewrite using 1.21 RecipeManager.getRecipes() + SmeltingRecipe */
public enum GuideSmeltingRecipes implements IStackRecipes {
    INSTANCE;

    @Override
    public List<GuidePartFactory> getUsages(@Nonnull ItemStack stack) {
        // Stubbed — needs 1.21 Recipe API rewrite
        return ImmutableList.of();
    }

    @Override
    public List<GuidePartFactory> getRecipes(@Nonnull ItemStack stack) {
        // Stubbed — needs 1.21 Recipe API rewrite
        return ImmutableList.of();
    }
}
