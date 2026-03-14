/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.util.List;

import com.mojang.blaze3d.vertex.VertexConsumer;

import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.block.model.BakedQuad;

import buildcraft.lib.client.model.MutableQuad;

import buildcraft.transport.client.model.PipeModelCacheBase.PipeBaseCutoutKey;
import buildcraft.transport.client.model.key.PipeModelKey;
import buildcraft.transport.tile.TilePipeHolder;

/** Utility class that generates BakedQuads for placed pipe blocks. Delegates to the model
 *  cache hierarchy to generate quads based on the pipe's current model key.
 *
 *  In NeoForge 1.21.11, the BakedModel interface has been completely redesigned.
 *  Rather than implementing BakedModel directly, pipe rendering is done through
 *  RenderPipeHolder (BlockEntityRenderer) which calls these methods. */
public class ModelPipe {

    public static List<BakedQuad> getCutoutQuads(TilePipeHolder tile) {
        if (tile == null || tile.getPipe() == null) {
            return ImmutableList.of();
        }
        return PipeModelCacheAll.getCutoutModel(tile);
    }

    public static List<BakedQuad> getTranslucentQuads(TilePipeHolder tile) {
        if (tile == null || tile.getPipe() == null) {
            return ImmutableList.of();
        }
        return PipeModelCacheAll.getTranslucentModel(tile);
    }

    /** Renders the pipe body (cutout layer) directly into a VertexConsumer,
     *  bypassing the BakedQuad conversion. Used by the BER. */
    public static void renderDirect(TilePipeHolder tile, VertexConsumer buffer, int light) {
        if (tile == null || tile.getPipe() == null) return;
        PipeModelKey modelKey = tile.getPipe().getModel();
        if (modelKey == null) return;
        PipeBaseCutoutKey key = new PipeBaseCutoutKey(modelKey);
        List<MutableQuad> quads = PipeModelCacheBase.generator.generateCutoutMutable(key);
        for (MutableQuad q : quads) {
            q.lighti(light);
            q.render(buffer);
        }
    }
}
