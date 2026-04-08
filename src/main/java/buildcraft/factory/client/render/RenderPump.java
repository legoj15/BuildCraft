/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
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
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserRow;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.render.tile.RenderPartCube;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;

import buildcraft.factory.tile.TilePump;

/**
 * Block entity renderer for the pump. Renders colored LED indicators on all
 * four sides and a laser-textured tube beam extending downward.
 * Ported from 1.12.2 RenderPump + RenderTube.
 */
public class RenderPump implements BlockEntityRenderer<TilePump, PumpRenderState> {
    private static final int[] COLOUR_POWER = new int[16];
    private static final int COLOUR_STATUS_ON = 0xFF_77_DD_77; // a light green
    private static final int COLOUR_STATUS_OFF = 0xFF_1f_10_1b; // black-ish

    private static final int BLOCK_LIGHT_STATUS_ON = 0xF;
    private static final int BLOCK_LIGHT_STATUS_OFF = 0x0;

    private static final double POWER = 1.5 / 16.0;
    private static final double STATUS = 3.5 / 16.0;
    private static final double Y = 13.5 / 16.0;

    private static final RenderPartCube[] LED_POWER = new RenderPartCube[4];
    private static final RenderPartCube[] LED_STATUS = new RenderPartCube[4];

    private static final LaserType TUBE_LASER;

    private static boolean ledsInitialized = false;

    static {
        for (int i = 0; i < COLOUR_POWER.length; i++) {
            int c = (i * 0x40) / COLOUR_POWER.length;
            int r = (i * 0xE0) / COLOUR_POWER.length + 0x1F;
            int colour = (0xFF << 24) + (c << 16) + (c << 8) + r;
            COLOUR_POWER[i] = colour;
        }

        for (int i = 0; i < 4; i++) {
            Direction facing = Direction.from2DDataValue(i);

            final int dX, dZ;
            final double ledX, ledZ;

            if (facing.getAxis() == Axis.X) {
                dX = 0;
                dZ = facing.getAxisDirection().getStep();
                ledZ = 0.5;
                if (facing == Direction.EAST) {
                    ledX = 15.6 / 16.0;
                } else {
                    ledX = 0.4 / 16.0;
                }
            } else {
                dX = -facing.getAxisDirection().getStep();
                dZ = 0;
                ledX = 0.5;
                if (facing == Direction.SOUTH) {
                    ledZ = 15.6 / 16.0;
                } else {
                    ledZ = 0.4 / 16.0;
                }
            }

            LED_POWER[i] = new RenderPartCube();
            LED_POWER[i].center.positiond(ledX + dX * POWER, Y, ledZ + dZ * POWER);

            LED_STATUS[i] = new RenderPartCube();
            LED_STATUS[i].center.positiond(ledX + dX * STATUS, Y, ledZ + dZ * STATUS);
        }

        SpriteHolder spriteTubeMiddle = SpriteHolderRegistry.getHolder("buildcraftunofficial:block/pump/tube");
        LaserRow cap = new LaserRow(spriteTubeMiddle, 0, 8, 8, 16);
        LaserRow middle = new LaserRow(spriteTubeMiddle, 0, 0, 16, 8);

        LaserRow[] middles = { middle };

        TUBE_LASER = new LaserType(cap, middle, middles, null, cap);
    }

    public RenderPump(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public PumpRenderState createRenderState() {
        return new PumpRenderState();
    }

    @Override
    public boolean shouldRender(TilePump tile, Vec3 cameraPos) {
        // Always render — the tube can extend far below, so normal distance checks would cull it
        return true;
    }

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

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();

        // --- LED Rendering ---
        if (!ledsInitialized) {
            ledsInitialized = true;
            for (int i = 0; i < 4; i++) {
                LED_POWER[i].setWhiteTex();
                LED_STATUS[i].setWhiteTex();
            }
        }
        renderLEDs(tile, pos, level, poseStack, bufferSource);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private void renderLEDs(TilePump tile, BlockPos pos, Level level,
                            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        float percentFilled = tile.getPercentFilledForRender();
        int powerColour = COLOUR_POWER[(int) (percentFilled * (COLOUR_POWER.length - 1))];

        boolean complete = tile.isComplete();
        int statusColour = complete ? COLOUR_STATUS_OFF : COLOUR_STATUS_ON;
        int statusBlockLight = complete ? BLOCK_LIGHT_STATUS_OFF : BLOCK_LIGHT_STATUS_ON;

        VertexConsumer consumer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < 4; i++) {
            // Get the light level from the adjacent block for this face
            Direction dir = Direction.from2DDataValue(i);
            BlockPos adjPos = pos.relative(dir);
            int block = level.getBrightness(LightLayer.BLOCK, adjPos);
            int sky = level.getBrightness(LightLayer.SKY, adjPos);

            LED_POWER[i].center.colouri(powerColour);
            LED_STATUS[i].center.colouri(statusColour);

            LED_POWER[i].center.lighti(block, sky);
            LED_STATUS[i].center.lighti(Math.max(statusBlockLight, block), sky);

            // Skip the inward face — it's always hidden against the pump body
            Direction skipFace = dir.getOpposite();
            LED_POWER[i].render(pose, consumer, skipFace);
            LED_STATUS[i].render(pose, consumer, skipFace);
        }
    }
}
