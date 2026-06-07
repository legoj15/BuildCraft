/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client;

import com.mojang.serialization.MapCodec;

//? if >=1.21.10 {
import net.minecraft.client.color.item.ItemTintSource;
//?}
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.transport.BCTransportItems;

/**
 * ItemTintSource that reads PIPE_COLOUR from a pipe item stack and returns
 * the dye colour value for tinting overlay quads. Returns white (0xFFFFFF)
 * when no colour is present so that white overlay geometry is invisible.
 */
//? if >=1.21.10 {
public final class PipeColourTintSource implements ItemTintSource {
//?} else {
/*public final class PipeColourTintSource implements net.minecraft.client.color.item.ItemColor {*/
//?}
    public static final PipeColourTintSource INSTANCE = new PipeColourTintSource();
    //? if >=1.21.10 {
    public static final MapCodec<PipeColourTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);
    //?}

    private PipeColourTintSource() {}

    /** Semi-transparent alpha for the overlay (76/255 ≈ 30%, matching overlay_stained.png). */
    private static final int OVERLAY_ALPHA = 76;

    /** Shared colour computation; modern {@code calculate} and 1.21.1 {@code getColor} both delegate. */
    private int computeColor(ItemStack stack) {
        DyeColor col = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (col != null) {
            return (OVERLAY_ALPHA << 24) | ColourUtil.getLightHex(col);
        }
        return (OVERLAY_ALPHA << 24) | 0xFFFFFF;
    }

    //? if >=1.21.10 {
    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        return computeColor(stack);
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
    //?} else {
    /*@Override
    public int getColor(ItemStack stack, int tintIndex) {
        return computeColor(stack);
    }*/
    //?}
}
