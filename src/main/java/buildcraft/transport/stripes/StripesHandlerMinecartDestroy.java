/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
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
 *  handlers (8.0.x registered this handler but never dispatched it). Nothing it takes apart is lost or duplicated:
 *  an item goes down the pipe only once it is out of the cart, a cart with an item that will not come out is left
 *  standing, and a cart that names no item of its own to send is destroyed the way an attack would destroy it. */
public enum StripesHandlerMinecartDestroy implements IStripesHandlerBlock {
    INSTANCE;

    /** A blow that breaks any cart in one hit: a cart breaks once its damage passes 40, and a blow adds ten times its
     *  strength. */
    private static final float BREAKING_BLOW = 100.0F;

    @Override
    public boolean handle(Level world, BlockPos pos, Direction direction, Player player, IStripesActivator activator) {
        if (!(world instanceof ServerLevel serverLevel)) {
            return false;
        }
        AABB box = new AABB(pos.relative(direction));
        List<AbstractMinecart> minecarts = world.getEntitiesOfClass(AbstractMinecart.class, box);

        if (!minecarts.isEmpty()) {
            AbstractMinecart cart = minecarts.get(world.getRandom().nextInt(minecarts.size()));
            // Breaking a cart is what a player's attack does, so a claim mod is asked the way it is asked about a
            // player's swing (the attack-entity event; nothing is hit). A refusal is a refused click: it is counted
            // and ends the dispatch, and the cart is left alone.
            if (NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, cart)).isCanceled()) {
                MachineRefusals.record(player);
                return false;
            }
            // If the minecart has an inventory (e.g. chest/hopper minecart), extract its contents. A stack is sent
            // only once it is out of its slot: sending one that is still there would duplicate it.
            boolean moved = false;
            boolean stuck = false;
            if (cart instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack s = container.getItem(i);
                    if (!s.isEmpty()) {
                        container.setItem(i, ItemStack.EMPTY);
                        if (container.getItem(i).isEmpty()) {
                            activator.sendItem(s, direction);
                            moved = true;
                        } else {
                            stuck = true;
                        }
                    }
                }
            }
            // A slot that would not empty still holds its item, and breaking the cart around it would delete it (or
            // spill it on the ground, for a cart that drops its contents when it goes). Sending a copy first would
            // duplicate it. So the cart stays, with what is left in it; the pipe has done what it could.
            if (stuck) {
                return moved;
            }
            ItemStack cartItem = cart.getPickResult();
            if (cartItem == null || cartItem.isEmpty()) {
                // A cart that names no item to hand back would simply vanish if discarded: it is destroyed its own
                // way instead, as an attack on it would, so whatever it drops lands in the world. One that cannot be
                // destroyed that way (invulnerable) stays, emptied.
                destroyAsAnAttack(serverLevel, player, cart);
                return moved || cart.isRemoved();
            }
            cart.discard();
            activator.sendItem(cartItem, direction);
            return true;
        }
        return false;
    }

    /** Hits {@code cart} with a blow from {@code player} that breaks it, so it goes the way its own type breaks. */
    private static void destroyAsAnAttack(ServerLevel world, Player player, AbstractMinecart cart) {
        DamageSource blow = world.damageSources().playerAttack(player);
        //? if >=1.21.10 {
        cart.hurtServer(world, blow, BREAKING_BLOW);
        //?} else {
        /*cart.hurt(blow, BREAKING_BLOW);*/
        //?}
    }
}
