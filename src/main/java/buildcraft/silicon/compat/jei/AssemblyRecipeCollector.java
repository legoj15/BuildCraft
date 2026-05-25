/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

import buildcraft.lib.misc.ItemStackKey;
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
 *   <li>{@link FacadeAssemblyRecipes} → TWO entries (basic + hollow) whose
 *       input block slot and output slot are equal-length, deduplicated lists.
 *       JEI's slot-cycling shares one global index per recipe layout and picks
 *       {@code list.get(index % list.size())} per slot, so the two slots only
 *       stay paired when their lists have the same length and no adjacent
 *       duplicates that JEI's normalised-stack cache could collapse. Iterating
 *       {@link FacadeStateManager#validFacadeStates} once and keying by
 *       {@link ItemStackKey} produces one block-stack per unique source item,
 *       paired with one representative facade variant, keeping the JEI index
 *       count at (non-facade outputs + 2) instead of thousands.</li>
 *   <li>Every other recipe → one entry per {@code getOutputPreviews()} stack,
 *       with input slots gathered from {@code getInputsFor(output)} and the
 *       MJ cost from {@code getRequiredMicroJoulesFor(output)}.</li>
 * </ul>
 *
 * <p>Sorted by the synthetic {@link AssemblyRecipeJei#id() id} so JEI's
 * recipe order is deterministic between runs.
 */
@SuppressWarnings("deprecation")
public final class AssemblyRecipeCollector {
    private AssemblyRecipeCollector() {}

    public static List<AssemblyRecipeJei> collect() {
        List<AssemblyRecipeJei> out = new ArrayList<>();

        for (AssemblyRecipe recipe : AssemblyRecipeRegistry.REGISTRY.values()) {
            if (recipe instanceof FacadeAssemblyRecipes facade) {
                out.addAll(collectFacade(facade));
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

    private static List<AssemblyRecipeJei> collectFacade(FacadeAssemblyRecipes facade) {
        // Slot 0: 3x structure pipe (the base requirement, fixed across every facade).
        // We don't try to fall back to the cobblestone-wall placeholder used inside
        // FacadeAssemblyRecipes — if structure pipe isn't registered, JEI would
        // mislead anyway and the cleanest signal is "no facade entry."
        net.minecraft.world.item.Item structurePipe = BuiltInRegistries.ITEM.getValue(
                Identifier.parse("buildcraftunofficial:pipe_structure"));
        if (structurePipe == Items.AIR) {
            return List.of();
        }
        List<ItemStack> baseSlot = List.of(new ItemStack(structurePipe, 3));

        // Dedup by source ItemStack: validFacadeStates is keyed by BlockState,
        // so multi-state blocks (e.g. furnace = 8 states) appear many times with
        // the same requiredStack. Adjacent duplicates in the block slot get
        // collapsed by JEI's NormalizedTypedItemStack cache, leaving the block
        // slot's effective length shorter than the output slot's — and since
        // JEI's CycleTicker is shared but each slot uses (index % list.size()),
        // mismatched lengths desync the cycle. One representative facade per
        // unique source item keeps both lists the same length.
        LinkedHashMap<ItemStackKey, FacadeBlockStateInfo> uniqueBlocks = new LinkedHashMap<>();
        for (FacadeBlockStateInfo info : FacadeStateManager.validFacadeStates.values()) {
            if (!info.isVisible) continue;
            if (info.requiredStack.isEmpty()) continue;
            uniqueBlocks.putIfAbsent(new ItemStackKey(info.requiredStack), info);
        }
        if (uniqueBlocks.isEmpty()) {
            return List.of();
        }

        List<ItemStack> blockSlot = new ArrayList<>(uniqueBlocks.size());
        List<ItemStack> basicOutputs = new ArrayList<>(uniqueBlocks.size());
        List<ItemStack> hollowOutputs = new ArrayList<>(uniqueBlocks.size());
        for (FacadeBlockStateInfo info : uniqueBlocks.values()) {
            blockSlot.add(info.requiredStack.copy());
            basicOutputs.add(FacadeAssemblyRecipes.createFacadeStack(info, false));
            hollowOutputs.add(FacadeAssemblyRecipes.createFacadeStack(info, true));
        }

        long mjCost = 64L * MjAPI.MJ;
        List<List<ItemStack>> inputSlots = List.of(baseSlot, blockSlot);
        // focusLinkInputIndex=1 ties the block input slot (slot 1, not the
        // structure-pipe base at slot 0) to the output slot, so that when the
        // user opens JEI focused on a specific block — e.g. "see uses" on
        // leaves — the output slot stays pinned to that block's facade
        // instead of cycling independently and pairing leaves with the
        // emerald-ore facade. See JEI's createFocusLink javadoc.
        return List.of(
                new AssemblyRecipeJei(facade.getRegistryName() + ":basic", inputSlots, basicOutputs, mjCost, 1),
                new AssemblyRecipeJei(facade.getRegistryName() + ":hollow", inputSlots, hollowOutputs, mjCost, 1)
        );
    }

    private static String outputKey(ItemStack output) {
        Identifier id = BuiltInRegistries.ITEM.getKey(output.getItem());
        return id + "@" + output.getComponents().hashCode();
    }
}
