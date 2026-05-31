/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.google.common.collect.ImmutableList;

import net.minecraft.client.resources.model.geometry.BakedQuad;

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

    /** Renders the pipe body (cutout layer) directly into a VertexConsumer.
     *  For coloured pipes, call renderMaskOverlay() separately with a translucent buffer. */
    public static void renderDirect(TilePipeHolder tile, PoseStack.Pose pose, VertexConsumer buffer, int light) {
        if (tile == null || tile.getPipe() == null) return;
        renderDirect(tile.getPipe().getModel(), pose, buffer, light);
    }

    /** Renders the colour mask overlay for painted pipes into a TRANSLUCENT buffer.
     *  Must be called AFTER renderDirect() so the cutout pass writes depth for
     *  frame pixels but leaves glass pixels with no depth — allowing the mask quads
     *  to pass depth test for glass and fail for frame. No Z-fighting.
     *
     *  @param alpha tint intensity (0-255). 76 matches overlay_stained.png's 30% tint. */
    public static void renderMaskOverlay(TilePipeHolder tile, PoseStack.Pose pose,
            VertexConsumer buffer, int light, int alpha) {
        if (tile == null || tile.getPipe() == null) return;
        renderMaskOverlay(tile.getPipe().getModel(), pose, buffer, light, alpha);
    }

    /** Cutout-layer overload taking a pre-built {@link PipeModelKey} rather than a live tile, so
     *  callers without a {@link TilePipeHolder} — e.g. the blueprint/snapshot preview, which
     *  reconstructs the key offline from captured NBT — can render the pipe body. */
    public static void renderDirect(PipeModelKey modelKey, PoseStack.Pose pose, VertexConsumer buffer, int light) {
        if (modelKey == null) return;
        PipeBaseCutoutKey key = new PipeBaseCutoutKey(modelKey);
        List<MutableQuad> quads = PipeModelCacheBase.generator.generateCutoutMutable(key);
        for (MutableQuad q : quads) {
            q.lighti(light);
            q.render(pose, buffer);
        }
    }

    /** Mask-overlay overload taking a pre-built {@link PipeModelKey}. See
     *  {@link #renderMaskOverlay(TilePipeHolder, PoseStack.Pose, VertexConsumer, int, int)}. */
    public static void renderMaskOverlay(PipeModelKey modelKey, PoseStack.Pose pose,
            VertexConsumer buffer, int light, int alpha) {
        if (modelKey == null) return;
        PipeBaseCutoutKey key = new PipeBaseCutoutKey(modelKey);
        List<MutableQuad> quads = PipeBaseModelGenStandard.INSTANCE.generateMaskMutable(key, alpha);
        for (MutableQuad q : quads) {
            q.lighti(light);
            q.render(pose, buffer);
        }
    }
}
