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
        // Runs on both sides — the overlay is client-authoritative (no networking). The client
        // records which tile to draw; the server side just shows the explanatory action-bar text.
        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof IAdvDebugTarget target)) {
            // Not a debug target — don't swallow the interaction, let other handlers run.
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            BCAdvDebugging.INSTANCE.setClientTarget(context.getClickedPos());
        } else if (context.getPlayer() != null) {
            context.getPlayer().sendOverlayMessage(target.getAdvDebugMessage());
        }
        return InteractionResult.SUCCESS;
    }

    /** Returns true if the given player is in creative mode or is holding a debugger in either hand. */
    public static boolean isShowDebugInfo(Player player) {
        return player.getAbilities().instabuild
            || player.getMainHandItem().getItem() instanceof ItemDebugger
            || player.getOffhandItem().getItem() instanceof ItemDebugger;
    }
}
