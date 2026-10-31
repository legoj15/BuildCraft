/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.transport;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;

public interface IStripesActivator {
    boolean sendItem(@Nonnull ItemStack itemStack, Direction from);

    void dropItem(@Nonnull ItemStack itemStack, Direction from);

    /** Asked by a handler just before it uses {@code itemStack} with no block target (a use in the air), where the
     *  item's own aim from {@code player} decides what it touches. False withholds the use: the handler reports it as not
     *  handled and counts it a refused click, which ends the dispatch; the activator keeps its own account of why. The default allows it, for an
     *  activator that answers to no one beyond the player whose own events already judge the use. Few events do: the
     *  cells a bucket or a boat reaches from the player's aim post none, so BuildCraft's stripes pipe and robot
     *  override this to ask protection mods about them. */
    default boolean mayUseInAir(@Nonnull Player player, @Nonnull ItemStack itemStack) {
        return true;
    }
}
