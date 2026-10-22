/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.template.ITemplateHandler;

import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.BlockUtil;

public enum TemplateHandlerDefault implements ITemplateHandler {
    INSTANCE;

    /**
     * Clicks {@code stack} (held in {@code player}'s main hand) on the top face of the target cell, the way a player
     * places a block. With protection checks on (the default) the click asks first like a player's does, through
     * {@link BlockUtil#tryUseItemOnBlock}: a claim mod can refuse the right-click, and NeoForge's placement capture
     * still asks about the placement itself. With {@link BCCoreConfig#minePlayerProtected} on, the item is used
     * straight away (the capture is NeoForge's own and still runs).
     */
    @Override
    public boolean handle(Level world, BlockPos pos, Player player, ItemStack stack) {
        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        if (BCCoreConfig.minePlayerProtected.get()) {
            return stack.useOn(new UseOnContext(world, player, InteractionHand.MAIN_HAND, stack, hitResult))
                .consumesAction();
        }
        return BlockUtil.tryUseItemOnBlock(world, player, InteractionHand.MAIN_HAND, stack, hitResult)
            == BlockUtil.ItemUse.USED;
    }
}
