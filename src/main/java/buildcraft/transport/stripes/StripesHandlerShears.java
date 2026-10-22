/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.IShearable;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.MachineRefusals;

public enum StripesHandlerShears implements IStripesHandlerItem {
    INSTANCE;

    @Override
    public boolean handle(Level world,
                          BlockPos pos,
                          Direction direction,
                          ItemStack stack,
                          Player player,
                          IStripesActivator activator) {
        if (!(stack.getItem() instanceof ShearsItem)) {
            return false;
        }

        BlockPos target = pos.relative(direction);
        BlockState state = world.getBlockState(target);
        Block block = state.getBlock();

        if (block instanceof IShearable shearableBlock) {
            if (shearableBlock.isShearable(player, stack, world, target)) {
                // Shearing takes the block away, so break protection decides first, as it does for the pipe's own
                // digging. A refusal is a refused click: it ends the dispatch, so the dispenser fallback after this
                // handler does not right-click the same block with the same shears. Asked by the handler's own player
                // (no second lease) stood on the block for the ask, where the pipe's digging and a robot ask from, so
                // a claim that judges where the actor stands answers them alike.
                if (world instanceof ServerLevel serverLevel
                        && !BlockUtil.canMachineBreakAs(serverLevel, target, player)) {
                    MachineRefusals.record(player);
                    return false;
                }
                List<ItemStack> drops = shearableBlock.onSheared(player, stack, world, target);
                stack.hurtAndBreak(1, player, player.getEquipmentSlotForItem(stack));
                world.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                for (ItemStack dropStack : drops) {
                    activator.sendItem(dropStack, direction);
                }
                return true;
            }
        }
        return false;
    }
}
