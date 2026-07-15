/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.recipe;

import java.util.List;

import net.minecraft.world.inventory.Slot;

/**
 * The menu-side contract the shared {@link BCRecipeBookComponent} needs from a BuildCraft crafting
 * table: its phantom blueprint grid, result slot, and grid dimensions. Implemented by
 * {@code ContainerAutoCraftItems} and {@code ContainerAdvancedCraftingTable} — both
 * {@link buildcraft.lib.gui.ContainerBCCrafting} subclasses. Server-safe (no client-only types), so
 * the container classes can implement it directly.
 */
public interface IBCRecipeBookMenu {

    /** The result/display output slot the recipe book writes the crafted result into. */
    Slot getResultSlot();

    /** The phantom blueprint input grid slots (row-major) the recipe book lays a chosen pattern into. */
    List<Slot> getInputGridSlots();

    /** Width of the input grid, in columns. */
    int getGridWidth();

    /** Height of the input grid, in rows. */
    int getGridHeight();
}
