/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** An item that decides for itself which enchantments it accepts (NeoForge's {@code supportsEnchantment}). */
public interface EnchantmentFilterItem {
    boolean acceptsEnchantment(ItemStack stack, Holder<Enchantment> enchantment);
}
