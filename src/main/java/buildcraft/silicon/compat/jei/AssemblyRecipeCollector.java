/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.AssemblyRecipe;
import buildcraft.api.recipes.IngredientStack;

import buildcraft.lib.recipe.AssemblyRecipeRegistry;

import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.silicon.recipe.FacadeAssemblyRecipes;

/**
 * Walks {@link AssemblyRecipeRegistry#REGISTRY} once at JEI plugin init and
 * emits a flat list of {@link AssemblyRecipeJei} entries.
 *
 * <p>Two paths:
 * <ul>
 *   <li>{@link FacadeAssemblyRecipes} → exactly ONE entry whose two input
 *       slots and one output slot each carry a list-of-stacks. JEI's native
 *       slot-cycling (via {@code addItemStacks(List)}) renders this as one
 *       browsable recipe with cycling source-block input and cycling facade
 *       output, instead of the ~5,000 separate entries a naive port would
 *       produce.</li>
 *   <li>Every other recipe → one entry per {@code getOutputPreviews()} stack,
 *       with input slots gathered from {@code getInputsFor(output)} and the
 *       MJ cost from {@code getRequiredMicroJoulesFor(output)}.</li>
 * </ul>
 *
 * <p>Sorted by the synthetic {@link AssemblyRecipeJei#id() id} so JEI's
 * recipe order is deterministic between runs.
 */
public final class AssemblyRecipeCollector {
    private AssemblyRecipeCollector() {}

    public static List<AssemblyRecipeJei> collect() {
        List<AssemblyRecipeJei> out = new ArrayList<>();

        for (AssemblyRecipe recipe : AssemblyRecipeRegistry.REGISTRY.values()) {
            if (recipe instanceof FacadeAssemblyRecipes facade) {
                AssemblyRecipeJei facadeEntry = collectFacade(facade);
                if (facadeEntry != null) {
                    out.add(facadeEntry);
                }
                continue;
            }
            collectStandard(recipe, out);
        }

        out.sort(Comparator.comparing(AssemblyRecipeJei::id));
        return out;
    }

    private static void collectStandard(AssemblyRecipe recipe, List<AssemblyRecipeJei> out) {
        for (ItemStack output : recipe.getOutputPreviews()) {
            if (output.isEmpty()) continue;

            List<List<ItemStack>> inputSlots = new ArrayList<>();
            for (IngredientStack ing : recipe.getInputsFor(output)) {
                List<ItemStack> slot = new ArrayList<>();
                // Ingredient.items() is deprecated but is still the supported way
                // to enumerate matching items for display — getValues() throws on
                // custom ingredients (e.g. NeoForge CompoundIngredient).
                for (Holder<Item> holder : (Iterable<Holder<Item>>) ing.ingredient.items()::iterator) {
                    Item item = holder.value();
                    if (item == Items.AIR) continue;
                    slot.add(new ItemStack(item, ing.count));
                }
                if (!slot.isEmpty()) {
                    inputSlots.add(slot);
                }
            }
            if (inputSlots.isEmpty()) continue;

            String id = recipe.getRegistryName() + ":" + outputKey(output);
            out.add(new AssemblyRecipeJei(
                    id,
                    inputSlots,
                    List.of(output),
                    recipe.getRequiredMicroJoulesFor(output)
            ));
        }
    }

    private static AssemblyRecipeJei collectFacade(FacadeAssemblyRecipes facade) {
        // Slot 0: 3x structure pipe (the base requirement, fixed across every facade).
        // We don't try to fall back to the cobblestone-wall placeholder used inside
        // FacadeAssemblyRecipes — if structure pipe isn't registered, JEI would
        // mislead anyway and the cleanest signal is "no facade entry."
        net.minecraft.world.item.Item structurePipe = BuiltInRegistries.ITEM.getValue(
                Identifier.parse("buildcraftunofficial:pipe_structure"));
        if (structurePipe == Items.AIR) {
            return null;
        }
        List<ItemStack> baseSlot = List.of(new ItemStack(structurePipe, 3));

        // Slot 1 (block) and the output column (basic + hollow facade) cycle
        // in step. Doubling each block on the input side mirrors the
        // FacadeAssemblyRecipes.getRecipeInputs() pairing so input-block N
        // is on screen at the same time as both facade variants of N.
        List<ItemStack> blockSlot = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        for (FacadeBlockStateInfo info : FacadeStateManager.validFacadeStates.values()) {
            if (!info.isVisible) continue;
            if (info.requiredStack.isEmpty()) continue;
            blockSlot.add(info.requiredStack.copy());
            blockSlot.add(info.requiredStack.copy());
            outputs.add(FacadeAssemblyRecipes.createFacadeStack(info, false));
            outputs.add(FacadeAssemblyRecipes.createFacadeStack(info, true));
        }
        if (outputs.isEmpty()) {
            return null;
        }

        return new AssemblyRecipeJei(
                facade.getRegistryName(),
                List.of(baseSlot, blockSlot),
                outputs,
                64L * MjAPI.MJ
        );
    }

    private static String outputKey(ItemStack output) {
        Identifier id = BuiltInRegistries.ITEM.getKey(output.getItem());
        return id + "@" + output.getComponents().hashCode();
    }
}
