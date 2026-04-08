/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client;

import java.util.List;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Block tint source for pipe_holder that returns dye colours for translucent
 * overlay quads (tintIndex=1).
 *
 * <p>MC 26.1 replaced the old BlockColor callback with {@link BlockTintSource}
 * objects, registered per-block via
 * {@link RegisterColorHandlersEvent.BlockTintSources}.
 * Each tint index maps to one entry in the List parameter.
 *
 * <p>All return values are ARGB (0xAARRGGBB) — MC 26.1 tint multiplication
 * uses the alpha channel, so returning bare RGB (0x00RRGGBB) would zero out
 * the quad's alpha, making it invisible.
 */
public class PipeBlockColourHandler {

    /** No-op tint: full-alpha white = no colour modification. */
    private static final BlockTintSource NO_TINT = state -> 0xFFFFFFFF;

    /** Semi-transparent alpha for the overlay (76/255 ≈ 30%, matching overlay_stained.png). */
    private static final int OVERLAY_ALPHA = 76;

    /** BlockTintSource for tintIndex=1 (translucent colour overlay quads).
     *  Returns ARGB with alpha=76 for semi-transparent stained-glass effect. */
    private static final BlockTintSource PIPE_COLOUR_TINT = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return (OVERLAY_ALPHA << 24) | 0xFFFFFF; // semi-transparent white fallback
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            var be = level.getBlockEntity(pos);
            if (!(be instanceof TilePipeHolder tile)) return (OVERLAY_ALPHA << 24) | 0xFFFFFF;
            if (tile.getPipe() == null) return (OVERLAY_ALPHA << 24) | 0xFFFFFF;
            DyeColor colour = tile.getPipe().getModel().colour;
            if (colour == null) return (OVERLAY_ALPHA << 24) | 0xFFFFFF;
            return (OVERLAY_ALPHA << 24) | ColourUtil.getLightHex(colour);
        }
    };

    /**
     * Register block tint sources for pipe_holder.
     * <ul>
     *   <li>tintIndex 0 → {@link #NO_TINT} (pipe body — no colour modification)</li>
     *   <li>tintIndex 1 → {@link #PIPE_COLOUR_TINT} (translucent dye overlay)</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onRegisterBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(NO_TINT, PIPE_COLOUR_TINT), BCTransportBlocks.PIPE_HOLDER.get());
    }
}
