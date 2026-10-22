/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.transport;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface IStripesHandlerItem {

    /** Called to handle the given {@link ItemStack} within the world. Note that the player's inventory will be empty,
     * except that the target stack will be set into its {@link InteractionHand#MAIN_HAND}. Any items left in the players
     * inventory will be returned back through the activator with
     * {@link IStripesActivator#sendItem(ItemStack, Direction)}
     * 
     * @param world
     * @param pos
     * @param direction
     * @param stack The {@link ItemStack} being used
     * @param player
     * @param activator
     * @return True if this used the item, false otherwise (note that this handler MUST NOT return false if it has
     *         changed the world in any way). A false answer lets the next handler try the same stack, unless a click
     *         this handler made as the machine's fake player was refused by a protection mod: a refused click ends
     *         the whole dispatch, as it ends a player's, and the stack is not offered to the handlers after it.
     *         BuildCraft's own click helpers ({@code BlockUtil#useItemOnBlock}, {@code tryUseItemOnBlock} and
     *         {@code useItemInAir}), a cancelled mob interaction and a refused break in the shears handler record
     *         their refusals; a handler that clicks by other means records one with {@code MachineRefusals#record}
     *         for {@code player}. A handler that was refused should return false, not true. */
    boolean handle(Level world, BlockPos pos, Direction direction, ItemStack stack, Player player, IStripesActivator activator);
}

