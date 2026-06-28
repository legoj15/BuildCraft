/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

// Whole-file >=1.21.10: built on the 1.21.5+ offscreen PiP pipeline, which doesn't exist on 1.21.1
// (where the Zone Planner keeps a non-rendered placeholder). Cross-node plumbing mirrors
// BlueprintPipRenderer: a 26.2 sub-cliff swaps the BufferSource constructor + endBatch flush for the
// SubmitNodeCollector "submit" model.
//? if >=1.21.10 {
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
//? if >=26.2 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;*/
//?} else {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.client.zone.ZonePlannerMapChunk;
import buildcraft.robotics.client.zone.ZonePlannerMapDataClient;
import buildcraft.robotics.zone.ZonePlan;

/**
 * Paints the Zone Planner's isometric terrain map into an offscreen texture; the base class blits it
 * into the GUI. Terrain comes from the client's own loaded chunks (see {@link ZonePlannerMapChunk});
 * the camera/projection ({@link ZoneMapCamera}) is sampled fresh from the render state each frame, so
 * pan/zoom/paint are simply different states submitted by the GUI.
 *
 * <p>Terrain is drawn as opaque, depth-writing colour cuboids (every face emitted in both windings so
 * back-face culling can't drop a skirt regardless of the cumulative pose determinant — safe because the
 * faces are opaque and coplanar). Zone tints and the hover box are translucent quads laid just above the
 * surface. Both use BuildCraft's POSITION_COLOR debug render types — no textures, no lighting — with
 * per-face shading baked into the vertex colour, the same trick the 1.12.2 renderer used.
 */
public class ZoneMapPipRenderer extends PictureInPictureRenderer<ZoneMapPipRenderState> {

    /** Per-face brightness, matching vanilla's block-face shading so relief reads naturally. */
    private static final float SHADE_TOP = 1.0f;
    private static final float SHADE_NS = 0.8f;
    private static final float SHADE_EW = 0.6f;

    /** Alpha (0-255) of a painted-zone tint laid over the terrain. */
    private static final int ZONE_ALPHA = 0x88;
    /** Alpha of the hover highlight. */
    private static final int HOVER_ALPHA = 0x99;
    /** Lift the zone/hover overlay quads this far above the surface so they don't z-fight the top face. */
    private static final float OVERLAY_LIFT = 0.05f;
    /** How far a map-edge column's skirt drops when it has no neighbour to step down to. */
    private static final int EDGE_SKIRT = 2;
    /** Safety cap on the chunk grid scanned per frame (low zoom shows a lot of world). */
    private static final int MAX_CHUNK_SPAN = 48;
    /** Extra world-block padding around the visible box so tall terrain entering from a screen edge isn't clipped. */
    private static final int RELIEF_PAD = 64;

