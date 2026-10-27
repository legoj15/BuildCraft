/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.viewer;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;

/** One viewer entry for the Programming Table: one option of one {@code IProgrammingRecipe}, shown the way the table
 *  itself presents it. Viewer-free on purpose so {@link ProgrammingRecipeCollector} is unit-testable.
 *
 *  @param id          Unique, stable key: the recipe id plus the option's index over all the recipe's options.
 *  @param inputs      Stacks the table accepts for this recipe (a size &gt; 1 list cycles in the input slot).
 *  @param output      What the table crafts from {@code inputs.get(0)} with this option.
 *  @param grid        The page of the table's option grid this option is on (a recipe with more options than
 *                     the grid has cells spreads over pages) — context only, drawn but never offered to a viewer as an
 *                     input or output.
 *  @param gridIndex   The cell on that page the player clicks for this option; the category highlights it.
 *  @param microJoules The laser energy the table needs for this option. */
public record ProgrammingRecipeEntry(
        String id,
        List<ItemStack> inputs,
        ItemStack output,
        List<ItemStack> grid,
        int gridIndex,
        long microJoules
) {
    /** The recipe this entry is an option of: {@link #id} up to the option index {@link ProgrammingRecipeCollector}
     *  appended. What the viewer's "+" transfer asks the server to run. */
    public String recipeId() {
        return id.substring(0, id.lastIndexOf('/'));
    }

    /** The index of this entry's option over ALL the recipe's options: the number {@link #id} ends with, and what the
     *  table selects (it is the same on every page; {@link #gridIndex} is the cell within the page). */
    public int optionIndex() {
        return Integer.parseInt(id.substring(id.lastIndexOf('/') + 1));
    }

    /** The id of the board the clicked cell programs (null for a non-board cell): the option the viewer's "+" transfer
     *  asks the server to select. Matched by id rather than grid index so a client whose board registry differs from
     *  the server's cannot select a different board than the one shown. */
    public String boardId() {
        ItemStack option = grid.get(gridIndex);
        RedstoneBoardNBT<?> board = option.getItem() instanceof ItemRedstoneBoard
                ? ItemRedstoneBoard.getBoardNBT(option) : null;
        return board == null ? null : board.getID();
    }
}
