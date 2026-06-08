/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.craft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;


import buildcraft.lib.inventory.filter.ArrayStackFilter;
import buildcraft.lib.misc.CraftingUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/** Manages the auto-crafting logic for the Auto Workbench. Maintains a blueprint grid,
 *  matches recipes, extracts materials, and produces crafting results. */
public class WorkbenchCrafting {

    private final BlockEntity tile;
    private final int width;
    private final int height;
    private final ItemHandlerSimple invBlueprint;
    private final ItemHandlerSimple invMaterials;
    private final ItemHandlerSimple invResult;
    private boolean isBlueprintDirty = true;
    private boolean areMaterialsDirty = true;
    private boolean cachedHasRequirements = false;

    @Nullable
    private RecipeHolder<CraftingRecipe> currentRecipe;
    /** All recipes the current blueprint grid matches, sorted by id (see {@link CraftingUtil#findMatchingRecipes}).
     *  When this holds 2+ entries the machine's GUI shows a cycle-output button. Server-side only. */
    private List<RecipeHolder<CraftingRecipe>> matchingRecipes = List.of();
    /** Index into {@link #matchingRecipes} of the output the player picked. Preserved by recipe id
     *  across grid/material changes and reloads; clamps to 0 when the selection disappears. */
    private int selectedIndex = 0;
    /** A recipe id read from NBT on load, applied on the next {@link #tick()} once matches recompute. */
    @Nullable
    private String pendingSelectedRecipeId = null;
    private ItemStack assumedResult = ItemStack.EMPTY;

    public WorkbenchCrafting(int width, int height, TileBC_Neptune tile, ItemHandlerSimple invBlueprint,
        ItemHandlerSimple invMaterials, ItemHandlerSimple invResult) {
        this.width = width;
        this.height = height;
        this.tile = tile;
        this.invBlueprint = invBlueprint;
        if (invBlueprint.getSlots() < width * height) {
            throw new IllegalArgumentException("Passed blueprint has a smaller size than width * height! ( expected "
                + (width * height) + ", got " + invBlueprint.getSlots() + ")");
        }
        this.invMaterials = invMaterials;
        this.invResult = invResult;
    }

    public int getSize() {
        return width * height;
    }

    public ItemStack getAssumedResult() {
        return assumedResult;
    }

    public void onInventoryChange(ItemHandlerSimple inv) {
        if (inv == invBlueprint) {
            isBlueprintDirty = true;
        } else if (inv == invMaterials) {
            areMaterialsDirty = true;
        }
    }

    /** Creates a CraftingInput from the current blueprint slots. */
    private CraftingInput createBlueprintInput() {
        List<ItemStack> items = new ArrayList<>(getSize());
        for (int s = 0; s < getSize(); s++) {
            items.add(invBlueprint.getStackInSlot(s));
        }
        return CraftingInput.of(width, height, items);
    }

    /** @return True if anything changed, false otherwise */
    public boolean tick() {
        if (tile.getLevel().isClientSide()) {
            throw new IllegalStateException("Never call this on the client side!");
        }
        if (isBlueprintDirty) {
            CraftingInput input = createBlueprintInput();
            // Remember which output the player had picked so a grid edit (or a reload) doesn't
            // silently switch it: prefer the live selection, falling back to the id read from NBT.
            String desiredId = currentRecipe != null ? CraftingUtil.recipeId(currentRecipe) : pendingSelectedRecipeId;
            matchingRecipes = CraftingUtil.findMatchingRecipes(input, tile.getLevel());
            pendingSelectedRecipeId = null;
            selectedIndex = indexOfRecipeId(desiredId);
            currentRecipe = matchingRecipes.isEmpty() ? null : matchingRecipes.get(selectedIndex);
            updateAssumedResult(input);
            isBlueprintDirty = false;
            areMaterialsDirty = true; // re-check materials against new recipe
            return true;
        }
        return false;
    }

    /** Cycles the selected output among the matching recipes (dir = +1 next, -1 previous). No-op
     *  unless 2+ recipes match. Refreshes the assumed result so the tile re-syncs it to the client.
     *  @return true if the selection changed. */
    public boolean cycleOutput(int dir) {
        if (matchingRecipes.size() <= 1) {
            return false;
        }
        selectedIndex = Math.floorMod(selectedIndex + dir, matchingRecipes.size());
        currentRecipe = matchingRecipes.get(selectedIndex);
        updateAssumedResult(createBlueprintInput());
        areMaterialsDirty = true; // re-check materials/output against the newly selected result
        return true;
    }

    /** Number of recipes the current grid matches (0 if none). The GUI shows the cycle button when &gt;1. */
    public int getMatchCount() {
        return matchingRecipes.size();
    }

    /** Index of the currently selected output within the match list. */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** Id of the selected recipe, for NBT persistence; empty when nothing matches. */
    public String getSelectedRecipeId() {
        return currentRecipe != null ? CraftingUtil.recipeId(currentRecipe) : "";
    }

    /** Restores a selected-output choice from NBT; applied on the next {@link #tick()} once matches recompute. */
    public void setPendingSelectedRecipeId(String id) {
        pendingSelectedRecipeId = (id == null || id.isEmpty()) ? null : id;
        isBlueprintDirty = true;
    }

