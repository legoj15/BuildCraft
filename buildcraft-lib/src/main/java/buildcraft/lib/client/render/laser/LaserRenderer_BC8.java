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
 * This is a simplified 1.21 port — the 1.12 version used
 * GL display lists with textured quads.
 */
public class LaserRenderer_BC8 {

    /**
     * Renders a laser beam as a colored line between two world positions.
     * The PoseStack should already be translated to camera-relative coordinates.
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

        // Direction vector for the line (used as normal for LINES format)
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return;
        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;

        consumer.addVertex(matrix, (float) x1, (float) y1, (float) z1)
                .setColor(type.red, type.green, type.blue, type.alpha)
                .setNormal(nx, ny, nz)
                .setLineWidth(type.lineWidth);
        consumer.addVertex(matrix, (float) x2, (float) y2, (float) z2)
                .setColor(type.red, type.green, type.blue, type.alpha)
                .setNormal(nx, ny, nz)
                .setLineWidth(type.lineWidth);
    }

    /**
     * Renders a single laser beam as a line, creates a buffer source and flushes immediately.
     * Convenience method for standalone rendering (outside a batched pass).
     */
    public static void renderLaserStatic(PoseStack poseStack, LaserData_BC8 data, Vec3 cameraPos) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lines());
        renderLaser(poseStack, consumer, data, cameraPos);
        bufferSource.endBatch(RenderTypes.lines());
    }
}
