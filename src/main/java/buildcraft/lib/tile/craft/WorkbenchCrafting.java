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

import net.neoforged.neoforge.items.IItemHandler;

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

    public void onInventoryChange(IItemHandler inv) {
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
            currentRecipe = CraftingUtil.findMatchingRecipe(input, tile.getLevel());
            if (currentRecipe == null) {
                assumedResult = ItemStack.EMPTY;
            } else {
                assumedResult = currentRecipe.value().assemble(input);
            }
            isBlueprintDirty = false;
            areMaterialsDirty = true; // re-check materials against new recipe
            return true;
        }
        return false;
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
        ItemStack result = currentRecipe.value().assemble(craftInput);
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
