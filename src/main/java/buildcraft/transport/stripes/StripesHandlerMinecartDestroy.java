/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import java.util.Collections;
import java.util.List;

import net.minecraft.world.entity.player.Player;
//? if >=1.21.11 {
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
//?} else {
/*import net.minecraft.world.entity.vehicle.AbstractMinecart;*/
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.Container;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerBlock;

import buildcraft.lib.misc.MachineRefusals;

/** Breaks a minecart in front of the stripes pipe and sends it, and whatever it carried, back down the pipe. Run by
 *  {@code StripesRegistry#handleBlock} from the pipe's tick, ahead of its digging, as 7.1.x ran its BLOCK_BREAK
 *  handlers (8.0.x registered this handler but never dispatched it). */
public enum StripesHandlerMinecartDestroy implements IStripesHandlerBlock {
    INSTANCE;

    @Override
    public boolean handle(Level world, BlockPos pos, Direction direction, Player player, IStripesActivator activator) {
        AABB box = new AABB(pos.relative(direction));
        List<AbstractMinecart> minecarts = world.getEntitiesOfClass(AbstractMinecart.class, box);

        if (!minecarts.isEmpty()) {
            Collections.shuffle(minecarts);
            AbstractMinecart cart = minecarts.get(0);
            // Breaking a cart is what a player's attack does, so a claim mod is asked the way it is asked about a
            // player's swing (the attack-entity event; nothing is hit). A refusal is a refused click: it is counted
            // and ends the dispatch, and the cart is left alone.
            if (NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, cart)).isCanceled()) {
                MachineRefusals.record(player);
                return false;
            }
            // If the minecart has an inventory (e.g. chest/hopper minecart), extract its contents
            if (cart instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack s = container.getItem(i);
                    if (!s.isEmpty()) {
                        container.setItem(i, ItemStack.EMPTY);
                        if (container.getItem(i).isEmpty()) {
                            activator.sendItem(s, direction);
                        }
                    }
                }
            }
            ItemStack cartItem = cart.getPickResult();
            cart.discard();
            if (cartItem != null && !cartItem.isEmpty()) {
                activator.sendItem(cartItem, direction);
            }
            return true;
        }
        return false;
    }
}
