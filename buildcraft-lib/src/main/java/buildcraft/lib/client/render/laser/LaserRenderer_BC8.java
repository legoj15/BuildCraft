/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.laser;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * Renders laser beams between two points using colored lines.
 * Uses RenderTypes.LINES with POSITION_COLOR_NORMAL_LINEWIDTH vertex format.
 */
public class LaserRenderer_BC8 {

    /** Default line width for laser beams. */
    private static final float LINE_WIDTH = 2.0f;

    /**
     * Renders a laser beam as colored lines between two world positions.
     * Uses LINES render type which requires position, color, normal, and lineWidth per vertex.
     */
    public static void renderLaser(PoseStack poseStack, VertexConsumer consumer,
            LaserData_BC8 data, Vec3 cameraPos) {
        LaserData_BC8.LaserType type = data.laserType;

        double x1 = data.start.x - cameraPos.x;
        double y1 = data.start.y - cameraPos.y;
        double z1 = data.start.z - cameraPos.z;
        double x2 = data.end.x - cameraPos.x;
        double y2 = data.end.y - cameraPos.y;
        double z2 = data.end.z - cameraPos.z;

        Matrix4f matrix = poseStack.last().pose();

        // Direction vector for the line (used as normal)
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return;
        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;

        int r = (int) (type.red * 255);
        int g = (int) (type.green * 255);
        int b = (int) (type.blue * 255);
        int a = (int) (type.alpha * 255);

        float lineWidth = Math.max(LINE_WIDTH, type.lineWidth);

        // Main line
        consumer.addVertex(matrix, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz)
                .setLineWidth(lineWidth);
        consumer.addVertex(matrix, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz)
                .setLineWidth(lineWidth);

        // Additional offset lines to create a thicker beam appearance
        float halfWidth = type.lineWidth / 32.0f;
        Vec3 dir = new Vec3(dx, dy, dz).normalize();
        Vec3 up = Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = dir.cross(up).normalize().scale(halfWidth);
        Vec3 upVec = dir.cross(right).normalize().scale(halfWidth);

        // Render 4 more offset lines to make the beam thicker
        addOffsetLine(consumer, matrix, x1, y1, z1, x2, y2, z2, right.x, right.y, right.z, nx, ny, nz, r, g, b, a, lineWidth);
        addOffsetLine(consumer, matrix, x1, y1, z1, x2, y2, z2, -right.x, -right.y, -right.z, nx, ny, nz, r, g, b, a, lineWidth);
        addOffsetLine(consumer, matrix, x1, y1, z1, x2, y2, z2, upVec.x, upVec.y, upVec.z, nx, ny, nz, r, g, b, a, lineWidth);
        addOffsetLine(consumer, matrix, x1, y1, z1, x2, y2, z2, -upVec.x, -upVec.y, -upVec.z, nx, ny, nz, r, g, b, a, lineWidth);
    }

    private static void addOffsetLine(VertexConsumer consumer, Matrix4f matrix,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double ox, double oy, double oz,
            float nx, float ny, float nz,
            int r, int g, int b, int a, float lineWidth) {
        consumer.addVertex(matrix, (float)(x1+ox), (float)(y1+oy), (float)(z1+oz))
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz)
                .setLineWidth(lineWidth);
        consumer.addVertex(matrix, (float)(x2+ox), (float)(y2+oy), (float)(z2+oz))
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz)
                .setLineWidth(lineWidth);
    }

    /**
     * Renders a single laser beam, creates a buffer source and flushes immediately.
     * Convenience method for standalone rendering (outside a batched pass).
     */
    public static void renderLaserStatic(PoseStack poseStack, LaserData_BC8 data, Vec3 cameraPos) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.LINES);
        renderLaser(poseStack, consumer, data, cameraPos);
        bufferSource.endBatch(RenderTypes.LINES);
    }
}
