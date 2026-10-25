/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
//? if <1.21.10 {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;*/
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.tiles.IControllable.Mode;

import buildcraft.lib.client.render.BCLibRenderTypes;
//? if >=1.21.10 {
import buildcraft.lib.client.render.tile.BCRenderState;
//?}
import buildcraft.lib.client.render.tile.LedRenderUtil;
import buildcraft.lib.client.render.tile.RenderPartCube;

import buildcraft.builders.snapshot.Template;
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
 * <p>
 * Preview — with the GUI's preview toggle on and the Filler idle (nothing placing or breaking), translucent cubes are
 * drawn over every cell the pattern still has to fill ({@link FillPreviewRenderer}; the cells come from the tile's
 * {@code FillPreviewCache}). The box is usually bigger than the block, so this renderer is globally rendered, reports
 * the box as its bounding box and is distance-checked against it: the preview stays up when the Filler block itself
 * is off screen or far away.
 */
//? if >=1.21.10 {
public class RenderFiller implements BlockEntityRenderer<TileFiller, BCRenderState> {
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

    // A block entity renderer is only reached through its block's chunk section being on screen unless it is rendered
    // "off screen" (globally); the preview has to outlive the Filler block leaving view, so every Filler is.
    //? if >=1.21.10 {
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
    //?} else {
    /*@Override
    public boolean shouldRenderOffScreen(TileFiller tile) {
        return true;
    }*/
    //?}

    /** The block, or — with the preview on — the block and the whole box, so the frustum test keeps the renderer alive. */
    @Override
    public AABB getRenderBoundingBox(TileFiller tile) {
        return tile.getRenderBounds();
    }

    /** Every node's dispatcher asks this before drawing a BER, globally rendered or not (checked in each node's
     *  {@code BlockEntityRenderDispatcher}), so the distance gate below applies to the preview too. */
    @Override
    public boolean shouldRender(TileFiller tile, Vec3 cameraPos) {
        if (tile.isPreviewEnabled()) {
            // Within view distance of the nearest point of the block or the box, not of the block's centre.
            AABB bounds = tile.getRenderBounds();
            double dx = Math.max(Math.max(bounds.minX - cameraPos.x, 0), cameraPos.x - bounds.maxX);
            double dy = Math.max(Math.max(bounds.minY - cameraPos.y, 0), cameraPos.y - bounds.maxY);
            double dz = Math.max(Math.max(bounds.minZ - cameraPos.z, 0), cameraPos.z - bounds.maxZ);
            double view = getViewDistance();
            if (dx * dx + dy * dy + dz * dz <= view * view) {
                return true;
            }
        }
        return BlockEntityRenderer.super.shouldRender(tile, cameraPos);
    }

    //? if >=1.21.10 {
    @Override
    public BCRenderState createRenderState() {
        return new BCRenderState();
    }

    @Override
    public void submit(BCRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        // The render state already carries the world pos — no camera-pos reconstruction needed.
        BlockPos pos = renderState.blockPos;

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
        List<BlockPos> preview = previewCells(tile, cameraState.pos);
        if (!preview.isEmpty()) {
            BlockPos origin = tile.getBlockPos();
            collector.submitCustomGeometry(poseStack, BCLibRenderTypes.debugFilled(),
                    (pose, consumer) -> FillPreviewRenderer.emit(consumer, pose.pose(), preview, origin));
        }
        //?} else {
        /*// Each type is flushed on its own: a bare endBatch() would flush every type pending in the frame, twice per Filler.
        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType led = BCLibRenderTypes.led();
        renderLEDs(tile, poseStack.last(), bufferSource.getBuffer(led));
        bufferSource.endBatch(led);
        List<BlockPos> preview = previewCells(tile, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        if (!preview.isEmpty()) {
            RenderType filled = BCLibRenderTypes.debugFilled();
            FillPreviewRenderer.emit(bufferSource.getBuffer(filled), poseStack.last().pose(), preview, tile.getBlockPos());
            bufferSource.endBatch(filled);
        }*/
        //?}

        poseStack.popPose();
    }

    /** The cubes the preview draws this frame, farthest first: none with the toggle off, while the Filler is placing or
     *  breaking, or when nothing is left to fill. */
    private static List<BlockPos> previewCells(TileFiller tile, Vec3 camera) {
        if (!tile.isPreviewEnabled()) {
            tile.previewCache.clear(); // an off preview keeps no cell lists alive
            return List.of();
        }
        Level level = tile.getLevel();
        Template.BuildingInfo info = tile.getTemplateBuildingInfo();
        if (level == null || info == null) {
            return List.of();
        }
        if (!tile.previewCache.isIdle(level.getGameTime(), tile.hasWorkInFlight())) {
            return List.of();
        }
        return tile.previewCache.cellsToDraw(level, info, camera);
    }

    private void renderLEDs(TileFiller tile, PoseStack.Pose pose, VertexConsumer consumer) {
        Level level = tile.getLevel();
        if (level == null) return;
        BlockPos pos = tile.getBlockPos();
        BlockState state = level.getBlockState(pos);

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
            if (!LedRenderUtil.isFaceVisible(level, pos, state, dir)) continue; // face buried → no LEDs

            LED_GREEN[i].center.colouri(greenColour);
            LED_RED[i].center.colouri(redColour);

            // Skip the inward face — it's hidden against the filler body
            Direction skipFace = dir.getOpposite();
            LED_GREEN[i].render(pose, consumer, skipFace);
            LED_RED[i].render(pose, consumer, skipFace);
        }
    }
}
