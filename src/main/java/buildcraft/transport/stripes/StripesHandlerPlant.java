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

import buildcraft.api.crops.CropManager;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

import buildcraft.lib.misc.MachineRefusals;

public enum StripesHandlerPlant implements IStripesHandlerItem {
    INSTANCE;

    @Override
    public boolean handle(Level world,
                          BlockPos pos,
                          Direction direction,
                          ItemStack stack,
                          Player player,
                          IStripesActivator activator) {
        long refusals = MachineRefusals.count(player);
        if (CropManager.plantCrop(world, player, stack, pos.relative(direction).below())) {
            return true;
        }
        // A refused click ends the handler, as it ends a player's: the second try is a click on another block.
        return MachineRefusals.count(player) == refusals
            && CropManager.plantCrop(world, player, stack, pos.relative(direction));
    }
}
