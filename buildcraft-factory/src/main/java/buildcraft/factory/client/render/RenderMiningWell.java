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
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserRow;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.render.tile.RenderPartCube;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;

import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileMiningWell;

/**
 * Block entity renderer for the mining well. Renders colored LED indicators on
 * the front face and a laser-textured tube beam extending downward.
 * Ported from 1.12.2 RenderMiningWell + RenderTube.
 */
public class RenderMiningWell implements BlockEntityRenderer<TileMiningWell, MiningWellRenderState> {
    private static final int[] COLOUR_POWER = new int[16];
    private static final int COLOUR_STATUS_ON = 0xFF_77_DD_77; // a light green
    private static final int COLOUR_STATUS_OFF = 0xFF_1f_10_1b; // black-ish

    private static final int BLOCK_LIGHT_STATUS_ON = 0xF;
    private static final int BLOCK_LIGHT_STATUS_OFF = 0x0;

    private static final double POWER = 2.5 / 16.0;
    private static final double STATUS = 4.5 / 16.0;
    private static final double Y = 5.5 / 16.0;

    private static final RenderPartCube LED_POWER = new RenderPartCube();
    private static final RenderPartCube LED_STATUS = new RenderPartCube();

    private static final LaserType TUBE_LASER;

    private static boolean ledsInitialized = false;

    static {
        for (int i = 0; i < COLOUR_POWER.length; i++) {
            int c = ((i * 0x40) / COLOUR_POWER.length) & 0xFF;
            int r = (((i * 0xB0) / COLOUR_POWER.length) & 0xFF) + 0x4F;
            int colour = (0xFF << 24) | (c << 16) | (c << 8) | r;
            COLOUR_POWER[i] = colour;
        }

        SpriteHolder spriteTubeMiddle = SpriteHolderRegistry.getHolder("buildcraftunofficial:block/mining_well/tube");
        LaserRow cap = new LaserRow(spriteTubeMiddle, 0, 8, 8, 16);
        LaserRow middle = new LaserRow(spriteTubeMiddle, 0, 0, 16, 8);

        LaserRow[] middles = { middle };

        TUBE_LASER = new LaserType(cap, middle, middles, null, cap);
    }

    public RenderMiningWell(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public MiningWellRenderState createRenderState() {
        return new MiningWellRenderState();
    }

    @Override
    public boolean shouldRender(TileMiningWell tile, Vec3 cameraPos) {
        // Always render — the tube can extend far below, so normal distance checks would cull it
        return true;
    }

    @Override
    public void submit(MiningWellRenderState renderState, PoseStack poseStack,
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
        if (!(be instanceof TileMiningWell tile)) return;

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();

        // --- LED Rendering ---
        if (!ledsInitialized) {
            ledsInitialized = true;
            LED_POWER.setWhiteTex();
            LED_STATUS.setWhiteTex();
        }
        renderLEDs(tile, pos, level, poseStack, bufferSource);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private void renderLEDs(TileMiningWell tile, BlockPos pos, Level level,
                            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        // Get facing from blockstate
        BlockState state = level.getBlockState(pos);
        Direction facing = Direction.NORTH;
        if (state.is(BCFactoryBlocks.MINING_WELL.get())) {
            facing = state.getValue(BuildCraftProperties.BLOCK_FACING);
        }

        final int dX, dZ;
        final double ledX, ledZ;

        if (facing.getAxis() == Axis.X) {
            dX = 0;
            dZ = facing.getAxisDirection().getStep();
            ledZ = 0.5;
            if (facing == Direction.EAST) {
                ledX = 15.8 / 16.0;
            } else {
                ledX = 0.2 / 16.0;
            }
        } else {
            dX = -facing.getAxisDirection().getStep();
            dZ = 0;
            ledX = 0.5;
            if (facing == Direction.SOUTH) {
                ledZ = 15.8 / 16.0;
            } else {
                ledZ = 0.2 / 16.0;
            }
        }

        // Get light from the adjacent block in the facing direction
        BlockPos adjPos = pos.relative(facing);
        int block = level.getBrightness(LightLayer.BLOCK, adjPos);
        int sky = level.getBrightness(LightLayer.SKY, adjPos);

        float percentFilled = tile.getPercentFilledForRender();
        int colourIndex = (int) (percentFilled * (COLOUR_POWER.length - 1));

        LED_POWER.center.positiond(ledX + dX * POWER, Y, ledZ + dZ * POWER);
        LED_POWER.center.colouri(COLOUR_POWER[colourIndex]);
        LED_POWER.center.lighti(block, sky);
        LED_POWER.center.maxLighti(percentFilled > 0.01 ? BLOCK_LIGHT_STATUS_ON : BLOCK_LIGHT_STATUS_OFF, 0);

        boolean complete = tile.isComplete();

        LED_STATUS.center.positiond(ledX + dX * STATUS, Y, ledZ + dZ * STATUS);
        LED_STATUS.center.colouri(complete ? COLOUR_STATUS_OFF : COLOUR_STATUS_ON);
        LED_STATUS.center.lighti(block, sky);
        LED_STATUS.center.maxLighti(complete ? BLOCK_LIGHT_STATUS_OFF : BLOCK_LIGHT_STATUS_ON, 0);

        VertexConsumer consumer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();

        // Skip the inward face — it's always hidden against the block body
        Direction skipFace = facing.getOpposite();
        LED_POWER.render(pose, consumer, skipFace);
        LED_STATUS.render(pose, consumer, skipFace);
    }
}
