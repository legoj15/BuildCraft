package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.ChangingObject;

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

    /** Build a display factory for a single assembly recipe looked up by its registry name
     *  (e.g. {@code "gate-and-IRON-NO_MODIFIER"}, as assigned in BCSiliconRecipes). Returns the
     *  factory for the recipe's first output preview, or null if no recipe has that name. Backs
     *  the guide's {@code <recipe id="..." type="assembling"/>} tag, which lets a page pin one
     *  specific assembly recipe in a hand-authored order — the per-output {@link #getRecipes}
     *  path matches by item only, so it can't single out one gate variant's recipe. */
    @Nullable
    public static GuidePartFactory getFactoryByName(String name) {
        buildcraft.api.recipes.AssemblyRecipe recipe = buildcraft.lib.recipe.AssemblyRecipeRegistry.REGISTRY.get(name);
        if (recipe == null) {
            return null;
        }
        for (ItemStack out : recipe.getOutputPreviews()) {
            return createFactory(recipe, out);
        }
        return null;
    }

    private static GuidePartFactory createFactory(buildcraft.api.recipes.AssemblyRecipe recipe, ItemStack output) {
        return new GuideAssemblyFactory(resolveInputStacks(recipe, output), output,
            recipe.getRequiredMicroJoulesFor(output));
    }

    /** Resolve a recipe's inputs (for {@code output}) to one representative {@link ItemStack} per slot.
     *  {@code ingredient.display().resolveForStacks(ctx)} preserves data-component patches that
     *  {@code ingredient.items()} drops — e.g. a gate modifier-upgrade's DataComponentIngredient gate
     *  input would otherwise render as a bare PLUG_GATE (the default CLAY_BRICK "Basic Gate"), wrongly
     *  implying the Basic Gate is an ingredient. The shared context carries SlotDisplayContext.REGISTRIES
     *  so tag ingredients resolve (see {@code GuideCraftingFactory.displayContext}) rather than blank. */
    private static ItemStack[] resolveInputStacks(buildcraft.api.recipes.AssemblyRecipe recipe, ItemStack output) {
        java.util.Set<buildcraft.api.recipes.IngredientStack> inputs = recipe.getInputsFor(output);
        //? if >=1.21.10 {
        net.minecraft.util.context.ContextMap ctx = GuideCraftingFactory.displayContext();
        //?}
        ItemStack[] inStacks = new ItemStack[inputs.size()];
        int i = 0;
        for (buildcraft.api.recipes.IngredientStack ing : inputs) {
            //? if >=1.21.10 {
            java.util.List<ItemStack> resolved = ing.ingredient.display().resolveForStacks(ctx);
            //?} else {
            /*java.util.List<ItemStack> resolved = java.util.Arrays.asList(ing.ingredient.getItems());*/
            //?}
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
        return inStacks;
    }

    /** Gather every assembly recipe whose registry name contains {@code substring}, in name order
     *  (so {@code "gate-and-…"} precedes {@code "gate-or-…"}). Public so the {@code <recipe_cycle>}
     *  tag and a game test can both rely on which recipes a given match selects. */
    public static List<buildcraft.api.recipes.AssemblyRecipe> gatherByNameMatch(String substring) {
        List<buildcraft.api.recipes.AssemblyRecipe> matched = new ArrayList<>();
        for (java.util.Map.Entry<String, buildcraft.api.recipes.AssemblyRecipe> entry
                : new java.util.TreeMap<>(buildcraft.lib.recipe.AssemblyRecipeRegistry.REGISTRY).entrySet()) {
            if (entry.getKey().contains(substring)) {
                matched.add(entry.getValue());
            }
        }
        return matched;
    }

    /** Fold the {@code substring}-matched assembly recipes into one cycling panel (see
     *  {@link #getCyclingFactory}). Backs {@code <recipe_cycle type="assembling" match="…"/>}. */
    @Nullable
    public static GuidePartFactory getCyclingFactoryByNameMatch(String substring) {
        return getCyclingFactory(gatherByNameMatch(substring));
    }

    /** Fold several assembly recipes into one panel whose input slots, output and MJ cost cycle in
     *  lock-step through each recipe — recipe N shares animation frame N across all of them (see
     *  {@link ChangingObject}'s time-based, length-relative index). Lets a gate's AND and OR variants
     *  collapse from two near-identical panels into one. Returns the lone factory when a single recipe
     *  matched, or null when none did. */
    @Nullable
    public static GuidePartFactory getCyclingFactory(List<buildcraft.api.recipes.AssemblyRecipe> recipes) {
        List<ItemStack[]> inputsPerRecipe = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        List<Long> mjCosts = new ArrayList<>();
        int maxSlots = 0;
        for (buildcraft.api.recipes.AssemblyRecipe recipe : recipes) {
            ItemStack output = ItemStack.EMPTY;
            for (ItemStack out : recipe.getOutputPreviews()) {
                output = out;
                break;
            }
            if (output.isEmpty()) {
                continue;
            }
            ItemStack[] inStacks = resolveInputStacks(recipe, output);
            inputsPerRecipe.add(inStacks);
            outputs.add(output);
            mjCosts.add(recipe.getRequiredMicroJoulesFor(output));
            maxSlots = Math.max(maxSlots, inStacks.length);
        }
        if (outputs.isEmpty()) {
            return null;
        }
        if (outputs.size() == 1) {
            return new GuideAssemblyFactory(inputsPerRecipe.get(0), outputs.get(0), mjCosts.get(0));
        }
        ChangingItemStack[] input = new ChangingItemStack[maxSlots];
        for (int slot = 0; slot < maxSlots; slot++) {
            List<ItemStack> options = new ArrayList<>(outputs.size());
            for (ItemStack[] in : inputsPerRecipe) {
                options.add(slot < in.length ? in[slot] : ItemStack.EMPTY);
            }
            input[slot] = new ChangingItemStack(options);
        }
        ChangingItemStack output = new ChangingItemStack(outputs);
        ChangingObject<Long> mjCost = new ChangingObject<>(mjCosts.toArray(new Long[0]));
        return new GuideAssemblyFactory(input, output, mjCost);
    }
}
