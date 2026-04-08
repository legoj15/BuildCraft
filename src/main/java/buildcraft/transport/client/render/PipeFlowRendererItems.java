/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.Direction;

import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;

import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.pipe.flow.PipeFlowItems;

/** Renders items travelling through item pipes.
 *
 *  NOTE: As of the 1.21.11 port, item model rendering is handled directly by
 *  RenderPipeHolder.submitItems() using pre-resolved ItemStackRenderState instances
 *  (following the vanilla CampfireRenderer pattern). This renderer class is kept
 *  only for the colour overlay quad data. The render() method is a no-op since
 *  PipeFlowItems is now excluded from the renderContents() call in RenderPipeHolder.
 */
public enum PipeFlowRendererItems implements IPipeFlowRenderer<PipeFlowItems> {
    INSTANCE;

    private static final MutableQuad[] COLOURED_QUADS = new MutableQuad[6];
    private static boolean colouredQuadsInitialized = false;

    /** Lazily initializes the coloured overlay quads the first time they're needed.
     *  This ensures the sprite atlas has been baked before we try to read UV coordinates. */
    private static void ensureColouredQuads() {
        if (colouredQuadsInitialized) return;
        colouredQuadsInitialized = true;

        Vector3f center = new Vector3f();
        Vector3f radius = new Vector3f(0.2f, 0.2f, 0.2f);

        var sprite = BCTransportSprites.COLOUR_ITEM_BOX;
        UvFaceData uvs = new UvFaceData();
        uvs.minU = (float) sprite.getInterpU(0);
        uvs.maxU = (float) sprite.getInterpU(1);
        uvs.minV = (float) sprite.getInterpV(0);
        uvs.maxV = (float) sprite.getInterpV(1);

        for (Direction face : Direction.values()) {
            MutableQuad q = ModelUtil.createFace(face, center, radius, uvs);
            q.setCalculatedDiffuse();
            COLOURED_QUADS[face.ordinal()] = q;
        }
    }

    /** Returns the colour overlay quads for dye-tagged items (lazily initialized).
     *  Used by RenderPipeHolder.renderColourOverlay(). */
    public static MutableQuad[] getColouredQuads() {
        ensureColouredQuads();
        return COLOURED_QUADS;
    }

    @Override
    public void render(PipeFlowItems flow, double x, double y, double z, float partialTicks, VertexConsumer bb, PoseStack.Pose pose) {
        // No-op: item rendering is now handled by RenderPipeHolder.submitItems()
        // using pre-resolved ItemStackRenderState instances from extractRenderState().
    }
}
