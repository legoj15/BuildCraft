/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.item;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;

import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.lib.misc.SoundUtil;

/**
 * A simple item that creates a pluggable from a {@link PluggableDefinition} when placed on a pipe.
 * Used for Pipe Plug and Pipe Power Adaptor.
 */
public class ItemPluggableSimple extends Item implements IItemPluggable {
    /** Predicate that checks if the pipe's behaviour accepts redstone-triggered MJ power.
     *  True for wooden, obsidian, and stripes pipes — pipes that need engine power to operate. */
    public static final Predicate<IPipeHolder> PIPE_BEHAVIOUR_ACCEPTS_RS_POWER = holder -> {
        if (holder.getPipe() == null) return false;
        return holder.getPipe().getBehaviour() instanceof IMjRedstoneReceiver;
    };

    private final PluggableDefinition definition;
    private final Predicate<IPipeHolder> placementPredicate;

    public ItemPluggableSimple(Item.Properties properties, PluggableDefinition definition) {
        this(properties, definition, null);
    }

    public ItemPluggableSimple(Item.Properties properties, PluggableDefinition definition,
                                @Nullable Predicate<IPipeHolder> placementPredicate) {
        super(properties);
        this.definition = definition;
        this.placementPredicate = placementPredicate;
    }

    @Nullable
    @Override
    public PipePluggable onPlace(ItemStack stack, IPipeHolder holder, Direction side, Player player,
                                  InteractionHand hand) {
        if (placementPredicate != null && !placementPredicate.test(holder)) {
            return null;
        }
        if (definition.creator != null) {
            SoundUtil.playBlockPlace(holder.getPipeWorld(), holder.getPipePos());
            return definition.creator.createSimplePluggable(definition, holder, side);
        }
        return null;
    }
}
