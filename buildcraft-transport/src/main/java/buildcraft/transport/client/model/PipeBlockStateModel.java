/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.util.List;

import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.DynamicBlockStateModel;

import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * A DynamicBlockStateModel for the pipe_holder block that generates pipe quads
 * based on the actual pipe state at each block position. The quads are rendered
 * as part of the chunk mesh AND used by Minecraft for the block-breaking overlay.
 *
 * <p>This model wraps the vanilla-baked pipe_holder model (for particle texture),
 * and overrides collectParts() to produce the correct pipe geometry per position
 * by delegating to PipeModelCacheAll.
 *
 * <p>SimpleModelWrapper is a Java record and takes a ChunkSectionLayer parameter
 * in its 4-arg constructor to control the render type per part.
 */
public class PipeBlockStateModel implements DynamicBlockStateModel {
    private final BlockStateModel vanillaDelegate;

    public PipeBlockStateModel(BlockStateModel vanillaDelegate) {
        this.vanillaDelegate = vanillaDelegate;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockModelPart> parts) {
        // MovingBlockRenderState (used for breaking overlay) always returns null from
        // getBlockEntity() — fall through to the underlying level field to find the tile.
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null && level instanceof MovingBlockRenderState movingState && movingState.level != null) {
            be = movingState.level.getBlockEntity(pos);
        }
        if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
            TextureAtlasSprite particle = vanillaDelegate.particleIcon();

            // Get cutout quads from the pipe model cache — CUTOUT discards transparent pixels
            List<BakedQuad> cutoutQuads = PipeModelCacheAll.getCutoutModel(tile);
            if (!cutoutQuads.isEmpty()) {
                QuadCollection.Builder builder = new QuadCollection.Builder();
                for (BakedQuad quad : cutoutQuads) {
                    builder.addUnculledFace(quad);
                }
                parts.add(new SimpleModelWrapper(builder.build(), true, particle, ChunkSectionLayer.CUTOUT));
            }

            // Also get translucent quads (painted pipe overlays) — TRANSLUCENT for alpha blending
            List<BakedQuad> translucentQuads = PipeModelCacheAll.getTranslucentModel(tile);
            if (!translucentQuads.isEmpty()) {
                QuadCollection.Builder builder = new QuadCollection.Builder();
                for (BakedQuad quad : translucentQuads) {
                    builder.addUnculledFace(quad);
                }
                parts.add(new SimpleModelWrapper(builder.build(), false, particle, ChunkSectionLayer.TRANSLUCENT));
            }
        }
    }

    @Override
    @Deprecated
    public TextureAtlasSprite particleIcon() {
        return vanillaDelegate.particleIcon();
    }

    @Override
    public TextureAtlasSprite particleIcon(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        // Resolve the actual pipe texture — the vanilla delegate has the pipe_holder.json
        // particle which is always the wooden pipe texture.
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null && level instanceof net.minecraft.client.renderer.block.MovingBlockRenderState movingState
                && movingState.level != null) {
            be = movingState.level.getBlockEntity(pos);
        }
        if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
            PipeDefinition def = tile.getPipe().getDefinition();
            if (def.textures != null && def.textures.length > 0) {
                TextureAtlasSprite sprite = buildcraft.lib.misc.SpriteUtil.getSprite(def.textures[0]);
                if (sprite != null && sprite != buildcraft.lib.misc.SpriteUtil.missingSprite()) {
                    return sprite;
                }
            }
        }
        return vanillaDelegate.particleIcon();
    }
}
