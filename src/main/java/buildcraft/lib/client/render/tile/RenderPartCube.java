/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.Direction;

import buildcraft.lib.client.model.MutableVertex;

/**
 * A small vertex-coloured cube — the primitive behind the LED indicators on the
 * pump, mining well, and quarry. The {@link #center} vertex carries the cube's
 * position and RGBA colour; render with
 * {@link buildcraft.lib.client.render.BCLibRenderTypes#led()} (or any other
 * {@code POSITION_COLOR}-format RenderType) and the colour alone determines the
 * cube's appearance — no texture sampling, no lighting modulation.
 */
public class RenderPartCube {
    /** The centre of this cube. Only {@code position_*} and {@code colour_*} are read by {@link #render}. */
    public final MutableVertex center = new MutableVertex();
    public double sizeX = 1 / 16.0, sizeY = 1 / 16.0, sizeZ = 1 / 16.0;

    public RenderPartCube() {
        this(1 / 16.0, 1 / 16.0, 1 / 16.0);
    }

    public RenderPartCube(double x, double y, double z) {
        center.positiond(x, y, z);
    }

    /** Renders all 6 faces of this cube. */
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

        int r = center.colour_r;
        int g = center.colour_g;
        int b = center.colour_b;
        int a = center.colour_a;

        // Top face (+Y)
        if (skipFace != Direction.UP) {
            emit(pose, consumer, x - rX, y + rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y + rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y + rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x - rX, y + rY, z - rZ, r, g, b, a);
        }
        // Bottom face (-Y)
        if (skipFace != Direction.DOWN) {
            emit(pose, consumer, x - rX, y - rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y - rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y - rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x - rX, y - rY, z + rZ, r, g, b, a);
        }
        // West face (-X)
        if (skipFace != Direction.WEST) {
            emit(pose, consumer, x - rX, y - rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x - rX, y + rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x - rX, y + rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x - rX, y - rY, z - rZ, r, g, b, a);
        }
        // East face (+X)
        if (skipFace != Direction.EAST) {
            emit(pose, consumer, x + rX, y - rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y + rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y + rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y - rY, z + rZ, r, g, b, a);
        }
        // North face (-Z)
        if (skipFace != Direction.NORTH) {
            emit(pose, consumer, x - rX, y - rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x - rX, y + rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y + rY, z - rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y - rY, z - rZ, r, g, b, a);
        }
        // South face (+Z)
        if (skipFace != Direction.SOUTH) {
            emit(pose, consumer, x + rX, y - rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x + rX, y + rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x - rX, y + rY, z + rZ, r, g, b, a);
            emit(pose, consumer, x - rX, y - rY, z + rZ, r, g, b, a);
        }
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer consumer,
                             float x, float y, float z,
                             int r, int g, int b, int a) {
        consumer.addVertex(pose, x, y, z).setColor(r, g, b, a);
    }
}
