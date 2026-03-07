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

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.marker.MarkerSubCache;

/**
 * Renders all marker connections and volume boxes in the world.
 * Hooked into NeoForge's RenderLevelStageEvent.AfterTranslucentBlocks.
 */
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

        // Render all client-side VolumeBoxes
        renderVolumeBoxes();

        currentPoseStack = null;
        currentCameraPos = null;
    }

    /**
     * Renders all volume boxes from ClientVolumeBoxes.
     * Uses reflection-free approach: the BCCore module calls this via the public API.
     */
    private static void renderVolumeBoxes() {
        // VolumeBox rendering is handled by VolumeBoxRenderer in buildcraft-core
        // since ClientVolumeBoxes is in buildcraft-core and cannot be referenced from buildcraft-lib.
        // The renderer is registered separately.
        if (volumeBoxRenderCallback != null) {
            volumeBoxRenderCallback.run();
        }
    }

    /** Callback for rendering volume boxes, set by buildcraft-core */
    private static Runnable volumeBoxRenderCallback;

    /** Called by buildcraft-core to register the volume box rendering callback */
    public static void setVolumeBoxRenderCallback(Runnable callback) {
        volumeBoxRenderCallback = callback;
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
