/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

import buildcraft.lib.misc.BlockUtil;

/** Stripes handler that tries to use items by calling their useOn method — acts like
 *  a player right-clicking with the item. This is a simplified port from the 1.12.2
 *  dispenser handler, which used BlockSource + DispenseItemBehavior. In 1.21.11 we
 *  just simulate a right-click at the target position instead. */
public enum StripesHandlerDispenser implements IStripesHandlerItem {
    INSTANCE;

    @Override
    public boolean handle(Level world,
                          BlockPos pos,
                          Direction direction,
                          ItemStack stack,
                          Player player,
                          IStripesActivator activator) {
        if (!(world instanceof ServerLevel)) {
            return false;
        }

        // Set the item in the player's hand
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        BlockPos target = pos.relative(direction);

        // Each click asks the right-click event first (BlockUtil), as a player's does, and a refusal ends the handler
        // as it ends a player's click: strategy 2 names the pipe, though its effect lands on the target, so trying it
        // after strategy 1 was refused would carry the item to the block a claim just refused. StripesRegistry ends
        // the whole dispatch on a refusal too. Strategy 1 is also how a block item is placed in front of the pipe.

        // Strategy 1: Click directly on the block in front of the pipe.
        // This handles items that affect the clicked block itself (e.g. bonemeal on crops,
        // hoe on dirt, shears on leaves).
        BlockHitResult hitDirect = new BlockHitResult(
            Vec3.atCenterOf(target), direction.getOpposite(), target, false
        );
        BlockUtil.ItemUse direct = BlockUtil.tryUseItemOnBlock(world, player, InteractionHand.MAIN_HAND, stack, hitDirect);
        if (direct != BlockUtil.ItemUse.PASSED) {
            return direct == BlockUtil.ItemUse.USED;
        }

        // Strategy 2: Click on the pipe-side face of the pipe block, with the face
        // pointing outward (direction). Items like FlintAndSteelItem place their effect
        // at clickedPos.relative(clickedFace), so this puts the effect at
        // pos.relative(direction) = the block in front.
        BlockHitResult hitFromPipe = new BlockHitResult(
            Vec3.atCenterOf(pos), direction, pos, false
        );
        BlockUtil.ItemUse fromPipe = BlockUtil.tryUseItemOnBlock(world, player, InteractionHand.MAIN_HAND, stack, hitFromPipe);
        if (fromPipe != BlockUtil.ItemUse.PASSED) {
            return fromPipe == BlockUtil.ItemUse.USED;
        }

        // Strategy 3: Use in air (right-click with no target block). The activator has a say first: where this use
        // lands is the item's own aim, which a robot answering to a second player asks that player about.
        if (!activator.mayUseInAir(player, stack)) {
            return false;
        }
        return BlockUtil.useItemInAir(world, player, InteractionHand.MAIN_HAND, stack);
    }
}
