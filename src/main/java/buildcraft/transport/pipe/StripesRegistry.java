/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.api.core.EnumHandlerPriority;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerBlock;
import buildcraft.api.transport.IStripesHandlerItem;
import buildcraft.api.transport.IStripesRegistry;

import buildcraft.lib.misc.MachineRefusals;

public enum StripesRegistry implements IStripesRegistry {
    INSTANCE;

    private final EnumMap<EnumHandlerPriority, List<IStripesHandlerItem>> itemHandlers = new EnumMap<>(EnumHandlerPriority.class);
    private final EnumMap<EnumHandlerPriority, List<IStripesHandlerBlock>> blockHandlers = new EnumMap<>(EnumHandlerPriority.class);

    StripesRegistry() {
        for (EnumHandlerPriority priority : EnumHandlerPriority.VALUES) {
            itemHandlers.put(priority, new ArrayList<>());
            blockHandlers.put(priority, new ArrayList<>());
        }
    }

    @Override
    public void addHandler(IStripesHandlerItem handler, EnumHandlerPriority priority) {
        itemHandlers.get(priority).add(handler);
    }

    @Override
    public void addHandler(IStripesHandlerBlock handler, EnumHandlerPriority priority) {
        blockHandlers.get(priority).add(handler);
    }

    @Override
    public boolean handleItem(Level world,
                              BlockPos pos,
                              Direction direction,
                              ItemStack stack,
                              Player player,
                              IStripesActivator activator) {
        for (EnumHandlerPriority priority : EnumHandlerPriority.VALUES) {
            for (IStripesHandlerItem handler : itemHandlers.get(priority)) {
                long refusals = MachineRefusals.count(player);
                if (handler.handle(world, pos, direction, stack, player, activator)) {
                    return true;
                }
                // A click a claim mod refused for this player ends the dispatch, as it ends a player's: the handlers
                // after this one would click the same item on a neighbouring block the claim was never asked about
                // (the planting handler's refused soil, then the dispenser fallback placing the seed in the cell
                // above it).
                if (MachineRefusals.count(player) != refusals) {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handleBlock(Level world,
                               BlockPos pos,
                               Direction direction,
                               Player player,
                               IStripesActivator activator) {
        for (EnumHandlerPriority priority : EnumHandlerPriority.VALUES) {
            for (IStripesHandlerBlock handler : blockHandlers.get(priority)) {
                if (handler.handle(world, pos, direction, player, activator)) {
                    return true;
                }
            }
        }
        return false;
    }
}
