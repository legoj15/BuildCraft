/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;

import buildcraft.lib.misc.MachineEnchantments;

/**
 * The item of a mining machine that takes Silk Touch, Fortune and Efficiency at an anvil (the Quarry and the Mining
 * Well). Anvil only: the item stays out of the vanilla enchantable tags and leaves its enchantability alone, so an
 * enchanting table offers it nothing.
 *
 * <p>The anvil, {@code /enchant} and every other mechanism that applies an enchantment ask
 * {@link #supportsEnchantment}, which says yes to those three while the {@code machineEnchantments} option is on and
 * no to everything else (Unbreaking and Mending included). A creative-mode anvil ignores the answer, as it does for
 * every item.
 */
public class ItemEnchantableMachine extends BlockItem {

    public ItemEnchantableMachine(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return MachineEnchantments.enabled() && MachineEnchantments.isUseful(enchantment);
    }
}
