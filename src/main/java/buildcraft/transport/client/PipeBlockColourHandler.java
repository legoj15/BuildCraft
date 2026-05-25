/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.silicon.client.model.plug.PlugBakerFacade;
import buildcraft.silicon.plug.PluggableFacade;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Block tint sources for pipe_holder. The list pipe_holder registers covers:
 * <ul>
 *   <li>index 0 → {@link #NO_TINT} (pipe body — no colour modification)</li>
 *   <li>index 1 → {@link #PIPE_COLOUR_TINT} (translucent dye overlay)</li>
 *   <li>indices {@code [FACADE_TINT_BASE .. FACADE_TINT_LIST_SIZE)} → one
 *       {@link FacadeTintSource} per (wrapped-block tintindex, side) pair,
 *       matching the encoding written by
 *       {@link PlugBakerFacade#bakeForKey} so biome/state-tinted facades
 *       (grass, leaves, redstone, water-cauldron, …) pick up the wrapped
 *       block's colour at the pipe's position.</li>
 * </ul>
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

    /** BlockTintSource for tintIndex=1 (translucent colour overlay quads).
     *
     *  <p>Returns ARGB with alpha=255 (full opacity) — the actual stained-glass
     *  transparency lives in the geometry layer below: either the alpha-multiply
     *  the model gen applies to mask-sprite quads ({@code multColouri(0xFF,0xFF,0xFF,76)}
     *  in {@code generateTranslucentMutable}), or the 76-alpha pixels baked into
     *  {@code overlay_stained.png} on the fallback path. Returning alpha=76 here as
     *  well stacks a second multiplication on top of those, producing ~22 final alpha
     *  (≈9%) — basically invisible, which matched the regression we just hit. The
     *  1.12.2 path used a texture-alpha-only model; mirroring that here means the
     *  tint contributes colour, not transparency. */
    private static final BlockTintSource PIPE_COLOUR_TINT = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return 0xFFFFFFFF; // opaque white fallback (no colour, no alpha change)
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            var be = level.getBlockEntity(pos);
            if (!(be instanceof TilePipeHolder tile)) return 0xFFFFFFFF;
            if (tile.getPipe() == null) return 0xFFFFFFFF;
            DyeColor colour = tile.getPipe().getModel().colour;
            if (colour == null) return 0xFFFFFFFF;
            return 0xFF000000 | ColourUtil.getLightHex(colour);
        }
    };

    /**
     * Tint source for one (wrappedTintIndex, side) slot in the facade index
     * range. Resolves the wrapped block's BlockTintSource at the pipe's
     * position so biome/state-tinted facades render with the right colour.
     */
    private record FacadeTintSource(int wrappedTintIndex, Direction side) implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            // Item / no-context: facade items render via their own model path,
            // not through this tint source — so a plain "no tint" is correct.
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TilePipeHolder tile)) return -1;
            PipePluggable plug = tile.getPluggable(side);
            if (!(plug instanceof PluggableFacade facade)) return -1;
            BlockState wrappedState = facade.states.phasedStates[facade.activeState].stateInfo.state;
            BlockTintSource wrappedSource =
                Minecraft.getInstance().getBlockColors().getTintSource(wrappedState, wrappedTintIndex);
            if (wrappedSource == null) return -1;
            // Defensive 0xFF alpha — vanilla sources should already return ARGB with
            // alpha=0xFF (texture pixels load full-alpha by default), but the chunk
            // pipeline multiplies tint into quad alpha so a stray 0-alpha return would
            // make the facade invisible.
            return 0xFF000000 | wrappedSource.colorInWorld(wrappedState, level, pos);
        }
    }

    @SubscribeEvent
    public static void onRegisterBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        List<BlockTintSource> sources = new ArrayList<>(PlugBakerFacade.FACADE_TINT_LIST_SIZE);
        sources.add(NO_TINT);
        sources.add(PIPE_COLOUR_TINT);
        for (int data = 0; data < PlugBakerFacade.FACADE_TINT_MAX_DATA; data++) {
            for (Direction side : Direction.values()) {
                sources.add(new FacadeTintSource(data, side));
            }
        }
        event.register(sources, BCTransportBlocks.PIPE_HOLDER.get());
    }
}
