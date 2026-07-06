/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.fluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Shared quad-emission kit for BuildCraft's tile fluid renderers (tank, distiller, heat
 * exchanger). Every method is node-agnostic pure geometry: the caller supplies the
 * {@link PoseStack.Pose} and {@link VertexConsumer} (sourced from the retained-mode submit
 * collector on 26.2+, or {@code renderBuffers().bufferSource()} on pre-26.2 nodes), so nothing
 * here needs version directives.
 * <p>
 * These were three byte-identical copies of {@code quad}/{@code quadHorizontal}/{@code posU}/
 * {@code posV} (plus two near-identical six-face box helpers), each ported separately from
 * 1.12.2 {@code FluidRenderer}. UV is position-based to match the old {@code FluidRenderer.TexMap}
 * behaviour: the texture renders at natural 1:1 scale and clips at face edges.
 */
public final class FluidRenderer {
    private FluidRenderer() {}

    /**
     * Emit a vertical quad with position-based UV mapping. UV derives from each vertex's
     * block-space position: N/S faces ({@code nx == 0}) take U from X, E/W faces take U from Z;
     * V always comes from Y.
     */
    public static void quad(PoseStack.Pose pose, VertexConsumer builder, TextureAtlasSprite sprite,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float nx, float ny, float nz,
            float r, float g, float b, float a, int light, int overlay) {
        builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a)
                .setUv(posU(sprite, nx, x1, z1), posV(sprite, y1))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a)
                .setUv(posU(sprite, nx, x2, z2), posV(sprite, y2))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x3, y3, z3).setColor(r, g, b, a)
                .setUv(posU(sprite, nx, x3, z3), posV(sprite, y3))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x4, y4, z4).setColor(r, g, b, a)
                .setUv(posU(sprite, nx, x4, z4), posV(sprite, y4))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Emit a horizontal quad with position-based UV mapping. U derives from X, V from Z (TexMap.XZ). */
    public static void quadHorizontal(PoseStack.Pose pose, VertexConsumer builder, TextureAtlasSprite sprite,
            float x1, float x2, float z1, float z2, float y,
            float nx, float ny, float nz,
            float r, float g, float b, float a, int light, int overlay) {
        builder.addVertex(pose, x1, y, z1).setColor(r, g, b, a)
                .setUv(sprite.getU(x1), sprite.getV(z1))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y, z1).setColor(r, g, b, a)
                .setUv(sprite.getU(x2), sprite.getV(z1))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y, z2).setColor(r, g, b, a)
                .setUv(sprite.getU(x2), sprite.getV(z2))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x1, y, z2).setColor(r, g, b, a)
                .setUv(sprite.getU(x1), sprite.getV(z2))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /**
     * Draw the six faces of an axis-aligned fluid box (four verticals via {@link #quad}, top and
     * bottom via {@link #quadHorizontal}) with position-based UV and {@code NO_OVERLAY}. Shared by
     * the distiller and heat-exchanger tank renders. The tank BER does <em>not</em> use this — it
     * conditionally omits faces for connected/gaseous tanks, so it drives {@link #quad}/
     * {@link #quadHorizontal} directly.
     */
    public static void fluidBox(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
            float minX, float minZ, float maxX, float maxZ, float fluidTop, float fluidBottom,
            float r, float g, float b, float a, int light) {
        int overlay = OverlayTexture.NO_OVERLAY;

        // North face (-Z)
        quad(pose, buffer, sprite, minX, fluidTop, minZ, maxX, fluidTop, minZ,
                maxX, fluidBottom, minZ, minX, fluidBottom, minZ,
                0, 0, -1, r, g, b, a, light, overlay);
        // South face (+Z)
        quad(pose, buffer, sprite, minX, fluidBottom, maxZ, maxX, fluidBottom, maxZ,
                maxX, fluidTop, maxZ, minX, fluidTop, maxZ,
                0, 0, 1, r, g, b, a, light, overlay);
        // West face (-X)
        quad(pose, buffer, sprite, minX, fluidBottom, minZ, minX, fluidBottom, maxZ,
                minX, fluidTop, maxZ, minX, fluidTop, minZ,
                -1, 0, 0, r, g, b, a, light, overlay);
        // East face (+X)
        quad(pose, buffer, sprite, maxX, fluidTop, minZ, maxX, fluidTop, maxZ,
                maxX, fluidBottom, maxZ, maxX, fluidBottom, minZ,
                1, 0, 0, r, g, b, a, light, overlay);
        // Top face
        quadHorizontal(pose, buffer, sprite, minX, maxX, maxZ, minZ, fluidTop,
                0, 1, 0, r, g, b, a, light, overlay);
        // Bottom face
        quadHorizontal(pose, buffer, sprite, minX, maxX, maxZ, minZ, fluidBottom,
                0, -1, 0, r, g, b, a, light, overlay);
    }

    /** Compute U from position: E/W faces ({@code nx != 0}) take U from Z, otherwise from X. */
    private static float posU(TextureAtlasSprite sprite, float nx, float x, float z) {
        return sprite.getU(nx != 0 ? z : x);
    }

    /** Compute V from Y position (1-y flips top-to-bottom). */
    private static float posV(TextureAtlasSprite sprite, float y) {
        return sprite.getV(1.0f - y);
    }
}
