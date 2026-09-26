/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/** One JEI entry for the Programming Table: one option of one {@code IProgrammingRecipe}, shown the way the table
 *  itself presents it. JEI-free on purpose so {@link ProgrammingRecipeCollector} is unit-testable.
 *
 *  @param id          Unique, stable key: the recipe id plus the option's grid index.
 *  @param inputs      Stacks the table accepts for this recipe (a size &gt; 1 list cycles in the input slot).
 *  @param output      What the table crafts from {@code inputs.get(0)} with this option.
 *  @param grid        The table's option grid, cropped to the cells its GUI can show — context only, drawn but
 *                     never offered to JEI as an input or output.
 *  @param gridIndex   The cell the player clicks for this option; the category highlights it.
 *  @param microJoules The laser energy the table needs for this option. */
public record ProgrammingRecipeJei(
        String id,
        List<ItemStack> inputs,
        ItemStack output,
        List<ItemStack> grid,
        int gridIndex,
        long microJoules
) {}
