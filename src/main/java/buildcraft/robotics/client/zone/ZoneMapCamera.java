/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

/**
 * The Zone Planner viewport's camera and projection. Pure (no Minecraft client types) so it can be
 * unit-tested and shared between the renderer (which draws terrain through it) and the GUI (which
 * inverts it to turn a mouse position into a world column for hover/paint).
 *
 * <p><b>Projection: top-down perspective</b>, matching BuildCraft 8.x's Zone Planner (camera straight
 * overhead looking down, {@code gluPerspective} 70&deg; FOV — a single nadir rotation, <em>not</em>
 * isometric; 1.7.10 had no 3D view at all). Modelled as a pinhole camera floating {@link #camHeight()}
 * blocks above the reference plane {@link #camY}: a block at height {@code wy} is magnified by
 * {@code camHeight / (camHeight - (wy - camY))}, so taller terrain swells outward from the centre and
 * reveals its sides — the classic overhead-with-depth look. The whole projection is done in vertex math
 * (output straight into the PiP's orthographic canvas), so no GPU projection override is needed and the
 * GUI can invert it exactly.
 *
 * <p>Canvas units are <em>world blocks at the ground plane</em>; the PiP render scale converts them to
 * pixels, so an on-screen pixel offset from the viewport centre is {@code canvas * pxPerBlock} at ground
 * level (independent of GUI scale — the offscreen texture and its blit both carry the GUI-scale factor,
 * which cancels). World +X maps east/right, world +Z maps south/down (north up), like a vanilla map.
 *
 * <p><b>Picking</b> comes in two forms. {@link #pickGround} intersects the view ray with the ground plane at
 * {@link #camY} (where the magnification is exactly 1, so the inverse is a plain linear map). {@link #pickTerrain}
 * marches the ray through the height field to the column that is actually drawn under the cursor, and is what the
 * hover box and painting use. Both it and the renderer leave out the columns within {@link #NEAR_CLIP} of the
 * camera, and read terrain at the height it is drawn at, so the cursor and the picture agree.
 */
public class ZoneMapCamera {
    /** Blocks the camera floats above {@link #camY} at {@link #DEFAULT_PX_PER_BLOCK} and closer zooms.
     *  Smaller = stronger perspective (more block sides visible off-centre); larger = flatter / more
     *  nearly orthographic. Zooming out past the default raises the camera instead (see
     *  {@link #camHeight()}). */
    public static final double CAM_HEIGHT = 96.0;
    /** Floor on the camera-to-point distance so terrain at or above the camera can't blow up the
     *  magnification (divide-by-near-zero). Nothing the renderer draws gets this close: see {@link #NEAR_CLIP}. */
    private static final double MIN_DIST = 8.0;
    /** A column whose top face is this close to the camera (or closer) is neither drawn nor pickable. It is a
     *  block more than {@link #MIN_DIST}, so the clamp on the magnification never engages for anything drawn (a
     *  clamped magnification would draw the column where the true-geometry pick ray does not find it) and the
     *  few-hundredths lift of the overlay quads above a surface stays clear of it too. */
    public static final double NEAR_CLIP = MIN_DIST + 1.0;
    /** Hard ceiling on the terrain-pick raymarch, so a pathological zoom or build floor can't stall a frame. */
    private static final int PICK_MAX_STEPS = 32768;
    /** Depth-ordering weight for height: negative so taller terrain (nearer the overhead camera) sorts
     *  in front. Kept small so {@code depth * poseScale} stays within the PiP ortho clip. Sign verified
     *  in-client. */
    public static final double DEPTH_PER_HEIGHT = -0.03;

    /** Survey-range zoom floor: at 0.125 px/block (8 blocks per pixel, 1.7.10's planner limit) the
     *  213-px viewport spans about 1,700 blocks. Below 1 px/block the renderer LOD-samples the world
     *  (see {@code ZoneMapGeometry}), so the low floor costs bounded vertices, not columns. */
    public static final double MIN_PX_PER_BLOCK = 0.125;
    /** Zoom crossover between the per-column near path and the per-pixel-strided far path. */
    public static final double FAR_MODE_PX_PER_BLOCK = 1.0;
    public static final double MAX_PX_PER_BLOCK = 16.0;
    public static final double DEFAULT_PX_PER_BLOCK = 4.0;

