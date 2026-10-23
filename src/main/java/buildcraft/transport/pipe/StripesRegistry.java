/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    // Copy-on-write lists: a dispatch walks each priority's list as it was when the walk reached it, so a handler may
    // add or remove handlers (itself included) while it is being asked without breaking the walk; registration is
    // rare, dispatch is not.
    private final EnumMap<EnumHandlerPriority, List<IStripesHandlerItem>> itemHandlers = new EnumMap<>(EnumHandlerPriority.class);
    private final EnumMap<EnumHandlerPriority, List<IStripesHandlerBlock>> blockHandlers = new EnumMap<>(EnumHandlerPriority.class);

    StripesRegistry() {
        for (EnumHandlerPriority priority : EnumHandlerPriority.VALUES) {
            itemHandlers.put(priority, new CopyOnWriteArrayList<>());
            blockHandlers.put(priority, new CopyOnWriteArrayList<>());
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

    /**
     * Takes an item handler out again, whatever priority it was added at; false if it was not registered. On this
     * class only, not on the addon-facing {@link IStripesRegistry}: it exists so a test that registers its own
     * handler can remove it afterwards instead of leaving it first in every later dispatch of the run.
     */
    public boolean removeHandler(IStripesHandlerItem handler) {
        boolean removed = false;
        for (List<IStripesHandlerItem> list : itemHandlers.values()) {
            removed |= list.removeIf(registered -> registered == handler);
        }
        return removed;
    }

    /** {@link #removeHandler(IStripesHandlerItem)} for a block handler. */
    public boolean removeHandler(IStripesHandlerBlock handler) {
        boolean removed = false;
        for (List<IStripesHandlerBlock> list : blockHandlers.values()) {
            removed |= list.removeIf(registered -> registered == handler);
        }
        return removed;
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
                long refusals = MachineRefusals.count(player);
                if (handler.handle(world, pos, direction, player, activator)) {
                    return true;
                }
                // As in handleItem: a refused ask ends the dispatch, so a later handler does not act on what a claim
                // just refused.
                if (MachineRefusals.count(player) != refusals) {
                    return false;
                }
            }
        }
        return false;
    }
}
