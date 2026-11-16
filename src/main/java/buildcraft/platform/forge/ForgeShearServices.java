/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WebBlock;

import buildcraft.platform.ShearServices;
import buildcraft.platform.hook.ShearableBlock;

/^*
 * {@link ShearServices} on MinecraftForge, which has no shearable-block extension on 26.3 (its {@code IForgeShearable}
 * stops at 26.1.2, and the 26.3 patches shear entities only): the blocks NeoForge marks {@code IShearable} (leaves,
 * seagrass, tall grass, vines and cobwebs, with its defaults: always shearable, no drops of their own), and any
 * {@link ShearableBlock}, BuildCraft's loader-neutral hook. The same list as the Fabric twin.
 *^/
public final class ForgeShearServices implements ShearServices {
    @Override
    public boolean isShearableBlock(Block block) {
        return block instanceof ShearableBlock || isVanillaShearable(block);
    }

    @Override
    public boolean isShearableAt(Player player, ItemStack tool, Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof ShearableBlock shearable) {
            return shearable.isShearable(player, tool, level, pos);
        }
        return isVanillaShearable(block);
    }

    @Override
    public List<ItemStack> shear(Player player, ItemStack tool, Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ShearableBlock shearable
            ? shearable.onSheared(player, tool, level, pos) : List.of();
    }

    /^* The vanilla classes NeoForge patches {@code IShearable} onto (subclasses included, as there). ^/
    private static boolean isVanillaShearable(Block block) {
        return block instanceof LeavesBlock || block instanceof SeagrassBlock || block instanceof TallGrassBlock
            || block instanceof VineBlock || block instanceof WebBlock;
    }
}
*///?}
