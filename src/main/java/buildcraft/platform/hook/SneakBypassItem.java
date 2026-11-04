/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/**
 * An item that lets a sneaking player still use the block they click (NeoForge's {@code doesSneakBypassUse}).
 * Vanilla skips the block whenever a sneaking player holds anything; the block is still used when each hand is empty
 * or holds such an item.
 */
public interface SneakBypassItem {
    boolean sneakBypassesUse(ItemStack stack, LevelReader level, BlockPos pos, Player player);
}
