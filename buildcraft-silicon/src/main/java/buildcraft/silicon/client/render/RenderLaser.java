/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.silicon.block.BlockLaser;
import buildcraft.silicon.tile.TileLaser;

public class RenderLaser implements BlockEntityRenderer<TileLaser, LaserRenderState> {
    private static final int MAX_POWER = BuildCraftLaserManager.POWERS.length - 1;

    public RenderLaser(BlockEntityRendererProvider.Context context) {
        // Required by the registration API
    }

    @Override
    public LaserRenderState createRenderState() {
        return new LaserRenderState();
    }

    @Override
    public void submit(LaserRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        Vec3 camPos = cameraState.pos;
        if (camPos == null) return;

        org.joml.Vector3f t = new org.joml.Vector3f();
        poseStack.last().pose().getTranslation(t);
        BlockPos pos = new BlockPos(
                Math.round((float) (camPos.x + t.x)),
                Math.round((float) (camPos.y + t.y)),
                Math.round((float) (camPos.z + t.z)));

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileLaser tile)) return;

        if (tile.laserPos == null) {
            return;
        }

        long avg = tile.getAverageClient();
        if (avg <= 200_000) {
            return;
        }
        avg += 200_000;

        Direction side = tile.getBlockState().getValue(BlockLaser.FACING);
        Vec3 offset = new Vec3(0.5, 0.5, 0.5).add(
                Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(4 / 16D));
        int index = (int) (avg * MAX_POWER / tile.getMaxPowerPerTick());
        if (index > MAX_POWER) {
            index = MAX_POWER;
        }

        Vec3 startPos = Vec3.atLowerCornerOf(tile.getBlockPos()).add(offset);
        LaserData_BC8 laser = new LaserData_BC8(BuildCraftLaserManager.POWERS[index],
                startPos, tile.laserPos, 1 / 16D);

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(
                RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        Vec3 cameraZero = Vec3.ZERO;
        LaserRenderer_BC8.renderLaser(poseStack, consumer, laser, cameraZero);

        bufferSource.endBatch();
        poseStack.popPose();
    }

}
