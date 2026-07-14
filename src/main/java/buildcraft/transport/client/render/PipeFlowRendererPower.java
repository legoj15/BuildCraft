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

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.pipe.flow.PipeFlowPower;

/** MJ kinesis flow renderer. All draw logic lives in {@link PipeFlowRendererKinesis}; this only
 *  supplies the {@code POWER_FLOW} sprite and the node-specific render entry points. */
public enum PipeFlowRendererPower implements IPipeFlowRenderer<PipeFlowPower> {
    INSTANCE;

    //? if >=1.21.10 {
    /** Modern (>=1.21.10) entry: the BER passes its {@link SubmitNodeCollector}, so power geometry
     *  is queued via {@code submitCustomGeometry} (retained-mode "submit"). 26.2 removed the
     *  immediate-mode {@code renderBuffers()} path entirely; the collector path is identical on
     *  1.21.10/1.21.11/26.1/26.2. */
    public void render(PipeFlowPower flow, double x, double y, double z, float partialTicks,
                       SubmitNodeCollector collector, PoseStack poseStack) {
        if (PipeFlowRendererKinesis.computeCentrePower(flow) <= 0) {
            return;
        }
        collector.submitCustomGeometry(poseStack, PipeFlowRendererKinesis.kinesisRenderType(),
            (pose, consumer) -> PipeFlowRendererKinesis.drawAll(flow, partialTicks, pose, consumer,
                BCTransportSprites.POWER_FLOW.getSprite()));
    }
    //?}

    @Override
    public void render(PipeFlowPower flow, double x, double y, double z, float partialTicks, VertexConsumer bb, PoseStack.Pose pose) {
        if (PipeFlowRendererKinesis.computeCentrePower(flow) <= 0) {
            return;
        }
        //? if <26.2 {
        // 1.21.1..26.1 classic immediate-mode path (this interface overload is only reached on those
        // nodes; >=1.21.10 BERs call the SubmitNodeCollector overload above). Create a dedicated buffer
        // for power rendering — do NOT use the global buffer source shared by other renderers to avoid
        // endBatch() corrupting in-progress vertex data from other BERs.
        MultiBufferSource.BufferSource bufferSource =
            Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer powerBB = bufferSource.getBuffer(PipeFlowRendererKinesis.kinesisRenderType());
        PipeFlowRendererKinesis.drawAll(flow, partialTicks, pose, powerBB, BCTransportSprites.POWER_FLOW.getSprite());
        bufferSource.endBatch(PipeFlowRendererKinesis.kinesisRenderType());
        //?}
    }

    /** Kept for {@code PipeFlowRendererPowerGeometryTester} — delegates to the shared clamped box math. */
    static AABB sideFlowBox(Direction side, double power, double centrePower) {
        return PipeFlowRendererKinesis.sideFlowBox(side, power, centrePower);
    }
}