    private int indexOfRecipeId(@Nullable String id) {
        if (id != null) {
            for (int i = 0; i < matchingRecipes.size(); i++) {
                if (CraftingUtil.recipeId(matchingRecipes.get(i)).equals(id)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private void updateAssumedResult(CraftingInput input) {
        if (currentRecipe == null) {
            assumedResult = ItemStack.EMPTY;
        } else {
            //? if >=26.1 {
            assumedResult = currentRecipe.value().assemble(input);
            //?} else {
            /*assumedResult = currentRecipe.value().assemble(input, tile.getLevel().registryAccess());*/
            //?}
        }
    }

    /** @return True if {@link #craft()} might return true. */
    public boolean canCraft() {
        if (currentRecipe == null || isBlueprintDirty) {
            return false;
        }
        if (!invResult.canFullyAccept(assumedResult)) {
            return false;
        }
        if (areMaterialsDirty) {
            areMaterialsDirty = false;
            cachedHasRequirements = hasExactStacks();
        }
        return cachedHasRequirements;
    }

    /** Attempts to craft a single item.
     *  @return True if the crafting happened, false otherwise. */
    public boolean craft() {
        if (isBlueprintDirty) {
            return false;
        }
        return craftExact();
    }

    private boolean hasExactStacks() {
        Map<ItemStackKey, Integer> required = new HashMap<>();
        for (int s = 0; s < getSize(); s++) {
            ItemStack req = invBlueprint.getStackInSlot(s);
            if (!req.isEmpty()) {
                ItemStack singleReq = req.copyWithCount(1);
                ItemStackKey key = new ItemStackKey(singleReq);
                required.merge(key, req.getCount(), Integer::sum);
            }
        }
        for (Map.Entry<ItemStackKey, Integer> entry : required.entrySet()) {
            ArrayStackFilter filter = new ArrayStackFilter(entry.getKey().baseStack);
            int count = entry.getValue();
            ItemStack inInventory = invMaterials.extract(filter, count, count, true);
            if (inInventory.isEmpty() || inInventory.getCount() < count) {
                return false;
            }
        }
        return true;
    }

    /** Implementation of {@link #craft()}, extracting exact stacks from materials. */
    private boolean craftExact() {
        // Temporary storage for items currently in the "crafting grid"
        NonNullList<ItemStack> gridContents = NonNullList.withSize(getSize(), ItemStack.EMPTY);

        // Step 1: Extract required items from materials
        for (int s = 0; s < getSize(); s++) {
            ItemStack bpt = invBlueprint.getStackInSlot(s);
            if (!bpt.isEmpty()) {
                ItemStack stack = invMaterials.extract(new ArrayStackFilter(bpt), 1, 1, false);
                if (stack.isEmpty()) {
                    // Failed — return everything we already extracted
                    returnItemsToMaterials(gridContents);
                    return false;
                }
                gridContents.set(s, stack);
            }
        }

        // Step 2: Build CraftingInput from extracted items and verify recipe
        CraftingInput craftInput = CraftingInput.of(width, height, gridContents);
        if (!currentRecipe.value().matches(craftInput, tile.getLevel())) {
            returnItemsToMaterials(gridContents);
            return false;
        }

        // Step 3: Assemble result
        //? if >=26.1 {
        ItemStack result = currentRecipe.value().assemble(craftInput);
        //?} else {
        /*ItemStack result = currentRecipe.value().assemble(craftInput, tile.getLevel().registryAccess());*/
        //?}
        if (result.isEmpty()) {
            returnItemsToMaterials(gridContents);
            return false;
        }

        // Step 4: Insert result into output slot
        ItemStack leftover = invResult.insert(result, false, false);
        if (!leftover.isEmpty()) {
            InventoryUtil.addToBestAcceptor(tile.getLevel(), tile.getBlockPos(), null, leftover);
        }

        // Step 5: Get remaining items BEFORE clearing the grid, because
        // CraftingInput.of() stores a reference to the same list — clearing
        // gridContents would also empty the items inside craftInput.
        NonNullList<ItemStack> remainingStacks = currentRecipe.value().getRemainingItems(craftInput);

        // Step 6: Crafting consumed all inputs — clear the grid
        for (int s = 0; s < gridContents.size(); s++) {
            gridContents.set(s, ItemStack.EMPTY);
        }

        // Step 7: Handle remaining items (e.g. empty buckets from cake recipe)
        for (int s = 0; s < remainingStacks.size(); s++) {
            ItemStack remaining = remainingStacks.get(s);
            if (!remaining.isEmpty()) {
                leftover = invMaterials.insert(remaining, false, false);
                if (!leftover.isEmpty()) {
                    InventoryUtil.addToBestAcceptor(tile.getLevel(), tile.getBlockPos(), null, leftover);
                }
            }
        }

        return true;
    }

    /** Returns all non-empty items from the grid back to the materials inventory. */
    private void returnItemsToMaterials(NonNullList<ItemStack> gridContents) {
        for (int s = 0; s < gridContents.size(); s++) {
            ItemStack inSlot = gridContents.get(s);
            if (!inSlot.isEmpty()) {
                ItemStack leftover = invMaterials.insert(inSlot, false, false);
                if (!leftover.isEmpty()) {
                    InventoryUtil.addToBestAcceptor(tile.getLevel(), tile.getBlockPos(), null, leftover);
                }
                gridContents.set(s, ItemStack.EMPTY);
            }
        }
    }
}
