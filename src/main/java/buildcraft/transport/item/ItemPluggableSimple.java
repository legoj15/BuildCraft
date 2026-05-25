/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.item;

import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
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
    /** Per-side bounding box of the pluggable this item produces, for the placement-preview outline.
     *  Null means use the interface's default (gate-sized) — fine for any pluggable whose box already
     *  matches that. Provided explicitly for {@code PluggableBlocker} / {@code PluggablePowerAdaptor}
     *  whose panels overshadow the default slab. */
    @Nullable
    private final Function<Direction, AABB> placementBoundingBoxLookup;

    public ItemPluggableSimple(Item.Properties properties, PluggableDefinition definition) {
        this(properties, definition, null, null);
    }

    public ItemPluggableSimple(Item.Properties properties, PluggableDefinition definition,
                                @Nullable Predicate<IPipeHolder> placementPredicate) {
        this(properties, definition, placementPredicate, null);
    }

    public ItemPluggableSimple(Item.Properties properties, PluggableDefinition definition,
                                @Nullable Predicate<IPipeHolder> placementPredicate,
                                @Nullable Function<Direction, AABB> placementBoundingBoxLookup) {
        super(properties);
        this.definition = definition;
        this.placementPredicate = placementPredicate;
        this.placementBoundingBoxLookup = placementBoundingBoxLookup;
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

    @Nonnull
    @Override
    public AABB getPlacementBoundingBox(@Nonnull ItemStack stack, Direction side) {
        if (placementBoundingBoxLookup != null) {
            return placementBoundingBoxLookup.apply(side);
        }
        return IItemPluggable.super.getPlacementBoundingBox(stack, side);
    }
}
