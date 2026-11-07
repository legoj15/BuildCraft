/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

import java.util.function.BiFunction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
//? if <1.21.10 {
/*import net.minecraft.client.renderer.MultiBufferSource;*/
//?}
//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.state.CameraRenderState;*/
//?}
import net.minecraft.core.BlockPos;
//? if >=1.21.10 {
import net.minecraft.util.profiling.Profiler;
//?}
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.engine.TileEngineBase_BC8;

/**
 * Block Entity Renderer for all BuildCraft engines.
 * Uses the JSON variable model system to render animated engines.
 * The quad provider function is injected by the energy module to avoid circular dependencies.
 */
//? if >=1.21.10 {
public class RenderEngine_BC8 implements BlockEntityRenderer<TileEngineBase_BC8, BCRenderState> {
//?} else {
/*public class RenderEngine_BC8 implements BlockEntityRenderer<TileEngineBase_BC8> {*/
//?}

    private final BiFunction<TileEngineBase_BC8, Float, MutableQuad[]> quadProvider;

    /**
     * @param quadProvider Function that takes (engine, partialTicks) and returns animated quads
     *                     from the JSON variable model system via BCEnergyModels.
     */
    public RenderEngine_BC8(BiFunction<TileEngineBase_BC8, Float, MutableQuad[]> quadProvider) {
        this.quadProvider = quadProvider;
    }

    //? if >=1.21.10 {
    @Override
    public BCRenderState createRenderState() {
        return new BCRenderState();
    }

    @Override
    public void submit(BCRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        ProfilerFiller _profiler = Profiler.get();
        _profiler.push("buildcraft:engine_submit");
        try {
        // The render state already carries the world pos — no camera-pos reconstruction needed.
        BlockPos pos = state.blockPos;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileEngineBase_BC8 engine)) return;

        // Get animated quads from the quad provider (injected by energy module)
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — the engine (tile) + partialTicks are passed,
    // so no camera-pos reconstruction is needed. The passed packedLight/packedOverlay go unused
    // (the shared body sources its light from the level, as the modern submit path does).
    // Profiler.get() → Minecraft.getInstance().getProfiler() on this pre-render-state line.
    @Override
    public void render(TileEngineBase_BC8 engine, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ProfilerFiller _profiler = Minecraft.getInstance().getProfiler();
        _profiler.push("buildcraft:engine_render");
        try {
        BlockPos pos = engine.getBlockPos();
        Level level = engine.getLevel();
        if (level == null) return;*/
    //?}
        _profiler.push("buildcraft:engine_model_refresh");
        MutableQuad[] quads;
        try {
            quads = quadProvider.apply(engine, partialTicks);
        } finally {
            _profiler.pop();
        }
        if (quads == null || quads.length == 0) return;

        poseStack.pushPose();

        int light = buildcraft.lib.client.render.LightUtil.getLightCoords(level, pos);

        //? if >=1.21.10 {
        collector.submitCustomGeometry(poseStack, buildcraft.lib.client.render.BCLibRenderTypes.cutoutBlockSheet(),
                (pose, consumer) -> renderQuads(quads, light, pose, consumer));
        // The engine block model has no geometry, so the level's destroy pass draws nothing: draw the crack here.
        buildcraft.lib.client.render.CrumblingOverlayRenderer.submit(collector, poseStack, state.breakProgress,
                (pose, consumer) -> renderQuads(quads, light, pose, consumer));
        //?} else {
        /*// Draw into the passed buffers, not the global buffer source: while the engine is being mined the level
        // renderer hands in a wrapper that also writes the crack for render types that affect crumbling.
        renderQuads(quads, light, poseStack.last(), buffers.getBuffer(buildcraft.lib.client.render.BCLibRenderTypes.cutoutBlockSheet()));

        // No endBatch here: the level renderer flushes the cutout sheet after all block entities have drawn, and a
        // full flush per engine per frame cost extra draw calls for every visible engine.*/
        //?}
        poseStack.popPose();
        } finally {
            _profiler.pop();
        }
    }

    /**
     * Renders the animated engine quads into the supplied consumer. Node-agnostic:
     * on the modern (>=1.21.10) submit path {@code pose}/{@code consumer} come from the
     * {@link net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer}
     * lambda; on 1.21.1 they are derived from the immediate-mode buffer source.
     * <p>
     * Render quads directly via {@link MutableQuad#render} which writes per-vertex
     * color, normal, UV, and lightmap data. This bypasses BakedQuad (which in 26.1
     * carries no vertex colors) and ensures diffuse shading is applied.
     */
    private void renderQuads(MutableQuad[] quads, int light, PoseStack.Pose pose, VertexConsumer consumer) {
        for (MutableQuad quad : quads) {
            quad.setCalculatedDiffuse();
            quad.lighti(light);
            quad.render(pose, consumer);
        }
    }
}
