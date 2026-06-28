/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

import org.joml.Matrix4f;

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
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.client.render.BCLibRenderTypes;

import buildcraft.robotics.block.BlockZonePlanner;
import buildcraft.robotics.client.zone.ZoneFacePreview;
import buildcraft.robotics.client.zone.ZonePlannerMapChunk;
import buildcraft.robotics.client.zone.ZonePlannerMapDataClient;
import buildcraft.robotics.tile.TileZonePlanner;

/**
 * Block entity renderer for the Zone Planner: paints a small live top-down terrain map onto the block's
 * front face — the in-world "screen", restored from 1.12.2's {@code RenderZonePlanner}. Each frame it
 * samples a {@link ZoneFacePreview#GRID_W}&times;{@link ZoneFacePreview#GRID_H} grid of cells over a
 * window centred on the planner (rotated with the block's facing, see {@link ZoneFacePreview}) and draws
 * one flat coloured cell per loaded column onto the front-face rectangle. Terrain colour only — no zones,
 * no relief — matching the original.
 *
 * <p><b>No texture.</b> Unlike 1.12.2's per-block {@code DynamicTextureBC} (a GL texture that had to be
 * cached and freed), the cells are emitted as vertex-coloured quads through {@link BCLibRenderTypes#led()}
 * — the same untextured POSITION_COLOR path the architect/mining-well face LEDs use — so there is nothing
 * to allocate, upload, or clean up. The colour data is the client-side cache the GUI viewport already
 * builds ({@link ZonePlannerMapDataClient}), so the in-world preview and the GUI map always agree.
 *
 * <p>Columns whose chunk is unloaded (or has no recordable surface) are simply skipped — the preview
 * degrades to gaps at the edges rather than blanking, matching the GUI viewport.
 */
//? if >=1.21.10 {
public class RenderZonePlanner implements BlockEntityRenderer<TileZonePlanner, ZonePlannerRenderState> {
//?} else {
/*public class RenderZonePlanner implements BlockEntityRenderer<TileZonePlanner> {*/
//?}

    // Front-face screen rectangle, in block-local (0..1) units — matches 1.12.2's 3/16..13/16 (x) and
    // 5/16..13/16 (y). With GRID_W=10 / GRID_H=8 each cell is exactly 1/16 of a block.
    private static final double RECT_H0 = 3.0 / 16.0;  // horizontal start across the face
    private static final double RECT_V0 = 5.0 / 16.0;  // vertical start (up from the block bottom)
    private static final double CELL = 1.0 / 16.0;
    /** How far the cells sit proud of the face, so they read in front of the block texture. */
    private static final double PROUD = 0.002;

    public RenderZonePlanner(BlockEntityRendererProvider.Context context) {
    }

    //? if >=1.21.10 {
    @Override
    public ZonePlannerRenderState createRenderState() {
        return new ZonePlannerRenderState();
    }

    @Override
    public void submit(ZonePlannerRenderState renderState, PoseStack poseStack,
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
        if (!(be instanceof TileZonePlanner)) return;
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — the tile is passed, so pos/level come straight
    // off it (no camera-pos reconstruction). The passed buffers/light/overlay/partialTicks go unused; the
    // shared body sources its own buffer, as the modern submit path does.
    @Override
    public void render(TileZonePlanner tile, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockPos pos = tile.getBlockPos();
        Level level = tile.getLevel();
        if (level == null) return;*/
    //?}
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockZonePlanner.FACING)) return;
        Direction facing = state.getValue(BlockZonePlanner.FACING);

        poseStack.pushPose();

        //? if >=1.21.10 {
        collector.submitCustomGeometry(poseStack, BCLibRenderTypes.led(),
                (pose, consumer) -> renderFace(pos, facing, level, pose.pose(), consumer));
        //?} else {
        /*MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        renderFace(pos, facing, level, poseStack.last().pose(), bufferSource.getBuffer(BCLibRenderTypes.led()));
        bufferSource.endBatch();*/
        //?}

        poseStack.popPose();
    }

    /** Emits one flat coloured cell per loaded column of the preview grid onto the front face. */
    private void renderFace(BlockPos pos, Direction facing, Level level, Matrix4f mat, VertexConsumer vc) {
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;
        for (int sx = 0; sx < ZoneFacePreview.GRID_W; sx++) {
            for (int sy = 0; sy < ZoneFacePreview.GRID_H; sy++) {
                int[] col = ZoneFacePreview.worldColumn(pos.getX(), pos.getZ(), facing, sx, sy);
                int wx = col[0], wz = col[1];
                ZonePlannerMapChunk chunk = data.getChunk(level, wx >> 4, wz >> 4);
                if (chunk == null) continue;                       // unloaded → gap
                int lx = wx & 15, lz = wz & 15;
                if (!chunk.hasData(lx, lz)) continue;              // empty column → gap
                int c = chunk.getColour(lx, lz);
                if (c == 0) continue;
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                emitCell(mat, vc, facing, sx, sy, r, g, b);
            }
        }
    }

    /** A single double-wound (cull-immune) coloured quad for one grid cell, laid on the front face. */
    private void emitCell(Matrix4f mat, VertexConsumer vc, Direction facing, int sx, int sy,
                          int r, int g, int b) {
        double h0 = RECT_H0 + sx * CELL, h1 = h0 + CELL;
        double v0 = RECT_V0 + sy * CELL, v1 = v0 + CELL;
        double[] p00 = facePoint(facing, h0, v0);
        double[] p10 = facePoint(facing, h1, v0);
        double[] p11 = facePoint(facing, h1, v1);
        double[] p01 = facePoint(facing, h0, v1);
        // Front winding, then reversed — so back-face culling can't drop the cell whichever way the
        // outward normal lands (the faces are opaque and coplanar, so double-emitting is harmless).
        vertex(mat, vc, p00, r, g, b);
        vertex(mat, vc, p10, r, g, b);
        vertex(mat, vc, p11, r, g, b);
        vertex(mat, vc, p01, r, g, b);
        vertex(mat, vc, p01, r, g, b);
        vertex(mat, vc, p11, r, g, b);
        vertex(mat, vc, p10, r, g, b);
        vertex(mat, vc, p00, r, g, b);
    }

    private static void vertex(Matrix4f mat, VertexConsumer vc, double[] p, int r, int g, int b) {
        vc.addVertex(mat, (float) p[0], (float) p[1], (float) p[2]).setColor(r, g, b, 255);
    }

    /**
     * Maps a face-local position — horizontal {@code h} across the face, vertical {@code v} up the face,
     * both in block units (0..1) — to block-local {@code (x, y, z)} on the front face for the given facing,
     * sitting {@link #PROUD} proud of the face plane. The horizontal mirror per facing keeps the map's
     * left/right consistent as the player walks around the block.
     */
    private static double[] facePoint(Direction facing, double h, double v) {
        return switch (facing) {
            case NORTH -> new double[]{ 1 - h, v, -PROUD };
            case SOUTH -> new double[]{ h,     v, 1 + PROUD };
            case WEST  -> new double[]{ -PROUD, v, h };
            case EAST  -> new double[]{ 1 + PROUD, v, 1 - h };
            default    -> new double[]{ 1 - h, v, -PROUD };
        };
    }
}
