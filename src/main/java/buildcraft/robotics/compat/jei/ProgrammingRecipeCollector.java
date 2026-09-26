/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.lib.recipe.ProgrammingRecipeRegistry;

import buildcraft.silicon.tile.TileProgrammingTable;

/** Turns every {@link IProgrammingRecipe} into JEI entries — one per option the table can actually program — at JEI
 *  plugin init. JEI-free, so the whole mapping is unit-tested ({@code ProgrammingRecipeCollectorTester}).
 *
 *  <p>{@code IProgrammingRecipe} has no "list your inputs" method — only {@code canCraft(stack)} (7.1.x's API), so
 *  inputs are found by probing each registered item's default stack. For the board recipe that finds the bare
 *  Redstone Board: exactly what the crafting table makes, so JEI "uses" on a freshly crafted board lands here. (The
 *  table also reprograms already-programmed boards; listing all of them as cycling inputs would make every entry
 *  look like it needed some other program first, so the blank board alone stands for "a board".) A recipe whose
 *  inputs all carry data no default stack has gets no entries rather than a misleading one.
 *
 *  <p>Only the first {@code 6 x 4} options are emitted: the table's GUI can click nothing past its grid, so JEI must
 *  not promise more. An option whose output is the same JEI entry as every input is a no-op here — the empty board
 *  offered to a blank board — and is skipped. Entries run recipe id, then grid position, i.e. the table's own
 *  cheapest-first order. */
public final class ProgrammingRecipeCollector {
    /** Cells in the table's option grid — the most options any recipe can offer through its GUI. */
    public static final int GRID_SIZE = TileProgrammingTable.OPTION_COLS * TileProgrammingTable.OPTION_ROWS;

    private ProgrammingRecipeCollector() {}

    public static List<ProgrammingRecipeJei> collect() {
        return collect(ProgrammingRecipeRegistry.INSTANCE.getRecipes(), defaultStacksOfEveryItem());
    }

    /** @param candidates The stacks to probe each recipe's {@code canCraft} with, in display order. */
    static List<ProgrammingRecipeJei> collect(Collection<IProgrammingRecipe> recipes, List<ItemStack> candidates) {
        List<IProgrammingRecipe> sorted = new ArrayList<>(recipes);
        sorted.sort(Comparator.comparing(IProgrammingRecipe::getId));

        List<ProgrammingRecipeJei> out = new ArrayList<>();
        for (IProgrammingRecipe recipe : sorted) {
            List<ItemStack> inputs = new ArrayList<>();
            for (ItemStack candidate : candidates) {
                if (recipe.canCraft(candidate)) {
                    inputs.add(candidate.copy());
                }
            }
            if (inputs.isEmpty()) {
                continue;
            }
            List<ItemStack> options = recipe.getOptions(TileProgrammingTable.OPTION_COLS, TileProgrammingTable.OPTION_ROWS);
            List<ItemStack> grid = new ArrayList<>();
            for (int i = 0; i < Math.min(options.size(), GRID_SIZE); i++) {
                ItemStack option = options.get(i);
                grid.add(option == null ? ItemStack.EMPTY : option.copy());
            }
            // Entries only ever read these; one immutable copy each is shared by every entry of the recipe.
            List<ItemStack> sharedInputs = List.copyOf(inputs);
            List<ItemStack> sharedGrid = List.copyOf(grid);

            for (int i = 0; i < sharedGrid.size(); i++) {
                ItemStack option = sharedGrid.get(i);
                if (option.isEmpty()) {
                    continue;
                }
                ItemStack output = recipe.craft(sharedInputs.get(0).copy(), option.copy());
                if (output.isEmpty() || isNoOp(sharedInputs, output)) {
                    continue;
                }
                out.add(new ProgrammingRecipeJei(recipe.getId() + "/" + i, sharedInputs, output, sharedGrid, i,
                        recipe.getEnergyCost(option)));
            }
        }
        return out;
    }

    private static boolean isNoOp(List<ItemStack> inputs, ItemStack output) {
        for (ItemStack input : inputs) {
            if (!RoboticsJeiSubtypes.sameVariant(input, output)) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> defaultStacksOfEveryItem() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                stacks.add(new ItemStack(item));
            }
        }
        return stacks;
    }
}
