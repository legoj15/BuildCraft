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

import buildcraft.builders.tile.TileArchitectTable;

/**
 * Block entity renderer for the architect table. Draws two LEDs on the front face
 * — green on the right, red on the left (viewer perspective) — at the pixel
 * positions originally hand-drawn into the 1.12.2 {@code led_green.png}
 * ({@code u=5, v=12}) and {@code led_red.png} ({@code u=3, v=12}) overlay textures.
 * <p>
 * Four states, driven by ({@link TileArchitectTable#getIsValid valid},
 * input-slot-present, output-slot-present):
 * <ul>
 *   <li><b>Invalid</b> — marker box missing/malformed → red lit, green dark.</li>
 *   <li><b>Idle</b> — valid box, no work pending → green lit, red dark.</li>
 *   <li><b>Scanning</b> — valid + input present + output empty (the same gate
 *       {@code TileArchitectTable.tick} uses to start scanning) → both lit.</li>
 *   <li><b>Done</b> — valid + output has a finished snapshot waiting for pickup →
 *       green lit, red half-lit (a "please remove the blueprint" cue).</li>
 * </ul>
 */
//? if >=1.21.10 {
public class RenderArchitectTable implements BlockEntityRenderer<TileArchitectTable, ArchitectTableRenderState> {
//?} else {
/*public class RenderArchitectTable implements BlockEntityRenderer<TileArchitectTable> {*/
//?}
    /** Half-intensity red. Roughly half of {@link LedRenderUtil#COLOUR_RED_ON} per ABGR channel —
     *  visible enough to read as lit, but clearly dimmer than the "scanning" red. */
    private static final int COLOUR_RED_HALF = 0xFF_11_11_77;

    private static final double LED_INSET = 0.4 / 16.0;
    /** Inner offset = 2.5/16, derived from {@code led_green.png} pixel u=5 → world-x 10.5/16 = face-centre + 2.5/16. */
    private static final double GREEN_OFFSET = 2.5 / 16.0;
    /** Outer offset = 4.5/16, derived from {@code led_red.png} pixel u=3 → world-x 12.5/16 = face-centre + 4.5/16. */
    private static final double RED_OFFSET = 4.5 / 16.0;
    /** Vertical = 3.5/16, derived from both PNGs' pixel v=12 → world-y from bottom = (16-12.5)/16. */
    private static final double Y = 3.5 / 16.0;

    private static final RenderPartCube LED_GREEN = new RenderPartCube();
    private static final RenderPartCube LED_RED = new RenderPartCube();

    public RenderArchitectTable(BlockEntityRendererProvider.Context context) {
    }

    //? if >=1.21.10 {
    @Override
    public ArchitectTableRenderState createRenderState() {
        return new ArchitectTableRenderState();
    }

    @Override
    public void submit(ArchitectTableRenderState renderState, PoseStack poseStack,
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
        if (!(be instanceof TileArchitectTable tile)) return;
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — the tile is passed, so no camera-pos
    // reconstruction is needed. The passed buffers/packedLight/packedOverlay/partialTicks go unused
    // (the shared body sources its own buffer/light from the level, as the modern submit path does).
    @Override
    public void render(TileArchitectTable tile, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockPos pos = tile.getBlockPos();
        Level level = tile.getLevel();
        if (level == null) return;*/
    //?}
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return;
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);

        // Skip the LEDs when their face is hidden by a neighbour — they sit on the front face, which the
        // chunk mesher culls when buried, so the LEDs must cull with it.
        if (!LedRenderUtil.isFaceVisible(level, pos, state, facing)) return;

        poseStack.pushPose();

        //? if >=1.21.10 {
        collector.submitCustomGeometry(poseStack, BCLibRenderTypes.led(),
                (pose, consumer) -> renderLEDs(tile, facing, pose, consumer));
        //?} else {
        /*MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        renderLEDs(tile, facing, poseStack.last(), bufferSource.getBuffer(BCLibRenderTypes.led()));
        bufferSource.endBatch();*/
        //?}

        poseStack.popPose();
    }

    private void renderLEDs(TileArchitectTable tile, Direction facing,
                            PoseStack.Pose pose, VertexConsumer consumer) {
        boolean valid = tile.getIsValid();
        boolean hasInput = !tile.getSnapshotIn().isEmpty();
        boolean hasOutput = !tile.getSnapshotOut().isEmpty();

        int greenColour;
        int redColour;
        if (!valid) {
            // No valid marker box neighbour
            greenColour = LedRenderUtil.COLOUR_OFF;
            redColour = LedRenderUtil.COLOUR_RED_ON;
        } else if (hasOutput) {
            // Output occupied — scanning is paused/done; the player needs to pull the snapshot out.
            // Checked before hasInput so the "input still present but output blocked" transient
            // case lands on done rather than scanning.
            greenColour = LedRenderUtil.COLOUR_GREEN_ON;
            redColour = COLOUR_RED_HALF;
        } else if (hasInput) {
            // Actively scanning — matches the gate in TileArchitectTable.tick().
            greenColour = LedRenderUtil.COLOUR_GREEN_ON;
            redColour = LedRenderUtil.COLOUR_RED_ON;
        } else {
            // Valid + idle (no input, no output)
            greenColour = LedRenderUtil.COLOUR_GREEN_ON;
            redColour = LedRenderUtil.COLOUR_OFF;
        }

        LedRenderUtil.setFacePosition(LED_GREEN, facing, LED_INSET, GREEN_OFFSET, Y);
        LedRenderUtil.setFacePosition(LED_RED, facing, LED_INSET, RED_OFFSET, Y);
        LED_GREEN.center.colouri(greenColour);
        LED_RED.center.colouri(redColour);

        // Skip the inward face — it's hidden against the block body
        Direction skipFace = facing.getOpposite();
        LED_GREEN.render(pose, consumer, skipFace);
        LED_RED.render(pose, consumer, skipFace);
    }
}
