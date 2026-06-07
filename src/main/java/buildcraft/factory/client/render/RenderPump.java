/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.state.CameraRenderState;*/
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserRow;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.render.tile.LedRenderUtil;
import buildcraft.lib.client.render.tile.RenderPartCube;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;

import buildcraft.factory.tile.TilePump;

/**
 * Block entity renderer for the pump. Renders colored LED indicators on all
 * four sides and a laser-textured tube beam extending downward.
 * Ported from 1.12.2 RenderPump + RenderTube.
 */
//? if >=1.21.10 {
public class RenderPump implements BlockEntityRenderer<TilePump, PumpRenderState> {
//?} else {
/*public class RenderPump implements BlockEntityRenderer<TilePump> {*/
//?}
    /** 16-step red→yellow gradient driven by battery fill %. ABGR; the low byte is red. */
    private static final int[] COLOUR_POWER = new int[16];

    private static final double LED_INSET = 0.4 / 16.0;
    private static final double POWER_OFFSET = 1.5 / 16.0;
    private static final double STATUS_OFFSET = 3.5 / 16.0;
    private static final double Y = 13.5 / 16.0;

    private static final RenderPartCube[] LED_POWER = new RenderPartCube[4];
    private static final RenderPartCube[] LED_STATUS = new RenderPartCube[4];

    private static final LaserType TUBE_LASER;

    static {
        for (int i = 0; i < COLOUR_POWER.length; i++) {
            int c = (i * 0x40) / COLOUR_POWER.length;
            int r = (i * 0xE0) / COLOUR_POWER.length + 0x1F;
            COLOUR_POWER[i] = (0xFF << 24) | (c << 16) | (c << 8) | r;
        }

        for (int i = 0; i < 4; i++) {
            Direction face = Direction.from2DDataValue(i);
            LED_POWER[i] = new RenderPartCube();
            LED_STATUS[i] = new RenderPartCube();
            LedRenderUtil.setFacePosition(LED_POWER[i], face, LED_INSET, POWER_OFFSET, Y);
            LedRenderUtil.setFacePosition(LED_STATUS[i], face, LED_INSET, STATUS_OFFSET, Y);
        }

        SpriteHolder spriteTubeMiddle = SpriteHolderRegistry.getHolder("buildcraftunofficial:block/pump/tube");
        LaserRow cap = new LaserRow(spriteTubeMiddle, 0, 8, 8, 16);
        LaserRow middle = new LaserRow(spriteTubeMiddle, 0, 0, 16, 8);

        LaserRow[] middles = { middle };

        TUBE_LASER = new LaserType(cap, middle, middles, null, cap);
    }

    public RenderPump(BlockEntityRendererProvider.Context context) {
    }

    //? if >=1.21.10 {
    @Override
    public PumpRenderState createRenderState() {
        return new PumpRenderState();
    }
    //?}

    @Override
    public boolean shouldRender(TilePump tile, Vec3 cameraPos) {
        // Always render — the tube can extend far below, so normal distance checks would cull it
        return true;
    }

    //? if >=1.21.10 {
    @Override
    public void submit(PumpRenderState renderState, PoseStack poseStack,
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
        if (!(be instanceof TilePump tile)) return;
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — the tile is passed directly, so no
    // camera-pos reconstruction is needed. The passed buffers/packedLight/packedOverlay go unused
    // (the shared body below sources its own buffer/light, exactly as the modern submit path does).
    @Override
    public void render(TilePump tile, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {*/
    //?}

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        renderLEDs(tile, poseStack, bufferSource);
        bufferSource.endBatch();

        poseStack.popPose();
    }

    private void renderLEDs(TilePump tile, PoseStack poseStack,
                            MultiBufferSource.BufferSource bufferSource) {
        float percentFilled = tile.getPercentFilledForRender();
        int powerColour = COLOUR_POWER[(int) (percentFilled * (COLOUR_POWER.length - 1))];

        boolean complete = tile.isComplete();
        int statusColour = complete ? LedRenderUtil.COLOUR_OFF : LedRenderUtil.COLOUR_GREEN_ON;

        VertexConsumer consumer = bufferSource.getBuffer(BCLibRenderTypes.led());
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < 4; i++) {
            Direction dir = Direction.from2DDataValue(i);

            LED_POWER[i].center.colouri(powerColour);
            LED_STATUS[i].center.colouri(statusColour);

            // Skip the inward face — it's always hidden against the pump body
            Direction skipFace = dir.getOpposite();
            LED_POWER[i].render(pose, consumer, skipFace);
            LED_STATUS[i].render(pose, consumer, skipFace);
        }
    }
}
