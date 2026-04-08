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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
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
public class RenderEngine_BC8 implements BlockEntityRenderer<TileEngineBase_BC8, EngineRenderState> {

    private final BiFunction<TileEngineBase_BC8, Float, MutableQuad[]> quadProvider;

    /**
     * @param quadProvider Function that takes (engine, partialTicks) and returns animated quads
     *                     from the JSON variable model system via BCEnergyModels.
     */
    public RenderEngine_BC8(BiFunction<TileEngineBase_BC8, Float, MutableQuad[]> quadProvider) {
        this.quadProvider = quadProvider;
    }

    @Override
    public EngineRenderState createRenderState() {
        return new EngineRenderState();
    }

    @Override
    public void submit(EngineRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
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
        MutableQuad[] quads = quadProvider.apply(engine, partialTicks);
        if (quads == null || quads.length == 0) return;

        poseStack.pushPose();

        int light = LevelRenderer.getLightCoords(level, pos);

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

        bufferSource.endBatch();
        poseStack.popPose();
    }
}
