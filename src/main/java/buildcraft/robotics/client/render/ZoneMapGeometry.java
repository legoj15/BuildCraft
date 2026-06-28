/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.client.zone.ZonePlannerMapChunk;
import buildcraft.robotics.client.zone.ZonePlannerMapDataClient;
import buildcraft.robotics.zone.ZonePlan;

/**
 * The Zone Planner map's vertex geometry — shared, node-agnostic, and free of any GUI/PiP plumbing.
 * Both viewport backends feed it a {@link VertexConsumer} and the current pose {@link Matrix4f}:
 * {@link ZoneMapPipRenderer} (the &gt;=1.21.10 offscreen picture-in-picture path) and
 * {@code ZoneMapGuiRenderer} (the 1.21.1 direct-to-GUI path). Keeping the geometry here means both
 * paths draw the identical terrain/zones from one source.
 *
 * <p>Terrain is drawn as opaque, depth-writing colour cuboids (every face emitted in both windings so
 * back-face culling can't drop a skirt regardless of the cumulative pose determinant — safe because the
 * faces are opaque and coplanar). Zone tints and the hover box are translucent quads laid just above the
 * surface. Both use BuildCraft's POSITION_COLOR debug render types — no textures, no lighting — with
 * per-face shading baked into the vertex colour, the same trick the 1.12.2 renderer used. The caller
 * picks the render types ({@code BCLibRenderTypes.debugSolid()} for terrain,
 * {@code BCLibRenderTypes.debugFilled()} for overlays) and supplies the matching consumer.
 */
public final class ZoneMapGeometry {

    private ZoneMapGeometry() {}

    /** Per-face brightness, matching vanilla's block-face shading so relief reads naturally. */
    private static final float SHADE_TOP = 1.0f;
    private static final float SHADE_NS = 0.8f;
    private static final float SHADE_EW = 0.6f;

    /** Alpha (0-255) of a painted-zone tint laid over the terrain. */
    private static final int ZONE_ALPHA = 0x88;
    /** Lift the zone overlay quads this far above the surface so they don't z-fight the top face. */
    private static final float OVERLAY_LIFT = 0.05f;
    /** Depth-only bias for the hover highlight, in blocks "up" toward the overhead camera. Its screen
     *  position stays on the surface, but its depth is computed as if it were this much taller, so it
     *  sorts cleanly in front of the column top and normal relief instead of z-fighting them (which
     *  left only a thin edge sliver visible). Small enough to stay inside both the GUI and PiP depth
     *  ranges; large enough (≈8 blocks ≫ the sub-precision world-Y lift) to clear the z-fight. */
    private static final float HOVER_DEPTH_BIAS = 8.0f;
    /** Inset of the hover highlight's bright centre, leaving a dark contrast frame around it. */
    private static final float HOVER_BORDER = 0.16f;
    /** How far a map-edge column's skirt drops when it has no neighbour to step down to. */
    private static final int EDGE_SKIRT = 2;
    /** Safety cap on the chunk grid scanned per frame (low zoom shows a lot of world). */
    private static final int MAX_CHUNK_SPAN = 48;
    /** Extra world-block padding around the visible box so tall terrain entering from a screen edge isn't clipped. */
    private static final int RELIEF_PAD = 64;

    // ── Terrain ─────────────────────────────────────────────────────────────────────────

