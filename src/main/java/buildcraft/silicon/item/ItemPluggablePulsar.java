/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.SoundUtil;

import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.PluggablePulsar;

public class ItemPluggablePulsar extends Item implements IItemPluggable {
    public ItemPluggablePulsar(Item.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public PipePluggable onPlace(@Nonnull ItemStack stack, IPipeHolder holder, Direction side, Player player,
        InteractionHand hand) {
        if (!canPlace(stack, holder, side)) {
            return null;
        }
        SoundUtil.playBlockPlace(holder.getPipeWorld(), holder.getPipePos());
        return new PluggablePulsar(BCSiliconPlugs.pulsar, holder, side);
    }

    /** Pulsars only go on pipes an engine could power (wooden, obsidian, stripes). */
    @Override
    public boolean canPlace(@Nonnull ItemStack stack, IPipeHolder holder, Direction side) {
        IPipe pipe = holder.getPipe();
        return pipe != null && pipe.getBehaviour() instanceof IMjRedstoneReceiver;
    }
}
