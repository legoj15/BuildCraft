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
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.tiles.IControllable.Mode;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.client.render.tile.LedRenderUtil;
import buildcraft.lib.client.render.tile.RenderPartCube;

import buildcraft.builders.tile.TileFiller;

/**
 * Block entity renderer for the filler. Renders two LEDs on all four horizontal
 * faces at the same offsets the pump uses ({@code Y=13.5/16}, sideOffsets
 * {@code 1.5/16} and {@code 3.5/16}) — these are derived from the original
 * 1.12.2 {@code led_green.png} (u=6, v=2) and {@code led_red.png} (u=4, v=2)
 * overlay textures, which the art team placed at the same pixels as the pump
 * (a nice consistency that lets us share the offset values across both blocks).
 * <p>
 * LED state machine — driven by ({@link TileFiller#getControlMode() controlMode},
 * {@link TileFiller#hasPower() hasPower}, {@link TileFiller#isFinished() isFinished}):
 * <ul>
 *   <li><b>OFF</b> (mode == OFF) → both dark. Filler was manually disabled.</li>
 *   <li><b>No power</b> (mode != OFF, !hasPower) → red lit, green dark.</li>
 *   <li><b>Running, one-shot</b> (mode == ON, hasPower, !finished) → green lit full, red dark.</li>
 *   <li><b>Running, looping</b> (mode == LOOP, hasPower) → green <em>half-lit</em>, red dark.
 *       The half-lit green is the at-a-glance "this filler is in loop mode" indicator —
 *       lets the player tell a one-shot from a looping filler without opening the GUI.</li>
 *   <li><b>Finished</b> (mode == ON, finished, hasPower) → both lit full.
 *       Only reachable in ON mode — {@code isFinished()} returns false in LOOP because
 *       {@code TileFiller.isFinished()} short-circuits to {@code mode != LOOP && finished}.</li>
 * </ul>
 */
//? if >=1.21.10 {
public class RenderFiller implements BlockEntityRenderer<TileFiller, FillerRenderState> {
//?} else {
/*public class RenderFiller implements BlockEntityRenderer<TileFiller> {*/
//?}
    /** Half-intensity green — the "LOOP mode" indicator. Roughly half each ABGR channel of
     *  {@link LedRenderUtil#COLOUR_GREEN_ON} ({@code 0xFF_77_DD_77} → {@code 0xFF_3F_77_3F}). */
    private static final int COLOUR_GREEN_HALF = 0xFF_3F_77_3F;

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

    public RenderFiller(BlockEntityRendererProvider.Context context) {
    }

    //? if >=1.21.10 {
    @Override
    public FillerRenderState createRenderState() {
        return new FillerRenderState();
    }

    @Override
    public void submit(FillerRenderState renderState, PoseStack poseStack,
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
        if (!(be instanceof TileFiller tile)) return;
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — the tile is passed, so no camera-pos
    // reconstruction is needed. The passed buffers/packedLight/packedOverlay/partialTicks go unused
    // (the shared body sources its own buffer/light from the level, as the modern submit path does).
    @Override
    public void render(TileFiller tile, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {*/
    //?}
        poseStack.pushPose();

        //? if >=1.21.10 {
        collector.submitCustomGeometry(poseStack, BCLibRenderTypes.led(),
                (pose, consumer) -> renderLEDs(tile, pose, consumer));
        //?} else {
        /*MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        renderLEDs(tile, poseStack.last(), bufferSource.getBuffer(BCLibRenderTypes.led()));
        bufferSource.endBatch();*/
        //?}

        poseStack.popPose();
    }

    private void renderLEDs(TileFiller tile, PoseStack.Pose pose, VertexConsumer consumer) {
        Mode controlMode = tile.getControlMode();
        boolean hasPower = tile.hasPower();
        boolean finished = tile.isFinished();

        int greenColour;
        int redColour;
        if (controlMode == Mode.OFF) {
            greenColour = LedRenderUtil.COLOUR_OFF;
            redColour = LedRenderUtil.COLOUR_OFF;
        } else if (!hasPower) {
            // ON/LOOP but battery empty
            greenColour = LedRenderUtil.COLOUR_OFF;
            redColour = LedRenderUtil.COLOUR_RED_ON;
        } else if (finished) {
            // Only reachable in ON mode (LOOP's isFinished() short-circuits to false)
            greenColour = LedRenderUtil.COLOUR_GREEN_ON;
            redColour = LedRenderUtil.COLOUR_RED_ON;
        } else if (controlMode == Mode.LOOP) {
            // Running, looping
            greenColour = COLOUR_GREEN_HALF;
            redColour = LedRenderUtil.COLOUR_OFF;
        } else {
            // Running, ON (one-shot)
            greenColour = LedRenderUtil.COLOUR_GREEN_ON;
            redColour = LedRenderUtil.COLOUR_OFF;
        }

        for (int i = 0; i < 4; i++) {
            Direction dir = Direction.from2DDataValue(i);

            LED_GREEN[i].center.colouri(greenColour);
            LED_RED[i].center.colouri(redColour);

            // Skip the inward face — it's hidden against the filler body
            Direction skipFace = dir.getOpposite();
            LED_GREEN[i].render(pose, consumer, skipFace);
            LED_RED[i].render(pose, consumer, skipFace);
        }
    }
}