    /** World block coordinates at the centre of the viewport (the pan target). */
    public double camX;
    public double camZ;
    /** Reference ground height — the plane picking intersects and perspective is measured from. */
    public double camY;
    /** Zoom, in screen pixels per world block <em>at the ground plane</em>. */
    public double pxPerBlock = DEFAULT_PX_PER_BLOCK;

    public ZoneMapCamera(double camX, double camY, double camZ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    // ── Projection (world → canvas, canvas units = ground blocks) ────────────────────────

    /**
     * The camera's height above {@link #camY} at the current zoom. Fixed at {@link #CAM_HEIGHT} from the
     * default zoom inward; zooming out raises it in proportion to the visible span, so the field of view
     * stays constant instead of widening. A fixed-height camera reached a ~165&deg; FOV at the survey
     * floor, flinging off-centre trees hundreds of blocks outward into radial streaks.
     */
    public double camHeight() {
        return CAM_HEIGHT * Math.max(1.0, DEFAULT_PX_PER_BLOCK / pxPerBlock);
    }

    /** Perspective magnification for a point at world height {@code worldY}: 1 at ground, &gt;1 above. */
    private double magnification(double worldY) {
        double h = camHeight();
        double dist = h - (worldY - camY);
        if (dist < MIN_DIST) {
            dist = MIN_DIST;
        }
        return h / dist;
    }

    public double canvasX(double worldX, double worldY, double worldZ) {
        return (worldX - camX) * magnification(worldY);
    }

    /** Canvas Y, <b>down-positive</b> (texture convention): world +Z (south) maps down, north up. */
    public double canvasY(double worldX, double worldY, double worldZ) {
        return (worldZ - camZ) * magnification(worldY);
    }

    /** Depth ordering value (canvas units); smaller draws in front after the render scale is applied. */
    public double depth(double worldX, double worldY, double worldZ) {
        return (worldY - camY) * DEPTH_PER_HEIGHT;
    }

    /**
     * True for a column whose top face (surface Y + 1) comes within {@link #NEAR_CLIP} blocks of the camera's own
     * height ({@code camY +} {@link #camHeight()}), or rises past it. Such a column cannot be seen faithfully from
     * the overhead camera: the projection's magnification blows up (it is clamped at {@link #MIN_DIST}, which would
     * draw the column somewhere the true-geometry picking ray does not find it) and the ray starts inside the
     * ones that reach the camera. The renderer skips these columns ({@code ZoneMapGeometry}) and
     * {@link #pickTerrain} steps over them, so what is drawn and what the cursor can land on stay the same set,
     * and every column that is drawn is drawn where the ray finds it.
     */
    public boolean isNearClipped(int surfaceY) {
        return surfaceY + 1 + NEAR_CLIP >= camY + camHeight();
    }

    // ── Picking (pixel offset from viewport centre → world ground column) ────────────────

    /** Fills {@code out[0]=worldX, out[1]=worldZ} for the ground-plane hit under a viewport-centre-relative
     *  pixel offset ({@code dx} right-positive, {@code dy} down-positive). Exact inverse of the projection
     *  at {@code worldY == camY} (magnification 1). */
    public void pickGround(double dxPixels, double dyPixels, double[] out) {
        out[0] = camX + dxPixels / pxPerBlock;
        out[1] = camZ + dyPixels / pxPerBlock;
    }

    /** Supplies the surface Y of a world column for terrain picking, or {@link Integer#MIN_VALUE} if the
     *  column has no data (unloaded / off-map). */
    @FunctionalInterface
    public interface SurfaceQuery {
        int surfaceAt(int worldX, int worldZ);
    }

    /**
     * Picks the actual terrain column under a viewport-centre-relative pixel offset by marching the view
     * ray through the height field — the perspective-correct inverse the ground-plane {@link #pickGround}
     * can't give (under perspective, a column drawn at its surface height projects to a different pixel
     * than its ground-plane footprint, so a flat-plane pick drifts from the cursor off-centre). Mirrors
     * BC8's {@code rayTrace}. Returns {@code [worldX, surfaceY, worldZ]} of the first column the ray dips
     * into, or {@code null} if it hits nothing. Columns near the camera's height ({@link #isNearClipped}) are
     * skipped, matching what the renderer leaves undrawn. The search runs down to {@code minBuildY}, the build
     * floor, because that is as deep as the renderer draws ({@link #visibleChunkWindow}): terrain far below the
     * planner is drawn, so it must be pickable.
     *
     * <p>The height it tests the ray against is the one the renderer draws: below 1 px/block the terrain is drawn
     * one cell per {@link #stride()} blocks, at the surface of the cell's lowest-corner sample, so that sample's
     * surface is what {@code query} is asked for (the exact column hit is still returned, so painting stays
     * block-precise).
     */
    public int[] pickTerrain(double dxPixels, double dyPixels, SurfaceQuery query, int minBuildY) {
        // The cursor ray runs from the camera (camX, camY+camHeight, camZ) through the ground-plane point
        // it would hit at magnification 1, i.e. (camX + dx/px, camY, camZ + dy/px).
        double gx = dxPixels / pxPerBlock;
        double gz = dyPixels / pxPerBlock;
        double h = camHeight();
        double dirX = gx, dirY = -h, dirZ = gz;
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        // The hit lands at or before the ground plane when the terrain is level with or above the planner, but
        // the search also dips down to the build floor, so the budget scales with the ray's length to that
        // floor: len is its length to the plane, and the floor is (camHeight + camY - floor) / camHeight times
        // as far down. At survey zoom the ground point is hundreds of blocks out, far beyond a fixed step count.
        // One ray per frame, and one step per block of its length, so the cost is bounded by this one loop
        // (and, for a pathologically deep floor, by PICK_MAX_STEPS). A floor above the plane (never in a real
        // world) still searches down to the plane.
        double floorY = Math.min(minBuildY, camY);
        long budget = (long) Math.ceil(len * (h + camY - floorY) / h) + 8;
        budget = Math.min(budget, PICK_MAX_STEPS);

        double x = camX, y = camY + h, z = camZ;
        int sampleMask = -stride(); // clears the low bits: the sample grid is world-aligned, a power of two apart
        for (int i = 0; i < budget && y > floorY; i++) {
            x += dirX;
            y += dirY;
            z += dirZ;
            int wx = (int) Math.floor(x);
            int wz = (int) Math.floor(z);
            int sy = query.surfaceAt(wx & sampleMask, wz & sampleMask);
            // A column near the camera's own height is not drawn (see isNearClipped), and the ray starts inside
            // the ones that reach it: step over it, so the cursor lands on terrain that is actually in view.
            if (sy != Integer.MIN_VALUE && isNearClipped(sy)) {
                continue;
            }
            // The column's top face sits at sy + 1; the ray has hit once it descends to/through it.
            if (sy != Integer.MIN_VALUE && y <= sy + 1) {
                return new int[]{wx, sy, wz};
            }
        }
        return null;
    }

    /** World blocks per terrain sample at this zoom: 1 at 1 px/block and closer, then the power of two
     *  (2, 4, 8; the survey floor is 0.125 px/block) that keeps a sample at least a pixel wide. Powers of two
     *  keep the sample grid world-aligned across chunk borders &mdash; chunk origins are multiples of 16 and
     *  16 % stride == 0, so adjacent chunks never double-cover or gap. */
    public int stride() {
        int stride = 1;
        if (pxPerBlock < FAR_MODE_PX_PER_BLOCK) {
            int need = (int) Math.ceil(1.0 / pxPerBlock);
            while (stride < need && stride < 8) {
                stride <<= 1;
            }
        }
        return stride;
    }

    // ── Zoom / pan helpers ──────────────────────────────────────────────────────────────

    public void zoomBy(double factor) {
        pxPerBlock = clampZoom(pxPerBlock * factor);
    }

    public static double clampZoom(double v) {
        return Math.max(MIN_PX_PER_BLOCK, Math.min(MAX_PX_PER_BLOCK, v));
    }

    /** Pan by a screen-pixel drag delta so the ground point under the cursor follows it. */
    public void panByPixels(double dxPixels, double dyPixels) {
        camX -= dxPixels / pxPerBlock;
        camZ -= dyPixels / pxPerBlock;
    }

    /** Extra world-block padding around the visible box (per-column zoom) so a column's skirt or an edge cell isn't clipped. */
    private static final int RELIEF_PAD = 64;
    /** The same for the survey zoom, where the grid is coarse and the viewport spans many chunks. */
    private static final int FAR_PAD = 16;
    /** Most chunks the terrain scan covers along one axis: a safety cap for a world whose build range is absurdly
     *  deep (the window then clips around the camera, not off one side). 512 chunks is 8,192 blocks.
     *
     *  <p>This bounds the terrain scan's walk of the window each frame; it no longer bounds the zone overlay's memory,
     *  which follows the painted chunks ({@code ZoneOverlayCache} keeps no entry for an empty chunk). Since the camera
     *  rises as it zooms out ({@link #camHeight()}), the build floor barely de-magnifies at the survey zoom: a vanilla
     *  overworld (floor at -64) needs about 115 chunks for a planner at y=64 and about 125 for one at the top of the
     *  world, so only a modded world with a build range thousands of blocks deep reaches the cap. */
    public static final int MAX_CHUNK_SPAN = 512;

    /**
     * Conservative world-XZ bounding box of everything that can project into a viewport of the given pixel
     * size, for terrain no lower than {@code minBuildY}. Terrain below the reference plane is de-magnified: a
     * column {@code d} blocks from the camera axis projects only {@code d / factor} out, with
     * {@code factor = (camHeight + camY - y) / camHeight}. So the lowest terrain a world can hold, its build
     * floor, is what reaches farthest in from beyond the ground-level span, and that factor scales the ground
     * half-span before padding. Terrain above the plane is magnified and projects out sooner, so it is always
     * inside this box too. Returns {@code [minX, minZ, maxX, maxZ]} in world blocks.
     */
    public double[] visibleWorldBounds(int viewportWidthPx, int viewportHeightPx, int pad, int minBuildY) {
        double h = camHeight();
        double overscan = Math.max(1.0, (h + camY - minBuildY) / h); // the build floor's de-magnification
        double halfW = (viewportWidthPx / 2.0) / pxPerBlock * overscan + pad;
        double halfH = (viewportHeightPx / 2.0) / pxPerBlock * overscan + pad;
        return new double[]{camX - halfW, camZ - halfH, camX + halfW, camZ + halfH};
    }

    /**
     * The world chunks the terrain scan covers for a viewport of the given pixel size, as
     * {@code {minChunkX, minChunkZ, maxChunkX, maxChunkZ}} (all inclusive): every chunk with terrain down to
     * {@code minBuildY} that can project into the viewport ({@link #visibleWorldBounds}), at the survey zoom as at
     * the per-column zoom. Clipped to {@link #MAX_CHUNK_SPAN} chunks along an axis, around the camera. The zone
     * overlays are drawn on that same terrain, so they use the same window.
     */
    public int[] visibleChunkWindow(int viewportWidthPx, int viewportHeightPx, int minBuildY) {
        int pad = pxPerBlock < FAR_MODE_PX_PER_BLOCK ? FAR_PAD : RELIEF_PAD;
        double[] b = visibleWorldBounds(viewportWidthPx, viewportHeightPx, pad, minBuildY);
        int[] x = clipAround((int) Math.floor(b[0]) >> 4, (int) Math.floor(b[2]) >> 4, (int) Math.floor(camX) >> 4);
        int[] z = clipAround((int) Math.floor(b[1]) >> 4, (int) Math.floor(b[3]) >> 4, (int) Math.floor(camZ) >> 4);
        return new int[] {x[0], z[0], x[1], z[1]};
    }

    /** {@code [lo, hi]}, limited to {@link #MAX_CHUNK_SPAN} values centred (as far as it allows) on {@code centre}. */
    private static int[] clipAround(int lo, int hi, int centre) {
        if (hi - lo + 1 <= MAX_CHUNK_SPAN) {
            return new int[] {lo, hi};
        }
        int from = Math.max(lo, Math.min(centre - MAX_CHUNK_SPAN / 2, hi - MAX_CHUNK_SPAN + 1));
        return new int[] {from, from + MAX_CHUNK_SPAN - 1};
    }
}