    /** Emits the visible terrain columns for a viewport of {@code wPx}x{@code hPx} pixels. */
    public static void emitTerrain(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                   int wPx, int hPx, Level level) {
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;

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

    private static void emitColumn(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam, Level level,
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
    private static void emitSkirt(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
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
        int z0 = dz > 0 ? wz + 1 : wz;
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

    /** Emits the painted-zone tints (one colour per dye layer, the in-progress paint preview swapped in
     *  for its layer) and the hover highlight, laid just above each column's surface. */
    public static void emitOverlays(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                    BlockPos tile, ZonePlan[] layers,
                                    ZonePlan bufferLayer, int bufferColorIndex,
                                    BlockPos hover, Level level) {
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;

        for (int i = 0; i < layers.length; i++) {
            ZonePlan layer = (bufferColorIndex == i && bufferLayer != null)
                    ? bufferLayer
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

        if (hover != null) {
            hoverHighlight(vc, mat, cam, hover.getX(), hover.getZ(), hover.getY());
        }
    }

    /** Bold, opaque highlight for the hovered column — a bright centre inside a dark frame, lifted above
     *  the zone tints. The dark frame reads on light terrain (sand), the bright centre on dark terrain
     *  (water/forest), so the cursor cell is unmistakable over any colour. Replaces the old faint 60%
     *  white tile that washed out over light terrain (and read only on a glancing edge). */
    private static void hoverHighlight(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                       int wx, int wz, int surf) {
        double posY = surf + 1 + OVERLAY_LIFT;            // screen position: just above the surface
        double dFrame = surf + 1 + HOVER_DEPTH_BIAS;      // depth: biased in front of the terrain top
        double dFill = dFrame + 1.0;                      // centre one step more in front than the frame
        // Dark frame: the full cell.
        hoverQuad(vc, mat, cam, 0, 0, 0, 255, wx, wz, wx + 1, wz + 1, posY, dFrame);
        // Bright centre, inset so the dark frame shows around it.
        double m = HOVER_BORDER;
        hoverQuad(vc, mat, cam, 255, 255, 255, 255, wx + m, wz + m, wx + 1 - m, wz + 1 - m, posY, dFill);
    }

    /** Flat, double-wound (cull-immune) quad for the hover highlight. Screen position uses {@code posY}
     *  (on the surface) but depth uses the raised {@code depthY}, so it draws in front of nearby terrain
     *  without floating out of place. */
    private static void hoverQuad(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam, int r, int g, int b, int a,
                                  double x0, double z0, double x1, double z1, double posY, double depthY) {
        hoverVertex(vc, mat, cam, x0, posY, z0, depthY, r, g, b, a);
        hoverVertex(vc, mat, cam, x1, posY, z0, depthY, r, g, b, a);
        hoverVertex(vc, mat, cam, x1, posY, z1, depthY, r, g, b, a);
        hoverVertex(vc, mat, cam, x0, posY, z1, depthY, r, g, b, a);
        hoverVertex(vc, mat, cam, x0, posY, z1, depthY, r, g, b, a);
        hoverVertex(vc, mat, cam, x1, posY, z1, depthY, r, g, b, a);
        hoverVertex(vc, mat, cam, x1, posY, z0, depthY, r, g, b, a);
        hoverVertex(vc, mat, cam, x0, posY, z0, depthY, r, g, b, a);
    }

    /** Vertex whose canvas X/Y come from {@code posY} but whose depth comes from {@code depthY}. */
    private static void hoverVertex(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                    double wx, double posY, double wz, double depthY, int r, int g, int b, int a) {
        float ex = (float) cam.canvasX(wx, posY, wz);
        float ey = (float) cam.canvasY(wx, posY, wz);
        float ez = (float) cam.depth(wx, depthY, wz);
        vc.addVertex(mat, ex, ey, ez).setColor(r, g, b, a);
    }

    /** A flat translucent tile sitting just above the surface of one column. */
    private static void overlayQuad(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
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
    private static void quad(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam, float shade,
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

    private static void putVertex(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                  double wx, double wy, double wz, int r, int g, int b, int a) {
        float ex = (float) cam.canvasX(wx, wy, wz);
        float ey = (float) cam.canvasY(wx, wy, wz); // canvasY is already down-positive (texture convention)
        float ez = (float) cam.depth(wx, wy, wz);
        vc.addVertex(mat, ex, ey, ez).setColor(r, g, b, a);
    }

    private static int clamp255(float v) {
        int i = (int) (v * 255f + 0.5f);
        return i < 0 ? 0 : Math.min(i, 255);
    }
}
