/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.client.render.tile.RenderPartCube;

import buildcraft.builders.tile.TileQuarry;

/**
 * Block entity renderer for the quarry. Renders two LED indicators on three of
 * the four horizontal faces (the rear face — opposite to the block's FACING — is
 * omitted because that side is conventionally cabled or wall-mounted).
 * <p>
 * LED states:
 * <ul>
 *   <li>Green only — actively mining (has power, has work).</li>
 *   <li>Red only — out of power but still has work to do.</li>
 *   <li>Both — no current task (mining complete, or setup not yet finished).</li>
 * </ul>
 * <p>
 * Note: world-space rendering (frame/drill lasers, rigs) is still handled by
 * {@code BCBuildersEventDist} via {@link net.neoforged.neoforge.client.event.RenderLevelStageEvent}
 * because those cross block boundaries; this BER only covers the block-local LEDs.
 */
public class RenderQuarry implements BlockEntityRenderer<TileQuarry, QuarryRenderState> {
    private static final int COLOUR_GREEN_ON = 0xFF_77_DD_77; // a light green (ABGR)
    private static final int COLOUR_RED_ON = 0xFF_22_22_DD; // a vivid red (ABGR — red is the low byte; green/blue dropped to avoid the pink read of a symmetric 0x77 fill)
    private static final int COLOUR_OFF = 0xFF_1f_10_1b; // black-ish

    private static final int BLOCK_LIGHT_ON = 0xF;
    private static final int BLOCK_LIGHT_OFF = 0x0;

    private static final double GREEN_OFFSET = 1.5 / 16.0;
    private static final double RED_OFFSET = 3.5 / 16.0;
    private static final double Y = 13.5 / 16.0;

    private static final RenderPartCube[] LED_GREEN = new RenderPartCube[4];
    private static final RenderPartCube[] LED_RED = new RenderPartCube[4];

    private static boolean ledsInitialized = false;

    static {
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

            LED_GREEN[i] = new RenderPartCube();
            LED_GREEN[i].center.positiond(ledX + dX * GREEN_OFFSET, Y, ledZ + dZ * GREEN_OFFSET);

            LED_RED[i] = new RenderPartCube();
            LED_RED[i].center.positiond(ledX + dX * RED_OFFSET, Y, ledZ + dZ * RED_OFFSET);
        }
    }

    public RenderQuarry(BlockEntityRendererProvider.Context context) {
    }

    /** Called from BCBuildersClient to classload this class at registration time. */
    public static void init() {
    }

    @Override
    public QuarryRenderState createRenderState() {
        return new QuarryRenderState();
    }

    @Override
    public void submit(QuarryRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        net.minecraft.world.phys.Vec3 camPos = cameraState.pos;
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
        if (!(be instanceof TileQuarry tile)) return;

        BlockState state = level.getBlockState(pos);
        Direction front = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
        Direction rear = front.getOpposite();

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();

        if (!ledsInitialized) {
            ledsInitialized = true;
            for (int i = 0; i < 4; i++) {
                LED_GREEN[i].setWhiteTex();
                LED_RED[i].setWhiteTex();
            }
        }
        renderLEDs(tile, pos, level, rear, poseStack, bufferSource);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private void renderLEDs(TileQuarry tile, BlockPos pos, Level level, Direction rear,
                            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        boolean hasPower = tile.hasPower();
        boolean hasTask = tile.isMining();

        // Green: lit when working (or "no work to do" — combines with red to mark done).
        // Red:   lit when can't work (no power) or "no work to do".
        // Truth table: (hasPower, hasTask) →
        //   (T, T) Running:  Green only
        //   (F, T) NoPower:  Red only
        //   (T, F) Done:     Both
        //   (F, F) Idle/Done: Both
        boolean greenOn = hasPower || !hasTask;
        boolean redOn = !hasPower || !hasTask;

        int greenColour = greenOn ? COLOUR_GREEN_ON : COLOUR_OFF;
        int redColour = redOn ? COLOUR_RED_ON : COLOUR_OFF;
        int greenBlockLight = greenOn ? BLOCK_LIGHT_ON : BLOCK_LIGHT_OFF;
        int redBlockLight = redOn ? BLOCK_LIGHT_ON : BLOCK_LIGHT_OFF;

        VertexConsumer consumer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < 4; i++) {
            Direction dir = Direction.from2DDataValue(i);
            if (dir == rear) continue; // omit LEDs on the rear face

            BlockPos adjPos = pos.relative(dir);
            int block = level.getBrightness(LightLayer.BLOCK, adjPos);
            int sky = level.getBrightness(LightLayer.SKY, adjPos);

            LED_GREEN[i].center.colouri(greenColour);
            LED_RED[i].center.colouri(redColour);

            LED_GREEN[i].center.lighti(Math.max(greenBlockLight, block), sky);
            LED_RED[i].center.lighti(Math.max(redBlockLight, block), sky);

            // Skip the inward face — it's hidden against the quarry body.
            Direction skipFace = dir.getOpposite();
            LED_GREEN[i].render(pose, consumer, skipFace);
            LED_RED[i].render(pose, consumer, skipFace);
        }
    }
}
