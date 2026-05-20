/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client.render;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.debug.DebugRenderHelper;

import buildcraft.builders.tile.TileQuarry;

/**
 * Advanced-debug overlay for the Quarry: draws a translucent green wireframe box around every
 * chunk the quarry keeps force-loaded. Faithful port of the 1.12.2 {@code AdvDebuggerQuarry} —
 * one box per {@link TileQuarry#getChunksToLoad()} entry, spanning the whole chunk in X/Z and the
 * frame box's Y range.
 */
public final class AdvDebuggerQuarry {
    /** Green, matching the 1.12.2 chunk-highlight colour (0x55_99_FF_99 ARGB). */
    private static final int COLOUR_CHUNK = 0x55_99FF99;

    private AdvDebuggerQuarry() {}

    public static void render(TileQuarry tile, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        if (!tile.frameBox.isInitialized()) {
            return;
        }
        Set<ChunkPos> chunks = tile.getChunksToLoad();
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        double minY = tile.frameBox.min().getY();
        double maxY = tile.frameBox.max().getY() + 1;
        for (ChunkPos chunkPos : chunks) {
            AABB box = new AABB(
                chunkPos.getMinBlockX(), minY, chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1, maxY, chunkPos.getMaxBlockZ() + 1
            );
            DebugRenderHelper.renderBox(poseStack, bufferSource, box, cameraPos, COLOUR_CHUNK);
        }
    }
}
