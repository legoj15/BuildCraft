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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
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
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.engine.TileEngineBase_BC8;

/**
 * Block Entity Renderer for all BuildCraft engines.
 * Uses the JSON variable model system to render animated engines.
 * The quad provider function is injected by the energy module to avoid circular dependencies.
 */
//? if >=1.21.10 {
public class RenderEngine_BC8 implements BlockEntityRenderer<TileEngineBase_BC8, EngineRenderState> {
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
    public EngineRenderState createRenderState() {
        return new EngineRenderState();
    }

    @Override
    public void submit(EngineRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        ProfilerFiller _profiler = Profiler.get();
        _profiler.push("buildcraft:engine_submit");
        try {
        Vec3 camPos = cameraState.pos;
        if (camPos == null) return;
        org.joml.Vector3f t = new org.joml.Vector3f();
        poseStack.last().pose().getTranslation(t);
        BlockPos pos = new BlockPos(
                Math.round((float)(camPos.x + t.x)),
                Math.round((float)(camPos.y + t.y)),
                Math.round((float)(camPos.z + t.z)));

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileEngineBase_BC8 engine)) return;

        // Get animated quads from the quad provider (injected by energy module)
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — the engine (tile) + partialTicks are passed,
    // so no camera-pos reconstruction is needed. The passed buffers/packedLight/packedOverlay go unused
    // (the shared body sources its own buffer/light from the level, as the modern submit path does).
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

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());

        // Render quads directly via MutableQuad.render() which writes per-vertex
        // color, normal, UV, and lightmap data. This bypasses BakedQuad (which in
        // 26.1 carries no vertex colors) and ensures diffuse shading is applied.
        for (MutableQuad quad : quads) {
            quad.setCalculatedDiffuse();
            quad.lighti(light);
            quad.render(poseStack.last(), buffer);
        }

        // Intentionally not calling bufferSource.endBatch() here — the previous
        // implementation issued a *full* flush per engine per frame (no render-type
        // argument means MultiBufferSource.BufferSource walks every registered
        // RenderType and drains its builder), forcing extra draw calls and
        // GPU-sync overhead per visible engine. The BlockEntityRenderDispatcher
        // already calls endBatch on the shared buffer source after all BE
        // renders complete, so the quads we just wrote get drained at the
        // correct time without any per-engine intervention.
        poseStack.popPose();
        } finally {
            _profiler.pop();
        }
    }
}
