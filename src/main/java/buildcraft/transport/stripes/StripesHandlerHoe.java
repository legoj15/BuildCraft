/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

import buildcraft.lib.misc.BlockUtil;

public enum StripesHandlerHoe implements IStripesHandlerItem {
    INSTANCE;

    @Override
    public boolean handle(Level world,
                          BlockPos pos,
                          Direction direction,
                          ItemStack stack,
                          Player player,
                          IStripesActivator activator) {
        // The tag, not HoeItem: 26.3 deleted the class (hoes carry a BLOCK_TRANSFORMER component instead),
        // and every node tags its hoes, so one test serves all of them.
        if (!stack.is(ItemTags.HOES)) {
            return false;
        }

        BlockPos target = pos.relative(direction);

        // Try using on the target block from above. A refused click ends the handler, as it ends a player's: the
        // second try below is a click on another block.
        BlockUtil.ItemUse first = till(world, player, stack, target);
        if (first != BlockUtil.ItemUse.PASSED) {
            return first == BlockUtil.ItemUse.USED;
        }

        // If not facing up and that didn't work, try using on the block below from above
        return direction != Direction.UP && till(world, player, stack, target.below()) == BlockUtil.ItemUse.USED;
    }

    /** Right-clicks the hoe on the top of {@code target} as the pipe's fake player: the right-click-block event
     *  first, so a claim mod can refuse the till on every node (on 26.3 it is the only ask a till gets), then the
     *  hoe's use. */
    private static BlockUtil.ItemUse till(Level world, Player player, ItemStack stack, BlockPos target) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
        return BlockUtil.tryUseItemOnBlock(world, player, InteractionHand.MAIN_HAND, stack, hit);
    }
}
