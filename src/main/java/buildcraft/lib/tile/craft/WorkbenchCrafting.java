/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.craft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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
 *  matches recipes, extracts materials, and produces crafting results.
 *
 *  <p>Two opt-in features, both built on one idea (swap every grid slot holding a given blueprint item for another
 *  item, then re-check the recipes) and both off by default, so the Auto Workbench, which shares this engine and whose
 *  blueprint may hold real items, behaves exactly as before:
 *  <ul>
 *  <li>{@link #enableVariants}: the output-cycle list also offers the recipes the grid would match with one of its
 *      items swapped for another member of a configured item tag (the other woods' stairs for a plank stair grid).
 *      Selecting such a variant rewrites the blueprint to the swapped grid, so what is shown is what is crafted.</li>
 *  <li>{@link #setAcceptSubstitutes}: when the exact blueprint items are not in the materials, the selected recipe may
 *      be crafted from other materials whose swap keeps the recipe satisfied and the result the same item.</li>
 *  </ul> */
public class WorkbenchCrafting {

    /** Bound on the recipe lookups one blueprint change may spend discovering variants. */
    private static final int MAX_VARIANT_LOOKUPS = 256;
    /** Bound on the joint substitute grids tried when the materials change. */
    private static final int MAX_SUBSTITUTE_COMBINATIONS = 256;

    /** One entry of the output-cycle list: a recipe plus, for a variant, the swapped grid it needs. */
    private record Match(RecipeHolder<CraftingRecipe> recipe, @Nullable List<ItemStack> variantGrid) {
        /** True for a recipe the blueprint matches only after swapping items: selecting it rewrites the blueprint. */
        boolean isVariant() {
            return variantGrid != null;
        }
    }

    private final BlockEntity tile;
    private final int width;
    private final int height;
    private final ItemHandlerSimple invBlueprint;
    private final ItemHandlerSimple invMaterials;
    private final ItemHandlerSimple invResult;
    private boolean isBlueprintDirty = true;
    private boolean areMaterialsDirty = true;
    /** The grid {@link #craft()} extracts from the materials (the blueprint, or its substituted form), or null when
     *  the materials cannot supply any acceptable grid. Valid while {@link #areMaterialsDirty} is false. */
    @Nullable
    private List<ItemStack> cachedCraftGrid = null;

    @Nullable
    private RecipeHolder<CraftingRecipe> currentRecipe;
    /** Every recipe the cycle offers, sorted by id: those the current blueprint grid matches (a null
     *  {@code variantGrid}), plus, with variants enabled, those it matches after a swap. When this holds 2+ entries the
     *  machine's GUI shows a cycle-output button. Server-side only. */
    private List<Match> matches = List.of();
    /** Index into {@link #matches} of the output the player picked. Preserved by recipe id across grid/material
     *  changes and reloads; between recomputes it always names an entry the blueprint itself matches. */
    private int selectedIndex = 0;
    /** A recipe id to select on the next {@link #tick()} once matches recompute: read from NBT on load, or the variant
     *  just picked (whose blueprint rewrite triggers that recompute). */
    @Nullable
    private String pendingSelectedRecipeId = null;
    private ItemStack assumedResult = ItemStack.EMPTY;

    /** Names the item tags whose members the cycle offers as variants; null (the default) means no variants. */
    @Nullable
    private Supplier<? extends List<? extends String>> variantTagSource = null;
    private boolean acceptSubstitutes = false;

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

    /** Offers variants in the output cycle: recipes the blueprint matches once its items are swapped for other
     *  members of the item tags {@code tagIds} names (read at every blueprint recompute, so a config edit applies to
     *  the next blueprint change). Selecting a variant <em>rewrites</em> {@code invBlueprint}, so only a machine whose
     *  blueprint is a phantom pattern (the Advanced Crafting Table) may call this. */
    public void enableVariants(Supplier<? extends List<? extends String>> tagIds) {
        this.variantTagSource = tagIds;
        isBlueprintDirty = true;
    }

    /** Lets {@link #craft()} use materials other than the blueprint's items when they keep the selected recipe
     *  satisfied and its result the same item. Off by default. */
    public void setAcceptSubstitutes(boolean accept) {
        if (acceptSubstitutes != accept) {
            acceptSubstitutes = accept;
            areMaterialsDirty = true;
        }
    }

    public boolean isAcceptSubstitutes() {
        return acceptSubstitutes;
    }

    public void onInventoryChange(ItemHandlerSimple inv) {
        if (inv == invBlueprint) {
            isBlueprintDirty = true;
        } else if (inv == invMaterials) {
            areMaterialsDirty = true;
        }
    }

    /** The blueprint's current stacks, one per grid slot (not copies; never mutate them). */
    private List<ItemStack> blueprintGrid() {
        List<ItemStack> items = new ArrayList<>(getSize());
        for (int s = 0; s < getSize(); s++) {
            items.add(invBlueprint.getStackInSlot(s));
        }
        return items;
    }

    /** Creates a CraftingInput from the current blueprint slots. */
    private CraftingInput createBlueprintInput() {
        return CraftingInput.of(width, height, blueprintGrid());
    }

    /** @return True if anything changed, false otherwise */
    public boolean tick() {
        if (tile.getLevel().isClientSide()) {
            throw new IllegalStateException("Never call this on the client side!");
        }
        if (isBlueprintDirty) {
            List<ItemStack> grid = blueprintGrid();
            CraftingInput input = CraftingInput.of(width, height, grid);
            // Remember which output the player had picked so a grid edit (or a reload) doesn't
            // silently switch it: prefer a pending pick (a variant just applied, or the id read from NBT),
            // falling back to the live selection.
            String desiredId = pendingSelectedRecipeId != null ? pendingSelectedRecipeId
                : currentRecipe != null ? CraftingUtil.recipeId(currentRecipe) : null;
            matches = findMatches(grid, input);
            pendingSelectedRecipeId = null;
            selectedIndex = indexOfGridRecipe(desiredId);
            currentRecipe = matches.isEmpty() ? null : matches.get(selectedIndex).recipe();
            updateAssumedResult(input);
            isBlueprintDirty = false;
            areMaterialsDirty = true; // re-check materials against new recipe
            return true;
        }
        return false;
    }

    /** Cycles the selected output among the matching recipes (dir = +1 next, -1 previous). No-op
     *  unless 2+ recipes match. Refreshes the assumed result so the tile re-syncs it to the client. Landing on a
     *  variant rewrites the blueprint to that variant's items and recomputes at once.
     *  @return true if the selection changed. */
    public boolean cycleOutput(int dir) {
        if (isBlueprintDirty) {
            // A grid edit in the same tick must win: never apply a variant built from the pre-edit blueprint.
            tick();
        }
        if (matches.size() <= 1) {
            return false;
        }
        selectedIndex = Math.floorMod(selectedIndex + dir, matches.size());
        Match picked = matches.get(selectedIndex);
        if (picked.isVariant()) {
            applyVariant(picked);
            return true;
        }
        currentRecipe = picked.recipe();
        updateAssumedResult(createBlueprintInput());
        areMaterialsDirty = true; // re-check materials/output against the newly selected result
        return true;
    }

    /** Makes a variant the blueprint: writes its swapped grid into the phantom blueprint and recomputes, landing the
     *  selection on that recipe. No hidden "locked variant" state: what the grid shows is what gets crafted. */
    private void applyVariant(Match variant) {
        pendingSelectedRecipeId = CraftingUtil.recipeId(variant.recipe());
        List<ItemStack> grid = variant.variantGrid();
        for (int s = 0; s < getSize(); s++) {
            invBlueprint.setStackInSlot(s, grid.get(s).copy());
        }
        isBlueprintDirty = true;
        tick();
    }

    /** Number of recipes in the cycle (0 if none). The GUI shows the cycle button when &gt;1. */
    public int getMatchCount() {
        return matches.size();
    }

    /** Index of the currently selected output within the cycle. */
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

    /** Index of the entry the blueprint itself matches (never a variant: a variant's grid is not the blueprint) with
     *  the given recipe id, else of the first such entry. */
    private int indexOfGridRecipe(@Nullable String id) {
        int first = -1;
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            if (match.isVariant()) {
                continue;
            }
            if (id != null && CraftingUtil.recipeId(match.recipe()).equals(id)) {
                return i;
            }
            if (first < 0) {
                first = i;
            }
        }
        return Math.max(first, 0);
    }

    // --- Cycle list: the grid's own matches plus the variants ---

    /** The unified, id-sorted cycle list for {@code grid}: its own matches, plus (variants enabled, and only when the
     *  grid matches something itself) the recipes it matches after swapping one blueprint item for another member of
     *  a configured tag. A recipe reachable both ways stays the grid's own match. */
    private List<Match> findMatches(List<ItemStack> grid, CraftingInput input) {
        List<Match> found = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (RecipeHolder<CraftingRecipe> recipe : CraftingUtil.findMatchingRecipes(input, tile.getLevel())) {
            found.add(new Match(recipe, null));
            ids.add(CraftingUtil.recipeId(recipe));
        }
        if (variantTagSource == null || found.isEmpty()) {
            return found;
        }
        List<? extends String> tagIds = variantTagSource.get();
        if (tagIds == null || tagIds.isEmpty()) {
            return found;
        }
        Map<String, Match> variants = new LinkedHashMap<>();
        collectVariants(grid, tagIds, ids, variants);
        found.addAll(variants.values());
        found.sort(Comparator.comparing((Match match) -> CraftingUtil.recipeId(match.recipe())));
        return found;
    }

    /** For each distinct blueprint stack, each configured tag containing its item, and each other member of that tag:
     *  swap every slot holding that stack for the member and keep the recipes the swapped grid matches (first swap
     *  wins per recipe). Stops after {@link #MAX_VARIANT_LOOKUPS} recipe lookups. */
    private void collectVariants(List<ItemStack> grid, List<? extends String> tagIds, Set<String> ownIds,
        Map<String, Match> variants) {
        ItemStackKey[] keys = slotKeys(grid);
        Set<ItemStackKey> visited = new HashSet<>();
        Map<String, List<Item>> membersByTag = new HashMap<>();
        int lookups = 0;
        for (ItemStackKey key : keys) {
            if (key == null || !visited.add(key)) {
                continue;
            }
            Item item = key.baseStack.getItem();
            Set<Item> tried = new HashSet<>();
            tried.add(item);
            for (String tagId : tagIds) {
                List<Item> members = membersByTag.computeIfAbsent(tagId, WorkbenchCrafting::tagMembers);
                if (!members.contains(item)) {
                    continue;
                }
                for (Item member : members) {
                    if (!tried.add(member)) {
                        continue;
                    }
                    if (lookups++ >= MAX_VARIANT_LOOKUPS) {
                        return;
                    }
                    List<ItemStack> swapped = swap(grid, keys, key, new ItemStack(member));
                    CraftingInput swappedInput = CraftingInput.of(width, height, swapped);
                    for (RecipeHolder<CraftingRecipe> recipe : CraftingUtil.findMatchingRecipes(swappedInput,
                        tile.getLevel())) {
                        String id = CraftingUtil.recipeId(recipe);
                        if (!ownIds.contains(id)) {
                            variants.putIfAbsent(id, new Match(recipe, swapped));
                        }
                    }
                }
            }
        }
    }

    /** The items in the item tag a config entry names ({@code namespace:path}, optionally {@code #}-prefixed); empty
     *  for an unparseable or unknown tag. {@code getTagOrEmpty} is the one iteration API shared by every node. */
    private static List<Item> tagMembers(String tagId) {
        Identifier id = Identifier.tryParse(tagId.startsWith("#") ? tagId.substring(1) : tagId);
        if (id == null) {
            return List.of();
        }
        List<Item> members = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
            members.add(holder.value());
        }
        return members;
    }

    // --- Grid swaps ---

    /** Per grid slot: the key of its stack (item + components), or null when the slot is empty. */
    private static ItemStackKey[] slotKeys(List<ItemStack> grid) {
        ItemStackKey[] keys = new ItemStackKey[grid.size()];
        for (int s = 0; s < keys.length; s++) {
            ItemStack stack = grid.get(s);
            keys[s] = stack.isEmpty() ? null : new ItemStackKey(stack);
        }
        return keys;
    }

    /** A copy of {@code grid} with every slot holding {@code target} replaced by {@code replacement} (same count). */
    private static List<ItemStack> swap(List<ItemStack> grid, ItemStackKey[] keys, ItemStackKey target,
        ItemStack replacement) {
        List<ItemStack> swapped = new ArrayList<>(grid.size());
        for (int s = 0; s < grid.size(); s++) {
            ItemStack stack = grid.get(s);
            swapped.add(target.equals(keys[s]) ? replacement.copyWithCount(stack.getCount()) : stack.copy());
        }
        return swapped;
    }

    private void updateAssumedResult(CraftingInput input) {
        if (currentRecipe == null) {
            assumedResult = ItemStack.EMPTY;
        } else {
            assumedResult = assemble(currentRecipe, input);
        }
    }

    private ItemStack assemble(RecipeHolder<CraftingRecipe> recipe, CraftingInput input) {
        //? if >=26.1 {
        return recipe.value().assemble(input);
        //?} else {
        /*return recipe.value().assemble(input, tile.getLevel().registryAccess());*/
        //?}
    }

    // --- Crafting ---

    /** @return True if {@link #craft()} might return true. */
    public boolean canCraft() {
        if (currentRecipe == null || isBlueprintDirty) {
            return false;
        }
        if (!invResult.canFullyAccept(assumedResult)) {
            return false;
        }
        refreshCraftGrid();
        return cachedCraftGrid != null;
    }

    /** Attempts to craft a single item.
     *  @return True if the crafting happened, false otherwise. */
    public boolean craft() {
        if (isBlueprintDirty || currentRecipe == null) {
            return false;
        }
        refreshCraftGrid();
        return cachedCraftGrid != null && craftExact(cachedCraftGrid);
    }

    /** Re-resolves the grid to craft from, only when the materials (or blueprint, recipe or substitute toggle) changed
     *  since the last time: the substitute search is never run per tick. */
    private void refreshCraftGrid() {
        if (areMaterialsDirty) {
            areMaterialsDirty = false;
            cachedCraftGrid = resolveCraftGrid();
        }
    }

    /** The blueprint itself when the materials hold its exact stacks; else, with substitutes accepted, a swapped grid
     *  the materials can supply; else null. */
    @Nullable
    private List<ItemStack> resolveCraftGrid() {
        List<ItemStack> grid = blueprintGrid();
        if (hasExactStacks(grid)) {
            return grid;
        }
        return acceptSubstitutes ? findSubstituteGrid(grid) : null;
    }

    /** Searches the swaps of {@code grid} the materials can supply and the selected recipe still accepts. Candidates
     *  per distinct blueprint stack are that stack itself first, then every stack type in the materials whose swap
     *  alone (all its slots, so woods are never mixed) keeps the recipe matching with the same result item; the joint
     *  choice over all blueprint stacks is then searched, original-first, for the first grid the materials can fully
     *  supply. Bounded by {@link #MAX_SUBSTITUTE_COMBINATIONS}. */
    @Nullable
    private List<ItemStack> findSubstituteGrid(List<ItemStack> grid) {
        Map<ItemStackKey, Integer> available = new HashMap<>();
        for (int s = 0; s < invMaterials.getSlots(); s++) {
            ItemStack stack = invMaterials.getStackInSlot(s);
            if (!stack.isEmpty()) {
                available.merge(new ItemStackKey(stack), stack.getCount(), Integer::sum);
            }
        }
        if (available.isEmpty()) {
            return null;
        }

        ItemStackKey[] keys = slotKeys(grid);
        List<ItemStackKey> blueprintKeys = new ArrayList<>(); // distinct blueprint stacks, in slot order
        Map<ItemStackKey, Integer> needed = new HashMap<>(); // how many of each the grid holds
        int[] slotKey = new int[keys.length]; // index into blueprintKeys per slot, -1 when empty
        for (int s = 0; s < keys.length; s++) {
            slotKey[s] = -1;
            if (keys[s] != null) {
                if (!needed.containsKey(keys[s])) {
                    blueprintKeys.add(keys[s]);
                }
                needed.merge(keys[s], grid.get(s).getCount(), Integer::sum);
                slotKey[s] = blueprintKeys.indexOf(keys[s]);
            }
        }

        List<List<ItemStackKey>> candidates = new ArrayList<>();
        for (ItemStackKey blueprintKey : blueprintKeys) {
            List<ItemStackKey> options = new ArrayList<>();
            options.add(blueprintKey);
            for (Map.Entry<ItemStackKey, Integer> material : available.entrySet()) {
                ItemStackKey candidate = material.getKey();
                // A stack type with fewer items than the slots need could never supply the swap on its own.
                if (candidate.equals(blueprintKey) || material.getValue() < needed.get(blueprintKey)) {
                    continue;
                }
                if (acceptedBySelectedRecipe(swap(grid, keys, blueprintKey, candidate.baseStack))) {
                    options.add(candidate);
                }
            }
            candidates.add(options);
        }

        int[] pick = new int[blueprintKeys.size()];
        for (int tried = 0; tried < MAX_SUBSTITUTE_COMBINATIONS; tried++) {
            if (!nextCombination(pick, candidates)) {
                return null; // every combination (the original-only one was ruled out by the caller) is spent
            }
            List<ItemStack> trial = new ArrayList<>(grid.size());
            for (int s = 0; s < keys.length; s++) {
                if (slotKey[s] < 0) {
                    trial.add(ItemStack.EMPTY);
                } else {
                    ItemStackKey chosen = candidates.get(slotKey[s]).get(pick[slotKey[s]]);
                    trial.add(chosen.baseStack.copyWithCount(grid.get(s).getCount()));
                }
            }
            if (acceptedBySelectedRecipe(trial) && hasExactStacks(trial)) {
                return trial;
            }
        }
        return null;
    }

    /** Advances {@code pick} (one candidate index per blueprint stack, last stack fastest) to the next combination.
     *  @return false when it wrapped around to all-original, i.e. every combination has been visited. */
    private static boolean nextCombination(int[] pick, List<List<ItemStackKey>> candidates) {
        for (int d = pick.length - 1; d >= 0; d--) {
            if (++pick[d] < candidates.get(d).size()) {
                return true;
            }
            pick[d] = 0;
        }
        return false;
    }

    /** Whether {@code trial} still satisfies the selected recipe and assembles the same result item. */
    private boolean acceptedBySelectedRecipe(List<ItemStack> trial) {
        CraftingInput input = CraftingInput.of(width, height, trial);
        if (!currentRecipe.value().matches(input, tile.getLevel())) {
            return false;
        }
        ItemStack result = assemble(currentRecipe, input);
        return !result.isEmpty() && result.getItem() == assumedResult.getItem();
    }

    private boolean hasExactStacks(List<ItemStack> grid) {
        Map<ItemStackKey, Integer> required = new HashMap<>();
        for (ItemStack req : grid) {
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

    /** Implementation of {@link #craft()}, extracting the stacks of {@code grid} from materials. */
    private boolean craftExact(List<ItemStack> grid) {
        // Temporary storage for items currently in the "crafting grid"
        NonNullList<ItemStack> gridContents = NonNullList.withSize(getSize(), ItemStack.EMPTY);

        // Step 1: Extract required items from materials
        for (int s = 0; s < getSize(); s++) {
            ItemStack bpt = grid.get(s);
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
        ItemStack result = assemble(currentRecipe, craftInput);
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
