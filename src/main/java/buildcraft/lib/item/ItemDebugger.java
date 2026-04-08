/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.lib.debug.BCAdvDebugging;
import buildcraft.lib.debug.IAdvDebugTarget;

public class ItemDebugger extends Item {
    public ItemDebugger(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (blockEntity == null) {
            return InteractionResult.FAIL;
        }
        if (blockEntity instanceof IAdvDebugTarget target) {
            BCAdvDebugging.setCurrentDebugTarget(target);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    /** Returns true if the given player is in creative mode or is holding a debugger in either hand. */
    public static boolean isShowDebugInfo(Player player) {
        return player.getAbilities().instabuild
            || player.getMainHandItem().getItem() instanceof ItemDebugger
            || player.getOffhandItem().getItem() instanceof ItemDebugger;
    }
}
