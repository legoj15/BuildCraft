/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.client.render.tile.RenderPartCube;

/**
 * Client-side helper for the advanced-debug overlay. Draws solid, vertex-coloured boxes through
 * vanilla's translucent debug filled-box render type ({@link BCLibRenderTypes#debugFilled()}). A
 * box's per-vertex alpha decides how it reads: a low alpha gives a see-through volume (the Quarry's
 * chunk highlights), full alpha gives a solid marker (the Laser's cone cubes).
 */
public final class DebugRenderHelper {
    private DebugRenderHelper() {}

    /**
     * Draws a filled box at the given world-space {@link AABB}, coloured by {@code argb} (the alpha
     * byte is honoured — use a low alpha for a translucent volume, full alpha for a solid box).
     * {@code cameraPos} is the camera's world position; the box is translated into camera space so
     * it renders in the right place regardless of where the player is.
     */
    public static void renderFilledBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                       Vec3 cameraPos, int argb) {
        VertexConsumer consumer = bufferSource.getBuffer(BCLibRenderTypes.debugFilled());
        RenderPartCube cube = new RenderPartCube();
        cube.center.positiond(
            (box.minX + box.maxX) / 2.0 - cameraPos.x,
            (box.minY + box.maxY) / 2.0 - cameraPos.y,
            (box.minZ + box.maxZ) / 2.0 - cameraPos.z
        );
        cube.sizeX = box.getXsize();
        cube.sizeY = box.getYsize();
        cube.sizeZ = box.getZsize();
        cube.center.colouri(argb);
        cube.render(poseStack.last(), consumer);
    }
}
