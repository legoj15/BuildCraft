/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import java.util.Collections;
import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

import buildcraft.lib.misc.MachineRefusals;

public enum StripesHandlerEntityInteract implements IStripesHandlerItem {
    INSTANCE;

    @Override
    public boolean handle(Level world,
                          BlockPos pos,
                          Direction direction,
                          ItemStack stack,
                          Player player,
                          IStripesActivator activator) {
        BlockPos target = pos.relative(direction);
        List<LivingEntity> entities = world.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(target)
        );
        Collections.shuffle(entities);
        for (LivingEntity entity : entities) {
            long refusals = MachineRefusals.count(player);
            //? if >=26.1 {
            InteractionResult interaction = player.interactOn(entity, InteractionHand.MAIN_HAND, entity.position());
            //?} else {
            /*InteractionResult interaction = player.interactOn(entity, InteractionHand.MAIN_HAND);*/
            //?}
            // A claim cancelling the interaction hands back whatever result it left on the event, the default PASS or
            // a consuming one alike (MachineRefusals sees the event itself): a refused click was no use of the item,
            // and ends the handler, as it ends a player's, instead of trying the next mob.
            if (MachineRefusals.count(player) != refusals) {
                return false;
            }
            if (interaction.consumesAction()) {
                return true;
            }
        }
        return false;
    }
}
