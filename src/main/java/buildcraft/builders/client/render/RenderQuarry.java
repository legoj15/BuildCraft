/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
//? if <1.21.10 {
/*import net.minecraft.client.renderer.MultiBufferSource;*/
//?}
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.client.render.tile.LedRenderUtil;
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
//? if >=1.21.10 {
public class RenderQuarry implements BlockEntityRenderer<TileQuarry, QuarryRenderState> {
//?} else {
/*public class RenderQuarry implements BlockEntityRenderer<TileQuarry> {*/
//?}
    private static final double LED_INSET = 0.4 / 16.0;
    private static final double GREEN_OFFSET = 1.5 / 16.0;
    private static final double RED_OFFSET = 3.5 / 16.0;
    private static final double Y = 13.5 / 16.0;

    private static final RenderPartCube[] LED_GREEN = new RenderPartCube[4];
    private static final RenderPartCube[] LED_RED = new RenderPartCube[4];

    static {
        for (int i = 0; i < 4; i++) {
            Direction face = Direction.from2DDataValue(i);
            LED_GREEN[i] = new RenderPartCube();
            LED_RED[i] = new RenderPartCube();
            LedRenderUtil.setFacePosition(LED_GREEN[i], face, LED_INSET, GREEN_OFFSET, Y);
            LedRenderUtil.setFacePosition(LED_RED[i], face, LED_INSET, RED_OFFSET, Y);
        }
    }

    public RenderQuarry(BlockEntityRendererProvider.Context context) {
    }

    /** Called from BCBuildersClient to classload this class at registration time. */
    public static void init() {
    }

    //? if >=1.21.10 {
    @Override
    public QuarryRenderState createRenderState() {
        return new QuarryRenderState();
    }

    @Override
    public void submit(QuarryRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        // The render state already carries the world pos — no camera-pos reconstruction needed.
        BlockPos pos = renderState.blockPos;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileQuarry tile)) return;
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — the tile is passed, so no camera-pos
    // reconstruction is needed. The passed buffers/packedLight/packedOverlay/partialTicks go unused
    // (the shared body sources its own buffer/light from the level, as the modern submit path does).
    @Override
    public void render(TileQuarry tile, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockPos pos = tile.getBlockPos();
        Level level = tile.getLevel();
        if (level == null) return;*/
    //?}
        BlockState state = level.getBlockState(pos);
        Direction front = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
        Direction rear = front.getOpposite();

        poseStack.pushPose();

        //? if >=1.21.10 {
        collector.submitCustomGeometry(poseStack, BCLibRenderTypes.led(),
                (pose, consumer) -> renderLEDs(tile, rear, pose, consumer));
        //?} else {
        /*MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        renderLEDs(tile, rear, poseStack.last(), bufferSource.getBuffer(BCLibRenderTypes.led()));
        bufferSource.endBatch();*/
        //?}

        poseStack.popPose();
    }

    private void renderLEDs(TileQuarry tile, Direction rear,
                            PoseStack.Pose pose, VertexConsumer consumer) {
        Level level = tile.getLevel();
        if (level == null) return;
        BlockPos pos = tile.getBlockPos();
        BlockState state = level.getBlockState(pos);

        boolean hasPower = tile.hasPower();
        boolean hasTask = tile.isMining();

        // Green: lit when working (or "no work to do" — combines with red to mark done).
        // Red:   lit when can't work (no power) or "no work to do".
        // Truth table: (hasPower, hasTask) →
        //   (T, T) Running:   Green only
        //   (F, T) NoPower:   Red only
        //   (T, F) Done:      Both
        //   (F, F) Idle/Done: Both
        boolean greenOn = hasPower || !hasTask;
        boolean redOn = !hasPower || !hasTask;

        int greenColour = greenOn ? LedRenderUtil.COLOUR_GREEN_ON : LedRenderUtil.COLOUR_OFF;
        int redColour = redOn ? LedRenderUtil.COLOUR_RED_ON : LedRenderUtil.COLOUR_OFF;

        for (int i = 0; i < 4; i++) {
            Direction dir = Direction.from2DDataValue(i);
            if (dir == rear) continue; // omit LEDs on the rear face
            if (!LedRenderUtil.isFaceVisible(level, pos, state, dir)) continue; // face buried → no LEDs

            LED_GREEN[i].center.colouri(greenColour);
            LED_RED[i].center.colouri(redColour);

            // Skip the inward face — it's hidden against the quarry body.
            Direction skipFace = dir.getOpposite();
            LED_GREEN[i].render(pose, consumer, skipFace);
            LED_RED[i].render(pose, consumer, skipFace);
        }
    }
}
