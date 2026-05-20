/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.client.render.tile.RenderPartCube;

/**
 * Client-side helper for the advanced-debug overlay. Draws filled, vertex-coloured boxes — either
 * as see-through volumes ({@link #renderTranslucentBox}, the Quarry's chunk highlights) or as solid
 * markers ({@link #renderSolidBox}, the Laser's cone cubes). The two differ only in render type:
 * translucent boxes skip depth writes so they layer cleanly, solid boxes write depth so they sort
 * correctly against the world (water, leaves, the block-selection outline, …).
 */
public final class DebugRenderHelper {
    private DebugRenderHelper() {}

    /**
     * Draws a translucent filled box at the given world-space {@link AABB}. Use a low-alpha
     * {@code argb} — the box does not write depth, so it layers over the world rather than sorting
     * against it.
     */
    public static void renderTranslucentBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                            Vec3 cameraPos, int argb) {
        renderBox(poseStack, bufferSource, box, cameraPos, argb, BCLibRenderTypes.debugFilled());
    }

    /**
     * Draws a solid filled box at the given world-space {@link AABB}. Use a full-alpha {@code argb}
     * — the box writes depth, so it occludes and is occluded by world geometry correctly.
     */
    public static void renderSolidBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                      Vec3 cameraPos, int argb) {
        renderBox(poseStack, bufferSource, box, cameraPos, argb, BCLibRenderTypes.debugSolid());
    }

    /**
     * Shared box emitter. {@code cameraPos} is the camera's world position; the box is translated
     * into camera space so it renders in the right place regardless of where the player is.
     */
    private static void renderBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                  Vec3 cameraPos, int argb, RenderType type) {
        VertexConsumer consumer = bufferSource.getBuffer(type);
        RenderPartCube cube = new RenderPartCube();
        cube.center.positiond(
            (box.minX + box.maxX) / 2.0 - cameraPos.x,
            (box.minY + box.maxY) / 2.0 - cameraPos.y,
            (box.minZ + box.maxZ) / 2.0 - cameraPos.z
        );
        cube.sizeX = box.getXsize();
        cube.sizeY = box.getYsize();
        cube.sizeZ = box.getZsize();
        // MutableVertex.colouri(int) unpacks ABGR (red in the low byte), so split the standard
        // 0xAARRGGBB argument into explicit channels — otherwise red and blue swap.
        cube.center.colouri(
            (argb >> 16) & 0xFF,
            (argb >> 8) & 0xFF,
            argb & 0xFF,
            (argb >>> 24) & 0xFF
        );
        cube.render(poseStack.last(), consumer);
    }
}
