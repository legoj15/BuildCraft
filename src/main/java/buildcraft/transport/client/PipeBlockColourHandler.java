/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
//?}
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

    //? if >=26.1 {
    // No-op tint: full-alpha white = no colour modification.
    private static final BlockTintSource NO_TINT = state -> 0xFFFFFFFF;

    // tintIndex=1 (colour overlay): returns full-alpha ARGB. The stained-glass transparency lives
    // in the geometry layer (mask-sprite alpha), not here — low alpha here would stack a second
    // multiply and make it ~invisible. So the tint contributes colour, not transparency.
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

    // Tint source for one (wrappedTintIndex, side) facade slot: resolves the wrapped block's tint
    // at the pipe position so biome/state-tinted facades render with the right colour.
    private record FacadeTintSource(int wrappedTintIndex, Direction side) implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            // Item / no-context: facade items render via their own model path — "no tint".
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
    //?} else {
    /*// 1.21.11 uses the old single BlockColor callback keyed by tintIndex (no per-index
    // BlockTintSource objects). One lambda replicates: idx 0 = no tint, idx 1 = pipe colour,
    // idx>=FACADE_TINT_BASE = facade slot decoded as (wrappedTintIndex, side).
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex <= 0) {
                return 0xFFFFFFFF;
            }
            if (tintIndex == 1) {
                if (level == null || pos == null) return 0xFFFFFFFF;
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof TilePipeHolder tile) || tile.getPipe() == null) return 0xFFFFFFFF;
                DyeColor colour = tile.getPipe().getModel().colour;
                if (colour == null) return 0xFFFFFFFF;
                return 0xFF000000 | ColourUtil.getLightHex(colour);
            }
            int facadeIdx = tintIndex - PlugBakerFacade.FACADE_TINT_BASE;
            int dirCount = Direction.values().length;
            int wrappedTintIndex = facadeIdx / dirCount;
            Direction side = Direction.values()[facadeIdx % dirCount];
            if (level == null || pos == null) return -1;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TilePipeHolder tile)) return -1;
            PipePluggable plug = tile.getPluggable(side);
            if (!(plug instanceof PluggableFacade facade)) return -1;
            BlockState wrappedState = facade.states.phasedStates[facade.activeState].stateInfo.state;
            return 0xFF000000 | Minecraft.getInstance().getBlockColors().getColor(wrappedState, level, pos, wrappedTintIndex);
        }, BCTransportBlocks.PIPE_HOLDER.get());
    }*/
    //?}
}
