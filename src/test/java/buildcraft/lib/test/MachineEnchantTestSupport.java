/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** What the enchantable Quarry / Mining Well game tests share: an enchantment looked up in the test level, an item
 *  carrying one the way an anvil leaves it, and a look into the chest that catches a machine's drops. */
public final class MachineEnchantTestSupport {

    private MachineEnchantTestSupport() {}

    public static Holder<Enchantment> enchantment(GameTestHelper gth, ResourceKey<Enchantment> key) {
        return gth.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    /** What an anvil leaves on the item and vanilla's placement copies onto the block entity: {@code item} carrying
     *  {@code enchantment} at {@code level}. */
    public static ItemStack enchantedItem(GameTestHelper gth, Item item, ResourceKey<Enchantment> enchantment,
            int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantment(gth, enchantment), level);
        return stack;
    }

    /** Whether the chest at {@code chestLocal} holds {@code item}. */
    public static boolean chestHolds(GameTestHelper gth, BlockPos chestLocal, Item item) {
        if (!(gth.getLevel().getBlockEntity(gth.absolutePos(chestLocal)) instanceof ChestBlockEntity chest)) {
            return false;
        }
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }
}
