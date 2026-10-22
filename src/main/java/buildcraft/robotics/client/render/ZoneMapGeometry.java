/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

import buildcraft.robotics.RobotUtils;
import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.client.zone.ZoneOverlayCache;
import buildcraft.robotics.client.zone.ZoneSurveyBorder;
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
 * {@code BCLibRenderTypes.zoneOverlay()} for overlays) and supplies the matching consumer.
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
    /** The survey-square border: a light red that reads over grass, sand, water and every dye tint. */
    private static final float BORDER_R = 1.0f, BORDER_G = 0.35f, BORDER_B = 0.3f;
    private static final int BORDER_ALPHA = 0xD0;
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

    // ── Terrain ─────────────────────────────────────────────────────────────────────────

    /** Emits the visible terrain columns for a viewport of {@code wPx}x{@code hPx} pixels.
     *
     *  <p>Below 1 px/block ({@code farMode}) the world is LOD-sampled on a stride grid — one merged
     *  cuboid per {@code ceil(1/pxPerBlock)} world blocks, so the sample count tracks the viewport's
     *  pixel count instead of the world area, and sub-pixel blocks can't alias. At every zoom the scan
     *  covers the chunks that can project into the viewport ({@link ZoneMapCamera#visibleChunkWindow}), which
     *  reaches out past the ground-level span to take in terrain far below the planner: that is de-magnified,
     *  so it shows at the viewport's edge from beyond the ground span. Only chunks the client can hold are visited
     *  at all ({@link ZonePlannerMapDataClient#mappedChunkBounds}), so the walk follows the view distance, not the
     *  window.
     *
     *  <p>Columns whose top comes within {@link ZoneMapCamera#NEAR_CLIP} of the camera's own height
     *  ({@link ZoneMapCamera#isNearClipped}) are left out, here and in the overlays, exactly as picking steps over
     *  them: the overhead camera cannot see them, and none that is drawn is drawn at a clamped magnification. */
    public static void emitTerrain(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                   int wPx, int hPx, Level level) {
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;

        int stride = cam.stride();
        int[] window = visibleChunkWindow(cam, wPx, hPx, level);
        int minCX = window[0];
        int minCZ = window[1];
        int maxCX = window[2];
        int maxCZ = window[3];

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                ZonePlannerMapChunk chunk = data.getChunk(level, cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int lx = 0; lx < 16; lx += stride) {
                    for (int lz = 0; lz < 16; lz += stride) {
                        if (!chunk.hasData(lx, lz)) {
                            continue;
                        }
                        int wx = (cx << 4) + lx;
                        int wz = (cz << 4) + lz;
                        int top = chunk.getSurfaceY(lx, lz);
                        if (cam.isNearClipped(top)) {
                            continue; // near the camera: not drawn, and not pickable (ZoneMapCamera.pickTerrain)
                        }
                        int colour = chunk.getColour(lx, lz);
                        emitColumn(vc, mat, cam, level, data, wx, wz, top, colour, stride);
                    }
                }
            }
        }
    }

    /** The world chunks the terrain scan covers for a viewport of {@code wPx}x{@code hPx} pixels, as
     *  {@code {minChunkX, minChunkZ, maxChunkX, maxChunkZ}} (all inclusive): the chunks that can project into the
     *  viewport, cut down to the ones the map can have data for ({@link ZonePlannerMapDataClient#mappedChunkBounds}).
     *  At survey zoom of a deep world the first is hundreds of chunks across while a client holds a few dozen, and
     *  every chunk of the window costs a lookup each frame whether or not it is loaded. The zone overlays and the
     *  survey border are drawn on that same terrain, so they use the same window. */
    private static int[] visibleChunkWindow(ZoneMapCamera cam, int wPx, int hPx, Level level) {
        int[] window = cam.visibleChunkWindow(wPx, hPx, RobotUtils.minBuildY(level));
        return ZoneMapCamera.clipWindow(window, ZonePlannerMapDataClient.INSTANCE.mappedChunkBounds(level));
    }

    /** Emits one terrain cell: a stride&times;stride top face at the sampled surface, with skirt faces
     *  where the strided neighbour steps down (or the map ends). {@code stride == 1} is the near-mode
     *  per-column cell. */
    private static void emitColumn(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam, Level level,
                                   ZonePlannerMapDataClient data, int wx, int wz, int top, int colour,
                                   int stride) {
        float r = ((colour >> 16) & 0xFF) / 255f;
        float g = ((colour >> 8) & 0xFF) / 255f;
        float bl = (colour & 0xFF) / 255f;

        // Top face at the surface.
        quad(vc, mat, cam, SHADE_TOP, r, g, bl, 255,
                wx, top + 1, wz,
                wx + stride, top + 1, wz,
                wx + stride, top + 1, wz + stride,
                wx, top + 1, wz + stride);

        // Skirt faces only where a neighbour steps down (or the map ends), so the relief reads as clean
        // contour steps instead of full-height pillars. Neighbours are the adjacent strided samples.
        emitSkirt(vc, mat, cam, data, level, r, g, bl, SHADE_NS, wx, wz, top, /*dx*/ 0, /*dz*/ -1, stride);
        emitSkirt(vc, mat, cam, data, level, r, g, bl, SHADE_NS, wx, wz, top, 0, 1, stride);
        emitSkirt(vc, mat, cam, data, level, r, g, bl, SHADE_EW, wx, wz, top, -1, 0, stride);
        emitSkirt(vc, mat, cam, data, level, r, g, bl, SHADE_EW, wx, wz, top, 1, 0, stride);
    }

    /** Draws the vertical face on the (dx,dz) side of a strided cell, dropping from {@code top} to the
     *  adjacent sample's surface (or {@link #EDGE_SKIRT} below if the neighbour is missing). Skipped when
     *  the neighbour is at least as high (face hidden). */
    private static void emitSkirt(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                  ZonePlannerMapDataClient data, Level level,
                                  float r, float g, float bl, float shade,
                                  int wx, int wz, int top, int dx, int dz, int stride) {
        int neighbour = surfaceAt(data, level, wx + dx * stride, wz + dz * stride);
        int bottom;
        if (neighbour == ZonePlannerMapChunk.NO_DATA) {
            bottom = top - EDGE_SKIRT;
        } else if (neighbour >= top) {
            return; // hidden by the neighbour
        } else {
            bottom = neighbour;
        }
        int t = top + 1;
        if (dz != 0) {
            // North/South face: spans X [wx, wx+stride] at fixed Z.
            int z0 = dz > 0 ? wz + stride : wz;
            quad(vc, mat, cam, shade, r, g, bl, 255,
                    wx, t, z0,
                    wx + stride, t, z0,
                    wx + stride, bottom, z0,
                    wx, bottom, z0);
        } else {
            // East/West face: spans Z [wz, wz+stride] at fixed X.
            int x0 = dx > 0 ? wx + stride : wx;
            quad(vc, mat, cam, shade, r, g, bl, 255,
                    x0, t, wz,
                    x0, t, wz + stride,
                    x0, bottom, wz + stride,
                    x0, bottom, wz);
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

    /** The painted cells of each layer in view, worked out once and reused until a layer is edited or the view
     *  moves (see {@link ZoneOverlayCache}); every Zone Planner viewport draws from this one. */
    private static final ZoneOverlayCache OVERLAY_CELLS = new ZoneOverlayCache();

    /** Forgets the cached overlay cells: the planner screen opening, so nothing from another world lingers. */
    public static void clearOverlayCache() {
        OVERLAY_CELLS.clear();
    }

    /** Emits the painted-zone tints (one colour per dye layer, the in-progress paint preview swapped in
     *  for its layer), the survey-square border and the hover highlight, laid just above each column's
     *  surface. {@code wPx}/{@code hPx} is the viewport size, which bounds the overlays to what is visible.
     *
     *  <p>Only the painted cells inside the terrain's own chunk window are drawn ({@link ZoneOverlayCache}), and at
     *  survey zoom, where the terrain is sampled on a stride grid, one tinted square per stride cell rather than per
     *  block: a layer painted over the whole survey square costs the same to draw as a small one. */
    public static void emitOverlays(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                    int wPx, int hPx, BlockPos tile, ZonePlan[] layers,
                                    ZonePlan bufferLayer, int bufferColorIndex,
                                    BlockPos hover, Level level) {
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;
        emitSurveyBorder(vc, mat, cam, wPx, hPx, tile, data, level);

        int stride = cam.stride();
        int[] window = visibleChunkWindow(cam, wPx, hPx, level);
        for (int i = 0; i < layers.length; i++) {
            ZonePlan layer = (bufferColorIndex == i && bufferLayer != null)
                    ? bufferLayer
                    : layers[i];
            if (layer == null) {
                continue;
            }
            ZoneOverlayCache.Cells cells = OVERLAY_CELLS.cells(i, layer, tile.getX(), tile.getZ(),
                    window[0], window[1], window[2], window[3], stride);
            if (cells.size() == 0) {
                continue;
            }
            int rgb = DyeColor.byId(i).getTextureDiffuseColor();
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float bl = (rgb & 0xFF) / 255f;
            // The cache lists a world chunk's cells together at every stride (see ZoneOverlayCache), so remember the
            // last map chunk looked up: one lookup per run of cells, not per cell.
            int lastCX = Integer.MIN_VALUE;
            int lastCZ = Integer.MIN_VALUE;
            ZonePlannerMapChunk mapChunk = null;
            for (int c = 0; c < cells.size(); c++) {
                int wx = cells.x(c);
                int wz = cells.z(c);
                if ((wx >> 4) != lastCX || (wz >> 4) != lastCZ) {
                    lastCX = wx >> 4;
                    lastCZ = wz >> 4;
                    mapChunk = data.getChunk(level, lastCX, lastCZ);
                }
                if (mapChunk == null) {
                    continue;
                }
                int surf = mapChunk.getSurfaceY(wx & 15, wz & 15);
                if (surf == ZonePlannerMapChunk.NO_DATA || cam.isNearClipped(surf)) {
                    continue; // unmapped, or a column the terrain pass leaves out for being near the camera
                }
                overlayQuad(vc, mat, cam, r, g, bl, ZONE_ALPHA, wx, wz, surf, stride);
            }
        }

        if (hover != null) {
            hoverHighlight(vc, mat, cam, hover.getX(), hover.getZ(), hover.getY());
        }
    }

    /** Outlines the survey square: strokes are clipped to it ({@link ZonePlan#paintRect}), on the client and
     *  on the server alike. Drawn as a band one terrain cell wide along each edge, made of the very cells the
     *  terrain is sampled in (the world-aligned stride grid, {@link ZoneSurveyBorder}) and at each cell's own
     *  sample height, so the band lies on the terrain it outlines and not across two cells of it. Only the visible
     *  stretch of each edge is walked, and unmapped columns are skipped (with vanilla render distances the edge
     *  usually lies beyond the loaded terrain, which cannot be painted either). */
    private static void emitSurveyBorder(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam, int wPx, int hPx,
                                         BlockPos tile, ZonePlannerMapDataClient data, Level level) {
        int band = cam.stride();
        // The square in world blocks, max exclusive.
        int minX = tile.getX() + ZonePlan.SURVEY_MIN_BLOCK;
        int maxX = tile.getX() + ZonePlan.SURVEY_MAX_BLOCK + 1;
        int minZ = tile.getZ() + ZonePlan.SURVEY_MIN_BLOCK;
        int maxZ = tile.getZ() + ZonePlan.SURVEY_MAX_BLOCK + 1;
        int[] window = visibleChunkWindow(cam, wPx, hPx, level); // the chunks the terrain itself is scanned over
        ZoneSurveyBorder.forEachCell(minX, minZ, maxX, maxZ,
                window[0] << 4, window[1] << 4, (window[2] + 1) << 4, (window[3] + 1) << 4, band,
                (cellX, cellZ) -> borderCell(vc, mat, cam, data, level, cellX, cellZ, band));
    }

    /** One border cell, the {@code size}-block square with its lowest corner at {@code (x, z)}, at the surface of
     *  that corner's column: the sample the terrain cell under it is drawn at. */
    private static void borderCell(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                   ZonePlannerMapDataClient data, Level level, int x, int z, int size) {
        int surf = surfaceAt(data, level, x, z);
        if (surf == ZonePlannerMapChunk.NO_DATA || cam.isNearClipped(surf)) {
            return;
        }
        float y = surf + 1 + 2 * OVERLAY_LIFT; // above the zone tints, so the edge reads over a painted zone
        quad(vc, mat, cam, 1f, BORDER_R, BORDER_G, BORDER_B, BORDER_ALPHA,
                x, y, z,
                x + size, y, z,
                x + size, y, z + size,
                x, y, z + size);
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

    /** A flat translucent tile sitting just above the surface of one terrain sample: a column, or at survey zoom
     *  the {@code size}-block square the terrain samples as one. */
    private static void overlayQuad(VertexConsumer vc, Matrix4f mat, ZoneMapCamera cam,
                                    float r, float g, float bl, int a, int wx, int wz, int surf, int size) {
        float y = surf + 1 + OVERLAY_LIFT;
        quad(vc, mat, cam, 1f, r, g, bl, a,
                wx, y, wz,
                wx + size, y, wz,
                wx + size, y, wz + size,
                wx, y, wz + size);
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
