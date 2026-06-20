/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import buildcraft.api.transport.pipe.IPipeBehaviourRenderer;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;

/** Renders the stripes pipe's small directional "laser" beam protruding from
 *  the pipe end in the direction it is pointing. The beam is a gold-colored
 *  4×1×1 pixel box, matching the 1.12.2 model pipes/stripes.json. */
@SuppressWarnings("deprecation")
public enum PipeBehaviourRendererStripes implements IPipeBehaviourRenderer<PipeBehaviourStripes> {
    INSTANCE;

    /** Cached quads for each direction (lazily built). */
    private static final MutableQuad[][] DIRECTION_QUADS = new MutableQuad[6][];

    //? if >=1.21.10 {
    /** Modern (>=1.21.10) entry: the BER passes its {@link SubmitNodeCollector}, so the beam geometry
     *  is queued via {@code submitCustomGeometry} (retained-mode "submit"). 26.2 removed the
     *  immediate-mode {@code renderBuffers()} path entirely; the collector path is identical on
     *  1.21.10/1.21.11/26.1/26.2. */
    public void render(PipeBehaviourStripes stripes, double x, double y, double z, float partialTicks,
                       SubmitNodeCollector collector, com.mojang.blaze3d.vertex.PoseStack poseStack) {
        Direction dir = stripes.direction;
        if (dir == null) return;
        MutableQuad[] quads = getQuads(dir);
        int light = beamLight(stripes);
        collector.submitCustomGeometry(poseStack, BCLibRenderTypes.cutoutBlockSheet(),
            (pose, consumer) -> drawBeam(quads, light, pose, consumer));
    }
    //?}

    @Override
    public void render(PipeBehaviourStripes stripes, double x, double y, double z,
                       float partialTicks, VertexConsumer bb, PoseStack.Pose pose) {
        Direction dir = stripes.direction;
        if (dir == null) return;

        MutableQuad[] quads = getQuads(dir);
        int light = beamLight(stripes);

        //? if <26.2 {
        // 1.21.1..26.1 classic immediate-mode path (this interface overload is only reached on those
        // nodes; >=1.21.10 BERs call the SubmitNodeCollector overload above). The BER does not pass a
        // shared VertexConsumer, so create a dedicated buffer.
        MultiBufferSource.BufferSource bufferSource =
            Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(BCLibRenderTypes.cutoutBlockSheet());
        drawBeam(quads, light, pose, buffer);
        bufferSource.endBatch(BCLibRenderTypes.cutoutBlockSheet());
        //?}
    }

    private static int beamLight(PipeBehaviourStripes stripes) {
        return buildcraft.lib.client.render.LightUtil.getLightCoords(
            stripes.pipe.getHolder().getPipeWorld(),
            stripes.pipe.getHolder().getPipePos()
        );
    }

    /** Draws the cached beam quads into {@code buffer}. Node-agnostic: {@code pose} / {@code buffer}
     *  come from the {@code submitCustomGeometry} lambda on >=1.21.10 and from the immediate-mode
     *  buffer source on 1.21.1. */
    private static void drawBeam(MutableQuad[] quads, int light, PoseStack.Pose pose, VertexConsumer buffer) {
        for (MutableQuad cached : quads) {
            // Copy so we don't permanently mutate the cached quad
            MutableQuad q = new MutableQuad(cached);
            q.lighti(light);
            q.render(pose, buffer);
        }
    }

    /** Get or build the quads for the given direction. */
    private static MutableQuad[] getQuads(Direction dir) {
        int idx = dir.ordinal();
        if (DIRECTION_QUADS[idx] == null) {
            DIRECTION_QUADS[idx] = buildQuads(dir);
        }
        return DIRECTION_QUADS[idx];
    }

    /** Build the 6 quads for a beam box pointing in the given direction.
     *  Base shape (WEST): from [0, 7.5, 7.5] to [4, 8.5, 8.5] in 1/16 block coords.
     *  Then rotated from WEST to the target direction around center (0.5, 0.5, 0.5). */
    private static MutableQuad[] buildQuads(Direction dir) {
        // Coordinates in block-space (0..1), matching the 1.12.2 model
        float minX = 0f / 16f;       // 0
        float maxX = 4f / 16f;       // 0.25
        float minY = 7.5f / 16f;     // 0.46875
        float maxY = 8.5f / 16f;     // 0.53125
        float minZ = 7.5f / 16f;
        float maxZ = 8.5f / 16f;

        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
            .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(Identifier.parse("minecraft:block/gold_block"));

        float u0 = sprite.getU(0);
        float u1 = sprite.getU(1);
        float v0 = sprite.getV(0);
        float v1 = sprite.getV(1);

        MutableQuad[] quads = new MutableQuad[6];

        // -Y face (bottom)
        quads[0] = makeQuad(
            minX, minY, maxZ,
            minX, minY, minZ,
            maxX, minY, minZ,
            maxX, minY, maxZ,
            0, -1, 0, u0, v0, u1, v1, sprite
        );
        // +Y face (top)
        quads[1] = makeQuad(
            minX, maxY, minZ,
            minX, maxY, maxZ,
            maxX, maxY, maxZ,
            maxX, maxY, minZ,
            0, 1, 0, u0, v0, u1, v1, sprite
        );
        // -Z face (north)
        quads[2] = makeQuad(
            maxX, maxY, minZ,
            maxX, minY, minZ,
            minX, minY, minZ,
            minX, maxY, minZ,
            0, 0, -1, u0, v0, u1, v1, sprite
        );
        // +Z face (south)
        quads[3] = makeQuad(
            minX, maxY, maxZ,
            minX, minY, maxZ,
            maxX, minY, maxZ,
            maxX, maxY, maxZ,
            0, 0, 1, u0, v0, u1, v1, sprite
        );
        // -X face (west / tip)
        quads[4] = makeQuad(
            minX, maxY, maxZ,
            minX, maxY, minZ,
            minX, minY, minZ,
            minX, minY, maxZ,
            -1, 0, 0, u0, v0, u1, v1, sprite
        );
        // +X face (east / base toward pipe)
        quads[5] = makeQuad(
            maxX, maxY, minZ,
            maxX, maxY, maxZ,
            maxX, minY, maxZ,
            maxX, minY, minZ,
            1, 0, 0, u0, v0, u1, v1, sprite
        );

        // Rotate from WEST to target direction around center
        if (dir != Direction.WEST) {
            for (MutableQuad q : quads) {
                q.rotate(Direction.WEST, dir, 0.5f, 0.5f, 0.5f);
            }
        }

        return quads;
    }

    private static MutableQuad makeQuad(
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float nx, float ny, float nz,
        float u0, float v0, float u1, float v1,
        TextureAtlasSprite sprite
    ) {
        MutableQuad q = new MutableQuad();
        q.vertex_0.positionf(x0, y0, z0).texf(u0, v0).normalf(nx, ny, nz).colourf(1, 1, 1, 1);
        q.vertex_1.positionf(x1, y1, z1).texf(u0, v1).normalf(nx, ny, nz).colourf(1, 1, 1, 1);
        q.vertex_2.positionf(x2, y2, z2).texf(u1, v1).normalf(nx, ny, nz).colourf(1, 1, 1, 1);
        q.vertex_3.positionf(x3, y3, z3).texf(u1, v0).normalf(nx, ny, nz).colourf(1, 1, 1, 1);
        q.setShade(false);
        q.setSprite(sprite);
        return q;
    }
}
