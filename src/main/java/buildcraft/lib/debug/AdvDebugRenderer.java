/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
//? if <26.1 {
/*import net.minecraft.client.renderer.MultiBufferSource;*/
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
//? if >=26.1 {
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
//?} else {
/*import net.neoforged.neoforge.client.event.RenderLevelStageEvent;*/
//?}

import buildcraft.builders.client.render.AdvDebuggerQuarry;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.silicon.client.render.AdvDebuggerLaser;
import buildcraft.silicon.tile.TileLaser;

/**
 * Client-side render dispatcher for the advanced-debug overlay. Reads the current debug target
 * recorded by {@link buildcraft.lib.item.ItemDebugger} and draws the matching overlay during world
 * rendering. The whole feature is client-authoritative — no networking — so this is the only place
 * the overlay geometry is produced.
 *
 * <p>How the per-subject debuggers obtain a draw target differs by MC line, and is bridged through
 * this class's per-frame static context:
 * <ul>
 *   <li><b>&gt;=26.1</b> — MC 26.1 removed immediate-mode rendering. We hook
 *       {@link SubmitCustomGeometryEvent}, stash its {@link net.minecraft.client.renderer.SubmitNodeCollector}
 *       for the frame, and {@link DebugRenderHelper} routes every box through
 *       {@code collector.submitCustomGeometry(...)}.</li>
 *   <li><b>&lt;26.1</b> — 1.21.x still have {@code MultiBufferSource}. We hook
 *       {@link net.neoforged.neoforge.client.event.RenderLevelStageEvent} at AfterTranslucentBlocks,
 *       stash the immediate buffer source, and {@link DebugRenderHelper} draws straight into it.</li>
 * </ul>
 */
public final class AdvDebugRenderer {
    private AdvDebugRenderer() {}

    //? if >=26.1 {
    /** The retained-mode collector for the frame currently being drawn (>=26.1 only). */
    private static net.minecraft.client.renderer.SubmitNodeCollector currentCollector;

    /** The SubmitNodeCollector for the frame in progress. Read by {@link DebugRenderHelper}. */
    public static net.minecraft.client.renderer.SubmitNodeCollector collector() {
        return currentCollector;
    }
    //?} else {
    /*/^* The immediate-mode buffer source for the frame currently being drawn (<26.1 only). *^/
    private static MultiBufferSource currentBufferSource;

    /^* The MultiBufferSource for the frame in progress. Read by {@link DebugRenderHelper}. *^/
    public static MultiBufferSource bufferSource() {
        return currentBufferSource;
    }*/
    //?}

    //? if >=26.1 {
    /**
     * 26.1 entry point. Registered against {@link SubmitCustomGeometryEvent}, which supplies a
     * {@link net.minecraft.client.renderer.SubmitNodeCollector} for retained-mode submission. There
     * is no batch to flush — the collector flushes itself after the event returns.
     */
    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        BlockEntity be = resolveTarget();
        if (be == null) {
            return;
        }
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        currentCollector = event.getSubmitNodeCollector();
        try {
            dispatch(be, poseStack, cameraPos);
        } finally {
            currentCollector = null;
        }
    }
    //?} elif >=1.21.10 {
    /*@SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        BlockEntity be = resolveTarget();
        if (be == null) {
            return;
        }
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        currentBufferSource = bufferSource;
        try {
            dispatch(be, poseStack, cameraPos);
        } finally {
            bufferSource.endBatch();
            currentBufferSource = null;
        }
    }*/
    //?} else {
    /*@SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        BlockEntity be = resolveTarget();
        if (be == null) {
            return;
        }
        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        currentBufferSource = bufferSource;
        try {
            dispatch(be, poseStack, cameraPos);
        } finally {
            bufferSource.endBatch();
            currentBufferSource = null;
        }
    }*/
    //?}

    /**
     * Resolves the current debug target into a live {@link IAdvDebugTarget} block entity, or returns
     * {@code null} (and clears the target) when there's nothing to draw — no target set, no player/
     * level, or the tile is gone (broken, unloaded, swapped).
     */
    private static BlockEntity resolveTarget() {
        BlockPos target = BCAdvDebugging.INSTANCE.getClientTarget();
        if (target == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        BlockEntity be = mc.level.getBlockEntity(target);
        if (!(be instanceof IAdvDebugTarget)) {
            // Tile gone (broken, unloaded, swapped) — stop drawing.
            BCAdvDebugging.INSTANCE.clear();
            return null;
        }
        return be;
    }

    /** Routes the resolved target to its per-subject debugger. */
    private static void dispatch(BlockEntity be, PoseStack poseStack, Vec3 cameraPos) {
        if (be instanceof TileQuarry quarry) {
            AdvDebuggerQuarry.render(quarry, poseStack, cameraPos);
        } else if (be instanceof TileLaser laser) {
            AdvDebuggerLaser.render(laser, poseStack, cameraPos);
        }
    }
}
