/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.common.IShearable;

import buildcraft.platform.ShearServices;
import buildcraft.platform.hook.ShearableBlock;

/**
 * {@link ShearServices} on NeoForge: the {@link IShearable} block extension, then BuildCraft's own {@link ShearableBlock}
 * (the loader-neutral hook; a block that is both answers as an {@code IShearable}).
 */
public final class NeoForgeShearServices implements ShearServices {
    @Override
    public boolean isShearableBlock(Block block) {
        return block instanceof IShearable || block instanceof ShearableBlock;
    }

    @Override
    public boolean isShearableAt(Player player, ItemStack tool, Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof IShearable shearable) {
            return shearable.isShearable(player, tool, level, pos);
        }
        return block instanceof ShearableBlock shearable && shearable.isShearable(player, tool, level, pos);
    }

    @Override
    public List<ItemStack> shear(Player player, ItemStack tool, Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof IShearable shearable) {
            return shearable.onSheared(player, tool, level, pos);
        }
        return block instanceof ShearableBlock shearable ? shearable.onSheared(player, tool, level, pos) : List.of();
    }
}
//?}
