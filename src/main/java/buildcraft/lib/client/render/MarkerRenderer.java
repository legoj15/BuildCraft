/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.misc.VecUtil;

/**
 * Renders all marker connections and volume boxes in the world.
 * Hooked into NeoForge's RenderLevelStageEvent.AfterTranslucentBlocks.
 */
public class MarkerRenderer {
    public static final MarkerRenderer INSTANCE = new MarkerRenderer();

    private static final double RENDER_SCALE = 1 / 16.05;
    private static final Vec3 VEC_HALF = new Vec3(0.5, 0.5, 0.5);

    /** The PoseStack and camera position for the current render frame. */
    private static PoseStack currentPoseStack;
    private static Vec3 currentCameraPos;

    //? if >=1.21.10 {
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
    //?} else {
    /*public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;*/
    //?}
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // Get camera position from the level render state (1.21.11 pattern)
        //? if >=1.21.10 {
        currentCameraPos = event.getLevelRenderState().cameraRenderState.pos;
        //?} else {
        /*currentCameraPos = event.getCamera().getPosition();*/
        //?}
        currentPoseStack = event.getPoseStack();

        // Render all active connections across all marker cache types
        for (MarkerCache<? extends MarkerSubCache<?>> cache : MarkerCache.CACHES) {
            for (MarkerConnection<?> connection : cache.getSubCache(player.level()).getConnections()) {
                connection.renderInWorld();
            }
        }

        // Render preview beams for potential connections when holding a marker connector
        if (holdingConnectorCheck != null && holdingConnectorCheck.test(player)) {
            renderPossibleConnections(player);
        }

        // Render all client-side VolumeBoxes
        renderVolumeBoxes();

        currentPoseStack = null;
        currentCameraPos = null;
    }

    /**
     * Renders preview beams between markers that can be validly connected.
     * These thin "possible" lasers show the player where connections can be made
     * before actually clicking the marker connector item.
     */
    private static void renderPossibleConnections(Player player) {
        // Track rendered pairs to avoid drawing the same beam twice (A->B and B->A)
        Set<Long> renderedPairs = new HashSet<>();

        for (MarkerCache<? extends MarkerSubCache<?>> cache : MarkerCache.CACHES) {
            MarkerSubCache<?> subCache = cache.getSubCache(player.level());
            LaserType laserType = subCache.getPossibleLaserType();
            if (laserType == null) continue;

            ImmutableList<BlockPos> allMarkers = subCache.getAllMarkers();
            for (BlockPos marker : allMarkers) {
                ImmutableList<BlockPos> validTargets = subCache.getValidConnections(marker);
                for (BlockPos target : validTargets) {
                    // Create a unique key for this pair so we don't render it twice
                    long pairKey = pairKey(marker, target);
                    if (!renderedPairs.add(pairKey)) continue;

                    // Render the preview laser between the two marker centers
                    Vec3 from = VecUtil.add(VEC_HALF, marker);
                    Vec3 to = VecUtil.add(VEC_HALF, target);
                    Vec3 fromOffset = offset(from, to);
                    Vec3 toOffset = offset(to, from);
                    LaserData_BC8 data = new LaserData_BC8(laserType, fromOffset, toOffset, RENDER_SCALE,
                            false, false, 15);
                    LaserRenderer_BC8.renderLaserStatic(currentPoseStack, data, currentCameraPos);
                }
            }
        }
    }

    /** Offset the start/end slightly inward so the caps don't z-fight with the marker blocks. */
    private static Vec3 offset(Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from).normalize();
        return from.add(VecUtil.scale(dir, 0.125));
    }

    /** Creates a unique pair key for two block positions, order-independent. */
    private static long pairKey(BlockPos a, BlockPos b) {
        long ha = a.asLong();
        long hb = b.asLong();
        return ha < hb ? (ha * 31 + hb) : (hb * 31 + ha);
    }

    /**
     * Renders all volume boxes from ClientVolumeBoxes.
     * Uses reflection-free approach: the BCCore module calls this via the public API.
     */
    private static void renderVolumeBoxes() {
        if (volumeBoxRenderCallback != null) {
            volumeBoxRenderCallback.run();
        }
    }

    /** Callback for rendering volume boxes, set by buildcraft-core */
    private static Runnable volumeBoxRenderCallback;

    /** Callback for checking if the player holds a marker connector, set by buildcraft-core */
    private static Predicate<Player> holdingConnectorCheck;

    /** Called by buildcraft-core to register the volume box rendering callback */
    public static void setVolumeBoxRenderCallback(Runnable callback) {
        volumeBoxRenderCallback = callback;
    }

    /** Called by buildcraft-core to register the held-connector check */
    public static void setHoldingConnectorCheck(Predicate<Player> check) {
        holdingConnectorCheck = check;
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
