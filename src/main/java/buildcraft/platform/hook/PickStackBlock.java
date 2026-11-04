/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/** A block whose pick-block item depends on where the player aims (NeoForge's player-aware {@code getCloneItemStack}). */
public interface PickStackBlock {
    /** The item {@code player} picks from this block at {@code pos}, or null for the block's default. */
    @Nullable
    ItemStack pickedStack(LevelReader level, BlockPos pos, Player player);
}
