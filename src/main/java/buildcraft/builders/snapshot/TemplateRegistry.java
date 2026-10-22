/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.api.core.EnumHandlerPriority;
import buildcraft.api.template.ITemplateHandler;
import buildcraft.api.template.ITemplateRegistry;

import buildcraft.lib.misc.MachineRefusals;

public enum TemplateRegistry implements ITemplateRegistry {
    INSTANCE;

    private final EnumMap<EnumHandlerPriority, List<ITemplateHandler>> handlers = new EnumMap<>(EnumHandlerPriority.class);

    TemplateRegistry() {
        for (EnumHandlerPriority priority : EnumHandlerPriority.VALUES) {
            handlers.put(priority, new ArrayList<>());
        }
    }

    @Override
    public void addHandler(ITemplateHandler handler, EnumHandlerPriority priority) {
        handlers.get(priority).add(handler);
    }

    /**
     * Offers the placement to each handler in priority order until one places it. A handler whose click a claim mod
     * refused (a {@link MachineRefusals} refusal for {@code player}: the right-click, the item gate, or the placement
     * itself, which is counted for the whole dispatch) ends the dispatch, as a refused click ends a player's, the way
     * {@code StripesRegistry#handleItem} does: the next handler would place into the same claimed cell by another
     * route.
     */
    @Override
    public boolean handle(Level world, BlockPos pos, Player player, ItemStack stack) {
        return MachineRefusals.countingPlacements(player, () -> {
            for (EnumHandlerPriority priority : EnumHandlerPriority.VALUES) {
                for (ITemplateHandler handler : handlers.get(priority)) {
                    long refusals = MachineRefusals.count(player);
                    if (handler.handle(world, pos, player, stack)) {
                        return true;
                    }
                    if (MachineRefusals.count(player) != refusals) {
                        return false;
                    }
                }
            }
            return false;
        });
    }
}
