/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * One JEI entry for the Assembly Table. Both regular single-output recipes
 * (chipsets, gates, lenses, plugs) and the cycling facade recipe collapse into
 * this shape: each input slot is a list of acceptable stacks (size &gt; 1
 * implies cycling), and the output column is a list (single-element for
 * regular recipes, many for facades).
 *
 * <p>{@code id} is the lookup key for {@link AssemblyRecipeJeiTypes#ASSEMBLY}
 * and must be unique per entry — for regular recipes it's
 * {@code registryName + ":" + outputItemId}, for the facade recipes it's the
 * recipe's registry name plus {@code :basic} or {@code :hollow}.
 *
 * <p>{@code focusLinkInputIndex} is the index into {@code inputSlots} of the
 * cycling input slot that should be focus-linked to the output slot (i.e. a
 * focus on a specific input ingredient narrows the output to the matching
 * variant, like the JEI planks→stairs example). Use {@code -1} when no link
 * is needed — regular single-output recipes don't need this since their
 * output already has one stack and cycling is per-slot. The facade entries
 * pass {@code 1} (the block slot, not the structure-pipe base slot).
 */
public record AssemblyRecipeJei(
        String id,
        List<List<ItemStack>> inputSlots,
        List<ItemStack> outputs,
        long microJoules,
        int focusLinkInputIndex
) {
    public AssemblyRecipeJei(String id, List<List<ItemStack>> inputSlots, List<ItemStack> outputs, long microJoules) {
        this(id, inputSlots, outputs, microJoules, -1);
    }
}
