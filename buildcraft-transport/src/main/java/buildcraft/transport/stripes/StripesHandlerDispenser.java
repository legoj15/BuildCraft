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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

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

        // Simulate a right-click on the face of the block at the pipe position,
        // looking in the pipe's direction. Items like FlintAndSteelItem place fire
        // at clickedPos.relative(clickedFace), so by targeting pos with face=direction
        // the effect lands at pos.relative(direction) — exactly where the pipe points.
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(pos), direction, pos, false
        );
        UseOnContext ctx = new UseOnContext(world, player, InteractionHand.MAIN_HAND, stack, hit);
        if (stack.useOn(ctx).consumesAction()) {
            return true;
        }

        // Try use (right-click in air)
        if (stack.getItem().use(world, player, InteractionHand.MAIN_HAND).consumesAction()) {
            return true;
        }

        return false;
    }
}
