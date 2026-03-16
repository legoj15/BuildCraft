/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.item;

import javax.annotation.Nullable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;

import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

/**
 * A simple item that creates a pluggable from a {@link PluggableDefinition} when placed on a pipe.
 * Used for Pipe Plug and Pipe Power Adaptor.
 */
public class ItemPluggableSimple extends Item implements IItemPluggable {
    private final PluggableDefinition definition;

    public ItemPluggableSimple(Item.Properties properties, PluggableDefinition definition) {
        super(properties);
        this.definition = definition;
    }

    @Nullable
    @Override
    public PipePluggable onPlace(ItemStack stack, IPipeHolder holder, Direction side, Player player,
                                  InteractionHand hand) {
        if (definition.creator != null) {
            return definition.creator.createSimplePluggable(definition, holder, side);
        }
        return null;
    }
}
