package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;

/** Assembly recipe guide integration.
 * Currently stubbed — requires AssemblyRecipeRegistry which is not yet ported. */
@SuppressWarnings("deprecation")
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
        // ingredient.display().resolveForStacks(ctx) preserves data-component patches that
        // ingredient.items() drops on the floor. The user-visible difference: a DataComponentIngredient
        // input (every gate modifier-upgrade recipe — e.g. Iron AND + Lapis → Iron+Lapis Gate)
        // would otherwise render its gate slot as `new ItemStack(PLUG_GATE)`, a bare stack
        // whose default GateVariant renders as the CLAY_BRICK "Basic Gate." Players reading the
        // guide-book entry then concluded the Basic Gate was an ingredient in higher-tier gates,
        // even though it isn't a recipe input anywhere.
        net.minecraft.util.context.ContextMap ctx = new net.minecraft.util.context.ContextMap.Builder()
            .create(net.minecraft.world.item.crafting.display.SlotDisplayContext.CONTEXT);
        ItemStack[] inStacks = new ItemStack[inputs.size()];
        int i = 0;
        for (buildcraft.api.recipes.IngredientStack ing : inputs) {
            java.util.List<ItemStack> resolved = ing.ingredient.display().resolveForStacks(ctx);
            ItemStack first = ItemStack.EMPTY;
            for (ItemStack candidate : resolved) {
                if (!candidate.isEmpty() && candidate.getItem() != net.minecraft.world.item.Items.AIR) {
                    first = candidate;
                    break;
                }
            }
            if (!first.isEmpty()) {
                ItemStack rep = first.copy();
                rep.setCount(ing.count);
                inStacks[i++] = rep;
            } else {
                inStacks[i++] = ItemStack.EMPTY;
            }
        }
        return new GuideAssemblyFactory(inStacks, output, recipe.getRequiredMicroJoulesFor(output));
    }
}
