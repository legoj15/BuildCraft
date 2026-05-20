package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;

/** Assembly recipe guide integration.
 * Currently stubbed — requires AssemblyRecipeRegistry which is not yet ported. */
public enum GuideAssemblyRecipes implements IStackRecipes {
    INSTANCE;

    @Override
    public List<GuidePartFactory> getUsages(@Nonnull ItemStack stack) {
        List<GuidePartFactory> list = new ArrayList<>();
        for (buildcraft.api.recipes.AssemblyRecipe recipe : buildcraft.lib.recipe.AssemblyRecipeRegistry.REGISTRY.values()) {
            for (ItemStack out : recipe.getOutputPreviews()) {
                boolean isUsed = false;
                for (buildcraft.api.recipes.IngredientStack ing : recipe.getInputsFor(out)) {
                    if (ing.ingredient.test(stack)) {
                        isUsed = true;
                        break;
                    }
                }
                if (isUsed) {
                    list.add(createFactory(recipe, out));
                }
            }
        }
        return list;
    }

    @Override
    public List<GuidePartFactory> getRecipes(@Nonnull ItemStack stack) {
        List<GuidePartFactory> list = new ArrayList<>();
        for (buildcraft.api.recipes.AssemblyRecipe recipe : buildcraft.lib.recipe.AssemblyRecipeRegistry.REGISTRY.values()) {
            for (ItemStack out : recipe.getOutputPreviews()) {
                if (ItemStack.isSameItem(stack, out)) {
                    list.add(createFactory(recipe, out));
                }
            }
        }
        return list;
    }

    private static GuidePartFactory createFactory(buildcraft.api.recipes.AssemblyRecipe recipe, ItemStack output) {
        java.util.Set<buildcraft.api.recipes.IngredientStack> inputs = recipe.getInputsFor(output);
        ItemStack[] inStacks = new ItemStack[inputs.size()];
        int i = 0;
        for (buildcraft.api.recipes.IngredientStack ing : inputs) {
            ItemStack[] matching = ing.ingredient.items()
                .map(holder -> new ItemStack(holder.value()))
                .toArray(ItemStack[]::new);
            if (matching.length > 0) {
                ItemStack rep = matching[0].copy();
                rep.setCount(ing.count);
                inStacks[i++] = rep;
            } else {
                inStacks[i++] = ItemStack.EMPTY;
            }
        }
        return new GuideAssemblyFactory(inStacks, output, recipe.getRequiredMicroJoulesFor(output));
    }
}
