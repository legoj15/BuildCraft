/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.compat.jei;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.item.ItemPluggableLens;

/** The JEI identity of the silicon pluggable items, kept JEI-free so it is unit-testable ({@code JeiSubtypeKeysTest}).
 *
 *  <p>Each key is the smallest value that uniquely identifies a <em>visual</em> variant — equal keys merge into one
 *  JEI entry, different keys get their own entry and their own recipe lookup. Without them JEI collapses every
 *  variant into a single entry, so R/U on any lens, gate or facade showed the recipes for all of them at once. Each
 *  key's {@code toString()} is also the legacy subtype text 1.21.1's JEI matches old bookmarks against, so the
 *  strings are pinned: change one and those bookmarks stop resolving. */
public final class SiliconJeiSubtypes {
    private SiliconJeiSubtypes() {}

    /** Lens / filter: colour × mode, as {@code "<colour>:<isFilter>"}; "clear" stands in for the no-colour lens. */
    public static String lensKey(ItemStack stack) {
        DyeColor colour = ItemPluggableLens.getColour(stack);
        boolean isFilter = ItemPluggableLens.isFilter(stack);
        return (colour == null ? "clear" : colour.getName()) + ":" + isFilter;
    }

    /** Gate: material + logic + modifier as {@code GateVariant.getVariantName()} encodes it — the same key the item
     *  model dispatch uses, so two gates with one key render identically. */
    public static String gateKey(ItemStack stack) {
        return ItemPluggableGate.getVariant(stack).getVariantName();
    }

    /** Facade: the whole stored "facade" compound — the wrapped block state(s), phased colour mappings and the hollow
     *  flag. {@code CompoundTag} has structural equals/hashCode on every node, so two facades of the same state(s)
     *  share an entry. The bulk Basic-facade case is a single small state, so comparisons stay cheap. */
    public static CompoundTag facadeKey(ItemStack stack) {
        return NBTUtilBC.getCompound(NBTUtilBC.getItemData(stack), "facade");
    }
}
