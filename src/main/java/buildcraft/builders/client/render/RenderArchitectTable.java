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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
 * State: green lit when the architect has a valid marker box neighbour
 * ({@link TileArchitectTable#getIsValid()} returns {@code true}); red lit otherwise.
 * The {@code valid} state was previously driven by a {@code PROP_VALID} blockstate
 * property swapping between identical {@code front_on}/{@code front_off} textures —
 * a no-op visually. With this BER the same boolean drives a real visible change.
 */
public class RenderArchitectTable implements BlockEntityRenderer<TileArchitectTable, ArchitectTableRenderState> {
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

        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return;
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        renderLEDs(tile, facing, poseStack, bufferSource);
        bufferSource.endBatch();

        poseStack.popPose();
    }

    private void renderLEDs(TileArchitectTable tile, Direction facing,
                            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        boolean valid = tile.getIsValid();
        int greenColour = valid ? LedRenderUtil.COLOUR_GREEN_ON : LedRenderUtil.COLOUR_OFF;
        int redColour = valid ? LedRenderUtil.COLOUR_OFF : LedRenderUtil.COLOUR_RED_ON;

        LedRenderUtil.setFacePosition(LED_GREEN, facing, LED_INSET, GREEN_OFFSET, Y);
        LedRenderUtil.setFacePosition(LED_RED, facing, LED_INSET, RED_OFFSET, Y);
        LED_GREEN.center.colouri(greenColour);
        LED_RED.center.colouri(redColour);

        VertexConsumer consumer = bufferSource.getBuffer(BCLibRenderTypes.led());
        PoseStack.Pose pose = poseStack.last();

        // Skip the inward face — it's hidden against the block body
        Direction skipFace = facing.getOpposite();
        LED_GREEN.render(pose, consumer, skipFace);
        LED_RED.render(pose, consumer, skipFace);
    }
}
