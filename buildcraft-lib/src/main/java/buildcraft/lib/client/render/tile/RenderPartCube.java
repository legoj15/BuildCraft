/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import buildcraft.lib.client.model.MutableVertex;

/**
 * A variable-sized element (like an LED) that can render somewhere in a BER.
 * The {@link #center} vertex lets you modify the location, colour, lightmap,
 * and UV of the single element. This does not allow for different textures —
 * typically you call {@link #setWhiteTex()} so the colour alone controls appearance.
 * <p>
 * Ported from 1.12.2 RenderPartCube to use {@link PoseStack} + {@link VertexConsumer}.
 */
public class RenderPartCube {
    /** The centre of this element. */
    public final MutableVertex center = new MutableVertex();
    public double sizeX = 1 / 16.0, sizeY = 1 / 16.0, sizeZ = 1 / 16.0;

    /** Constructs a simple cube configured for a LED. */
    public RenderPartCube() {
        this(1 / 16.0, 1 / 16.0, 1 / 16.0);
    }

    public RenderPartCube(double x, double y, double z) {
        center.positiond(x, y, z);
    }

    /** Sets the texture coordinates to the centre of the white "missing" sprite,
     *  so the cube renders as a solid colour determined only by the vertex colour. */
    public void setWhiteTex() {
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        // MissingTextureAtlasSprite is a pure white sprite on the block atlas.
        TextureAtlasSprite white = atlas.getSprite(Identifier.withDefaultNamespace("missingno"));
        float u = (white.getU0() + white.getU1()) / 2f;
        float v = (white.getV0() + white.getV1()) / 2f;
        center.texf(u, v);
    }

    /**
     * Renders all 6 faces of this cube.
     * Colour, UV, and lightmap are taken from the {@link #center} vertex.
     */
    public void render(PoseStack.Pose pose, VertexConsumer consumer) {
        render(pose, consumer, null);
    }

    /**
     * Renders this cube, optionally skipping one face.
     * @param skipFace the face to omit, or {@code null} to render all 6 faces.
     *                 Useful when a face is known to be hidden (e.g. an LED
     *                 face pressed against a parent block).
     */
    public void render(PoseStack.Pose pose, VertexConsumer consumer, Direction skipFace) {
        float x = center.position_x;
        float y = center.position_y;
        float z = center.position_z;

        float rX = (float) (sizeX / 2);
        float rY = (float) (sizeY / 2);
        float rZ = (float) (sizeZ / 2);

        int light = center.lightc();
        int r = center.colour_r;
        int g = center.colour_g;
        int b = center.colour_b;
        int a = center.colour_a;
        float u = center.tex_u;
        float v = center.tex_v;
        int overlay = OverlayTexture.NO_OVERLAY;

        // Top face (+Y)
        if (skipFace != Direction.UP) {
            vertex(pose, consumer, x - rX, y + rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y + rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y + rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x - rX, y + rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
        }

        // Bottom face (-Y) — normal forced to +Y to disable directional shading
        if (skipFace != Direction.DOWN) {
            vertex(pose, consumer, x - rX, y - rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y - rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y - rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x - rX, y - rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
        }

        // West face (-X) — normal forced to +Y to disable directional shading
        if (skipFace != Direction.WEST) {
            vertex(pose, consumer, x - rX, y - rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x - rX, y + rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x - rX, y + rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x - rX, y - rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
        }

        // East face (+X) — normal forced to +Y to disable directional shading
        if (skipFace != Direction.EAST) {
            vertex(pose, consumer, x + rX, y - rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y + rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y + rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y - rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
        }

        // North face (-Z) — normal forced to +Y to disable directional shading
        if (skipFace != Direction.NORTH) {
            vertex(pose, consumer, x - rX, y - rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x - rX, y + rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y + rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y - rY, z - rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
        }

        // South face (+Z) — normal forced to +Y to disable directional shading
        if (skipFace != Direction.SOUTH) {
            vertex(pose, consumer, x + rX, y - rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x + rX, y + rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x - rX, y + rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
            vertex(pose, consumer, x - rX, y - rY, z + rZ, 0, 1, 0, r, g, b, a, u, v, light, overlay);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer,
            float x, float y, float z,
            float nx, float ny, float nz,
            int r, int g, int b, int a,
            float u, float v, int light, int overlay) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
