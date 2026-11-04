/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.util.function.Predicate;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The extra equipment slots that accessory mods add (NeoForge: Curios, and Accessories on 1.21.1/1.21.10), so an item
 * BC treats as worn also counts in one of them. The mods are loader-specific, so the lookup is too.
 */
public interface AccessorySlotServices {
    /** Whether {@code entity} has a stack matching {@code filter} in any slot an installed accessory mod adds. */
    boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter);
}
