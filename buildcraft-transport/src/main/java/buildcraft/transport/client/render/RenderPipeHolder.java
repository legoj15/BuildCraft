/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.pipe.IPipeBehaviourRenderer;
import buildcraft.api.transport.pipe.IPipeFlowRenderer;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeFlow;
import buildcraft.api.transport.pluggable.IPlugDynamicRenderer;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.transport.client.PipeRegistryClient;
import buildcraft.transport.client.model.ModelPipe;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;

/** BlockEntityRenderer for pipe holder blocks. Renders the pipe body geometry
 *  plus dynamic content (pluggables, fluid/item/power flow) via registered renderers. */
public class RenderPipeHolder implements BlockEntityRenderer<TilePipeHolder, PipeHolderRenderState> {

    public RenderPipeHolder(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public PipeHolderRenderState createRenderState() {
        return new PipeHolderRenderState();
    }

    /** Populate render state with tile reference (if framework supports this). */
    public void extractRenderState(TilePipeHolder blockEntity, PipeHolderRenderState renderState, float partialTick) {
        renderState.pipe = blockEntity;
    }

    @Override
    public void submit(PipeHolderRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        // Try render state first (if extractRenderState was called), else fall back to BlockPos lookup
        TilePipeHolder pipe = renderState.pipe;
        Level level;
        if (pipe != null) {
            level = pipe.getLevel();
        } else {
            // Fallback: derive BlockPos from PoseStack + camera
            Vec3 camPos = cameraState.pos;
            if (camPos == null) return;
            org.joml.Vector3f t = new org.joml.Vector3f();
            poseStack.last().pose().getTranslation(t);
            BlockPos pos = BlockPos.containing(camPos.x + t.x, camPos.y + t.y, camPos.z + t.z);

            level = Minecraft.getInstance().level;
            if (level == null) return;

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TilePipeHolder holder)) return;
            pipe = holder;
        }
        if (level == null) return;

        // Get the buffer for rendering
        MultiBufferSource.BufferSource bufferSource =
            Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());
        int light = LevelRenderer.getLightColor(level, pipe.getBlockPos());

        poseStack.pushPose();

        // --- Render pipe body (static model from cache) ---
        renderPipeBody(pipe, poseStack, buffer, light);

        // --- Render pluggables ---
        renderPluggables(pipe, 0, 0, 0, 0, buffer);

        // --- Render flow + behaviour content ---
        renderContents(pipe, 0, 0, 0, 0, buffer);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    /** Emit the cached pipe body quads (cutout layer). */
    private static void renderPipeBody(TilePipeHolder pipe, PoseStack poseStack, VertexConsumer buffer, int light) {
        ModelPipe.renderDirect(pipe, poseStack.last(), buffer, light);
    }

    private static void renderPluggables(TilePipeHolder pipe, double x, double y, double z,
        float partialTicks, VertexConsumer bb) {
        for (Direction face : Direction.values()) {
            PipePluggable plug = pipe.getPluggable(face);
            if (plug == null) {
                continue;
            }
            renderPlug(plug, x, y, z, partialTicks, bb);
        }
    }

    @SuppressWarnings("unchecked")
    private static <P extends PipePluggable> void renderPlug(P plug, double x, double y, double z,
        float partialTicks, VertexConsumer bb) {
        IPlugDynamicRenderer<P> renderer = PipeRegistryClient.getPlugRenderer(plug);
        if (renderer != null) {
            renderer.render(plug, x, y, z, partialTicks, bb);
        }
    }

    private static void renderContents(TilePipeHolder pipe, double x, double y, double z,
        float partialTicks, VertexConsumer bb) {
        Pipe p = pipe.getPipe();
        if (p == null) {
            return;
        }
        if (p.flow != null) {
            renderFlow(p.flow, x, y, z, partialTicks, bb);
        }
        if (p.behaviour != null) {
            renderBehaviour(p.behaviour, x, y, z, partialTicks, bb);
        }
    }

    @SuppressWarnings("unchecked")
    private static <F extends PipeFlow> void renderFlow(F flow, double x, double y, double z,
        float partialTicks, VertexConsumer bb) {
        IPipeFlowRenderer<F> renderer = PipeRegistryClient.getFlowRenderer(flow);
        if (renderer != null) {
            renderer.render(flow, x, y, z, partialTicks, bb);
        }
    }

    @SuppressWarnings("unchecked")
    private static <B extends PipeBehaviour> void renderBehaviour(B behaviour, double x, double y, double z,
        float partialTicks, VertexConsumer bb) {
        IPipeBehaviourRenderer<B> renderer = PipeRegistryClient.getBehaviourRenderer(behaviour);
        if (renderer != null) {
            renderer.render(behaviour, x, y, z, partialTicks, bb);
        }
    }
}
