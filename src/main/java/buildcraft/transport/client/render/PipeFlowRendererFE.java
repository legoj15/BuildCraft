/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

//? if <26.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}

import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux;

/** FE/RF kinesis flow renderer. All draw logic lives in {@link PipeFlowRendererKinesis}; this only
 *  supplies the {@code POWER_FLOW_OVERLOAD} sprite and the node-specific render entry points. It now
 *  shares the MJ renderer's overload-safe {@link PipeFlowRendererKinesis#sideFlowBox} clamp, so an
 *  overloaded RF pipe no longer renders invisible connecting stems. */
public enum PipeFlowRendererFE implements IPipeFlowRenderer<PipeFlowRedstoneFlux> {
    INSTANCE;

    //? if >=1.21.10 {
    /** Modern (>=1.21.10) entry: the BER passes its {@link SubmitNodeCollector}, so RF-flow geometry
     *  is queued via {@code submitCustomGeometry} (retained-mode "submit"). 26.2 removed the
     *  immediate-mode {@code renderBuffers()} path entirely; the collector path is identical on
     *  1.21.10/1.21.11/26.1/26.2. */
    public void render(PipeFlowRedstoneFlux flow, double x, double y, double z, float partialTicks,
                       SubmitNodeCollector collector, PoseStack poseStack) {
        if (PipeFlowRendererKinesis.computeCentrePower(flow) <= 0) {
            return;
        }
        collector.submitCustomGeometry(poseStack, PipeFlowRendererKinesis.kinesisRenderType(),
            (pose, consumer) -> PipeFlowRendererKinesis.drawAll(flow, partialTicks, pose, consumer,
                BCTransportSprites.POWER_FLOW_OVERLOAD.getSprite()));
    }
    //?}

    @Override
    public void render(PipeFlowRedstoneFlux flow, double x, double y, double z, float partialTicks, VertexConsumer bb, PoseStack.Pose pose) {
        if (PipeFlowRendererKinesis.computeCentrePower(flow) <= 0) {
            return;
        }
        //? if <26.2 {
        // 1.21.1..26.1 classic immediate-mode path (this interface overload is only reached on those
        // nodes; >=1.21.10 BERs call the SubmitNodeCollector overload above).
        MultiBufferSource.BufferSource bufferSource =
            Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer rfBB = bufferSource.getBuffer(PipeFlowRendererKinesis.kinesisRenderType());
        PipeFlowRendererKinesis.drawAll(flow, partialTicks, pose, rfBB, BCTransportSprites.POWER_FLOW_OVERLOAD.getSprite());
        bufferSource.endBatch(PipeFlowRendererKinesis.kinesisRenderType());
        //?}
    }
}
