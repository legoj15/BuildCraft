/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The loader's shearable-block extension (NeoForge {@code IShearable}). */
public interface ShearServices {
    /** True if the block type takes part in the loader's shearing protocol at all. */
    boolean isShearableBlock(Block block);

    /** True if the block at {@code pos} can be sheared by this player with this tool. */
    boolean isShearableAt(Player player, ItemStack tool, Level level, BlockPos pos);

    /** The drops of shearing the block at {@code pos}; call only after {@link #isShearableAt} said yes. The block stays. */
    List<ItemStack> shear(Player player, ItemStack tool, Level level, BlockPos pos);
}
