/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.transport.BCTransportPipes;

/** An item pipe reaching a stripes pipe's open end asks {@link PipeApi#extensionManager} to extend the line by one
 *  cell (the void pipe retracts it). Any other pipe, a stripes pipe (which could not connect to the one it would sit
 *  behind, as 7.1.x excluded it too), or a use with no stripes pipe to move (a robot's) falls through to the
 *  dispenser handler, which places the pipe in front. */
public class StripesHandlerPipes implements IStripesHandlerItem {

    @Override
    public boolean handle(Level world, BlockPos pos, Direction direction, ItemStack stack, Player player, IStripesActivator activator) {
        if (!(stack.getItem() instanceof IItemPipe)) {
            return false;
        }

        PipeDefinition pipeDefinition = ((IItemPipe) stack.getItem()).getDefinition();
        if (pipeDefinition.flowType == PipeApi.flowItems && pipeDefinition != BCTransportPipes.stripesItem) {
            if (PipeApi.extensionManager != null &&
                PipeApi.extensionManager.requestPipeExtension(world, pos, direction, activator, stack.copy())) {
                // Nothing goes back now: the manager sends what is left of the stack back once it has run.
                player.getInventory().clearContent();
                return true;
            }
        }

        return false;
    }
}
