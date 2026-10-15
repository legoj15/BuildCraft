/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/** One JEI entry for the Integration Table. JEI-free on purpose so {@link IntegrationRecipeCollector} is
 *  unit-testable.
 *
 *  @param id          Unique, stable key.
 *  @param center      The stack for the table's centre slot.
 *  @param expansions  The stacks for the ring around it, one slot each.
 *  @param outputs     What the table crafts first; any further stacks are lookup aliases (variants JEI keeps as
 *                     separate entries that come from this same recipe), shown cycling in the output slot.
 *  @param microJoules The laser energy the table needs. */
public record IntegrationRecipeJei(
        String id,
        ItemStack center,
        List<ItemStack> expansions,
        List<ItemStack> outputs,
        long microJoules
) {}
