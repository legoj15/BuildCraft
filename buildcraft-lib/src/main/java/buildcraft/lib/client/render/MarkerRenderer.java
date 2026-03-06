/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.marker.MarkerSubCache;

/**
 * Renders all marker connections in the world.
 * Hooked into NeoForge's RenderLevelStageEvent.AfterTranslucentBlocks.
 */
@OnlyIn(Dist.CLIENT)
public class MarkerRenderer {
    public static final MarkerRenderer INSTANCE = new MarkerRenderer();

    /** The PoseStack and camera position for the current render frame. */
    private static PoseStack currentPoseStack;
    private static Vec3 currentCameraPos;

    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // Get camera position from the level render state (1.21.11 pattern)
        currentCameraPos = event.getLevelRenderState().cameraRenderState.pos;
        currentPoseStack = event.getPoseStack();

        // Render all active connections across all marker cache types
        for (MarkerCache<? extends MarkerSubCache<?>> cache : MarkerCache.CACHES) {
            for (MarkerConnection<?> connection : cache.getSubCache(player.level()).getConnections()) {
                connection.renderInWorld();
            }
        }

        currentPoseStack = null;
        currentCameraPos = null;
    }

    /** Called by connection renderInWorld() implementations to get the current PoseStack */
    public static PoseStack getPoseStack() {
        return currentPoseStack;
    }

    /** Called by connection renderInWorld() implementations to get the current camera position */
    public static Vec3 getCameraPos() {
        return currentCameraPos;
    }
}