    //? if >=26.2 {
    /*public ZoneMapPipRenderer() {
        super();
    }*/
    //?} else {
    public ZoneMapPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }
    //?}

    @Override
    public Class<ZoneMapPipRenderState> getRenderStateClass() {
        return ZoneMapPipRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "buildcraft_zone_map";
    }

    /** Origin at the centre of the texture so the camera sits in the middle of the viewport. */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0f;
    }

    //? if >=26.2 {
    /*@Override
    protected void renderToTexture(ZoneMapPipRenderState state, PoseStack poseStack,
                                   SubmitNodeCollector collector) {*/
    //?} else {
    @Override
    protected void renderToTexture(ZoneMapPipRenderState state, PoseStack poseStack) {
    //?}
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RenderType terrainType = BCLibRenderTypes.debugSolid();
        RenderType overlayType = BCLibRenderTypes.debugFilled();
        //? if >=26.2 {
        /*collector.submitCustomGeometry(poseStack, terrainType,
                (pose, vc) -> emitTerrain(vc, pose.pose(), state, level));
        collector.submitCustomGeometry(poseStack, overlayType,
                (pose, vc) -> emitOverlays(vc, pose.pose(), state, level));*/
        //?} else {
        Matrix4f mat = poseStack.last().pose();
        emitTerrain(this.bufferSource.getBuffer(terrainType), mat, state, level);
        emitOverlays(this.bufferSource.getBuffer(overlayType), mat, state, level);
        //?}
    }

    // ── Terrain ─────────────────────────────────────────────────────────────────────────

    private void emitTerrain(VertexConsumer vc, Matrix4f mat, ZoneMapPipRenderState state, Level level) {
        ZoneMapCamera cam = state.camera();
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;

        int wPx = state.x1() - state.x0();
        int hPx = state.y1() - state.y0();
        double[] b = cam.visibleWorldBounds(wPx, hPx, RELIEF_PAD);
        int minCX = (int) Math.floor(b[0]) >> 4;
        int minCZ = (int) Math.floor(b[1]) >> 4;
        int maxCX = (int) Math.floor(b[2]) >> 4;
        int maxCZ = (int) Math.floor(b[3]) >> 4;
        // Clamp the scanned grid so an extreme zoom-out can't ask for thousands of chunks.
        maxCX = Math.min(maxCX, minCX + MAX_CHUNK_SPAN);
        maxCZ = Math.min(maxCZ, minCZ + MAX_CHUNK_SPAN);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                ZonePlannerMapChunk chunk = data.getChunk(level, cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        if (!chunk.hasData(lx, lz)) {
                            continue;
                        }
                        int wx = (cx << 4) + lx;
                        int wz = (cz << 4) + lz;
                        int top = chunk.getSurfaceY(lx, lz);
                        int colour = chunk.getColour(lx, lz);
                        emitColumn(vc, mat, cam, level, data, wx, wz, top, colour);
                    }
                }
            }
        }
    }

    private void emitColumn(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam, Level level,
                            ZonePlannerMapDataClient data, int wx, int wz, int top, int colour) {
        float r = ((colour >> 16) & 0xFF) / 255f;
        float g = ((colour >> 8) & 0xFF) / 255f;
        float bl = (colour & 0xFF) / 255f;

        // Top face at the surface.
        quad(vc, mat, cam, SHADE_TOP, r, g, bl, 255,
                wx, top + 1, wz,
                wx + 1, top + 1, wz,
                wx + 1, top + 1, wz + 1,
                wx, top + 1, wz + 1);

        // Skirt faces only where a neighbour steps down (or the map ends), so the relief reads as clean
        // contour steps instead of full-height pillars.
        emitSkirt(vc, mat, cam, data, level, r, g, bl, SHADE_NS, wx, wz, top, /*dx*/ 0, /*dz*/ -1);
        emitSkirt(vc, mat, cam, data, level, r, g, bl, SHADE_NS, wx, wz, top, 0, 1);
        emitSkirt(vc, mat, cam, data, level, r, g, bl, SHADE_EW, wx, wz, top, -1, 0);
        emitSkirt(vc, mat, cam, data, level, r, g, bl, SHADE_EW, wx, wz, top, 1, 0);
    }

    /** Draws the vertical face on the (dx,dz) side of a column, dropping from {@code top} to the
     *  neighbour's surface (or {@link #EDGE_SKIRT} below if the neighbour is missing). Skipped when the
     *  neighbour is at least as high (face hidden). */
    private void emitSkirt(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                           ZonePlannerMapDataClient data, Level level,
                           float r, float g, float bl, float shade,
                           int wx, int wz, int top, int dx, int dz) {
        int neighbour = surfaceAt(data, level, wx + dx, wz + dz);
        int bottom;
        if (neighbour == ZonePlannerMapChunk.NO_DATA) {
            bottom = top - EDGE_SKIRT;
        } else if (neighbour >= top) {
            return; // hidden by the neighbour
        } else {
            bottom = neighbour;
        }
        int t = top + 1;
        // The face lives on the edge of the [wx,wx+1]x[wz,wz+1] footprint in the (dx,dz) direction.
        int x0 = dx > 0 ? wx + 1 : wx;
        int x1 = dx < 0 ? wx : wx + 1;
        int z0 = dz > 0 ? wz + 1 : wz;
        int z1 = dz < 0 ? wz : wz + 1;
        if (dx != 0) {
            // East/West face: spans Z, vertical from bottom..t at fixed X.
            quad(vc, mat, cam, shade, r, g, bl, 255,
                    x0, t, z0,
                    x0, t, z0 == wz ? wz + 1 : wz,
                    x0, bottom, z0 == wz ? wz + 1 : wz,
                    x0, bottom, z0);
        } else {
            // North/South face: spans X, vertical from bottom..t at fixed Z.
            quad(vc, mat, cam, shade, r, g, bl, 255,
                    x0, t, z0,
                    x0 == wx ? wx + 1 : wx, t, z0,
                    x0 == wx ? wx + 1 : wx, bottom, z0,
                    x0, bottom, z0);
        }
    }

    private static int surfaceAt(ZonePlannerMapDataClient data, Level level, int wx, int wz) {
        ZonePlannerMapChunk chunk = data.getChunk(level, wx >> 4, wz >> 4);
        if (chunk == null) {
            return ZonePlannerMapChunk.NO_DATA;
        }
        return chunk.getSurfaceY(wx & 15, wz & 15);
    }

    // ── Zone overlays + hover ─────────────────────────────────────────────────────────────

    private void emitOverlays(VertexConsumer vc, Matrix4f mat, ZoneMapPipRenderState state, Level level) {
        ZoneMapCamera cam = state.camera();
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;
        BlockPos tile = state.tilePos();
        ZonePlan[] layers = state.layers();

        for (int i = 0; i < layers.length; i++) {
            ZonePlan layer = (state.bufferColorIndex() == i && state.bufferLayer() != null)
                    ? state.bufferLayer()
                    : layers[i];
            if (layer == null) {
                continue;
            }
            int rgb = DyeColor.byId(i).getTextureDiffuseColor();
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float bl = (rgb & 0xFF) / 255f;
            for (int[] cell : layer.getAll()) {
                int wx = tile.getX() + cell[0];
                int wz = tile.getZ() + cell[1];
                int surf = surfaceAt(data, level, wx, wz);
                if (surf == ZonePlannerMapChunk.NO_DATA) {
                    continue;
                }
                overlayQuad(vc, mat, cam, r, g, bl, ZONE_ALPHA, wx, wz, surf);
            }
        }

        BlockPos hover = state.hoverPos();
        if (hover != null) {
            overlayQuad(vc, mat, cam, 1f, 1f, 1f, HOVER_ALPHA, hover.getX(), hover.getZ(), hover.getY());
        }
    }

    /** A flat translucent tile sitting just above the surface of one column. */
    private void overlayQuad(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                             float r, float g, float bl, int a, int wx, int wz, int surf) {
        float y = surf + 1 + OVERLAY_LIFT;
        quad(vc, mat, cam, 1f, r, g, bl, a,
                wx, y, wz,
                wx + 1, y, wz,
                wx + 1, y, wz + 1,
                wx, y, wz + 1);
    }

    // ── Vertex emission ───────────────────────────────────────────────────────────────────

    /** Emits one quad in both windings (cull-immune) with per-face shaded vertex colour. */
    private void quad(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam, float shade,
                      float r, float g, float bl, int a,
                      double x0, double y0, double z0, double x1, double y1, double z1,
                      double x2, double y2, double z2, double x3, double y3, double z3) {
        int ri = clamp255(r * shade);
        int gi = clamp255(g * shade);
        int bi = clamp255(bl * shade);
        putVertex(vc, mat, cam, x0, y0, z0, ri, gi, bi, a);
        putVertex(vc, mat, cam, x1, y1, z1, ri, gi, bi, a);
        putVertex(vc, mat, cam, x2, y2, z2, ri, gi, bi, a);
        putVertex(vc, mat, cam, x3, y3, z3, ri, gi, bi, a);
        // Reverse winding so back-face culling can't drop the face whichever way the pose determinant lands.
        putVertex(vc, mat, cam, x3, y3, z3, ri, gi, bi, a);
        putVertex(vc, mat, cam, x2, y2, z2, ri, gi, bi, a);
        putVertex(vc, mat, cam, x1, y1, z1, ri, gi, bi, a);
        putVertex(vc, mat, cam, x0, y0, z0, ri, gi, bi, a);
    }

    private void putVertex(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                           double wx, double wy, double wz, int r, int g, int b, int a) {
        float ex = (float) cam.canvasX(wx, wz);
        float ey = (float) (-cam.canvasY(wx, wy, wz)); // canvas Y is up-positive; texture Y is down-positive
        float ez = (float) cam.depth(wx, wy, wz);
        vc.addVertex(mat, ex, ey, ez).setColor(r, g, b, a);
    }

    private static int clamp255(float v) {
        int i = (int) (v * 255f + 0.5f);
        return i < 0 ? 0 : Math.min(i, 255);
    }
}
//?}
