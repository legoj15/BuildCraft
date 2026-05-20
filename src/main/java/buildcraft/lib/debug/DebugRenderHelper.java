/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Client-side helpers for the advanced-debug overlay. Draws wireframe boxes — perfectly readable
 * for a debug overlay and robust against the 26.1 render-pipeline changes (vanilla's old
 * {@code LevelRenderer.renderLineBox} no longer exists; {@link ShapeRenderer} with the
 * {@code lines()} render type is the modern equivalent).
 */
public final class DebugRenderHelper {
    private static final float LINE_WIDTH = 2.5F;

    private DebugRenderHelper() {}

    /**
     * Draws a wireframe box at the given world-space {@link AABB}. {@code cameraPos} is the camera's
     * world position — the box is translated into camera space so it renders in the right place
     * regardless of where the player is.
     */
    public static void renderBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box, Vec3 cameraPos,
                                 int argb) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderTypes.lines());
        VoxelShape shape = Shapes.create(box);
        ShapeRenderer.renderShape(
            poseStack, buffer, shape,
            -cameraPos.x, -cameraPos.y, -cameraPos.z,
            argb, LINE_WIDTH
        );
    }
}
