/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.item.ItemTintSource;
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
public final class PipeColourTintSource implements ItemTintSource {
    public static final PipeColourTintSource INSTANCE = new PipeColourTintSource();
    public static final MapCodec<PipeColourTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    private PipeColourTintSource() {}

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        DyeColor col = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (col != null) {
            return ColourUtil.getLightHex(col);
        }
        return 0xFFFFFF; // white — multiplies to no visual change on white overlay texture
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
