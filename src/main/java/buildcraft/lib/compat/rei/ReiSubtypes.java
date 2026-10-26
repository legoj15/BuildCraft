/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

import java.util.Objects;
import java.util.function.Function;

import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * REI's half of the subtype keys BuildCraft gives JEI ({@code JeiSubtypes}): registers an item comparator from a key
 * function, so the same key tells two stacks of an item apart in both viewers. Equal keys are one REI entry, different
 * keys are separate entries with their own lookups, and a {@code null} key is the bare item.
 */
public final class ReiSubtypes {
    private ReiSubtypes() {}

    /** @param key maps a stack to its identity; may return null for "no subtype" */
    public static void register(ItemComparatorRegistry registry, Item item, Function<ItemStack, ?> key) {
        registry.register((context, stack) -> Objects.hashCode(key.apply(stack)), item);
    }
}
