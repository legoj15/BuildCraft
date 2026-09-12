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
 * isometric; 1.7.10 had no 3D view at all). Modelled as a pinhole camera floating {@link #CAM_HEIGHT}
 * blocks above the reference plane {@link #camY}: a block at height {@code wy} is magnified by
 * {@code CAM_HEIGHT / (CAM_HEIGHT - (wy - camY))}, so taller terrain swells outward from the centre and
 * reveals its sides — the classic overhead-with-depth look. The whole projection is done in vertex math
 * (output straight into the PiP's orthographic canvas), so no GPU projection override is needed and the
 * GUI can invert it exactly.
 *
 * <p>Canvas units are <em>world blocks at the ground plane</em>; the PiP render scale converts them to
 * pixels, so an on-screen pixel offset from the viewport centre is {@code canvas * pxPerBlock} at ground
 * level (independent of GUI scale — the offscreen texture and its blit both carry the GUI-scale factor,
 * which cancels). World +X maps east/right, world +Z maps south/down (north up), like a vanilla map.
 *
 * <p><b>Picking</b> intersects the view ray with the ground plane at {@link #camY} (where the
 * magnification is exactly 1, so the inverse is a plain linear map) — the natural behaviour for painting
 * flat map regions. The hover box is then drawn at that column's real surface height.
 */
public class ZoneMapCamera {
    /** Blocks the camera floats above {@link #camY}. Smaller = stronger perspective (more block sides
     *  visible off-centre); larger = flatter / more nearly orthographic. */
    public static final double CAM_HEIGHT = 96.0;
    /** Floor on the camera-to-point distance so terrain at or above the camera can't blow up the
     *  magnification (divide-by-near-zero). */
    private static final double MIN_DIST = 8.0;
    /** How far below the reference plane terrain picking keeps searching. Also scales the raymarch
     *  budget: a ray that reaches the plane at length {@code len} can still dip into terrain up to
     *  this far below it, so the step budget is {@code len * (CAM_HEIGHT + this) / CAM_HEIGHT}. */
    private static final int PICK_SEARCH_DOWN = 80;
    /** Hard ceiling on the terrain-pick raymarch, so a pathological zoom can't stall a frame. */
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

    /** Perspective magnification for a point at world height {@code worldY}: 1 at ground, &gt;1 above. */
    private double magnification(double worldY) {
        double dist = CAM_HEIGHT - (worldY - camY);
        if (dist < MIN_DIST) {
            dist = MIN_DIST;
        }
        return CAM_HEIGHT / dist;
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
     * into, or {@code null} if it hits nothing.
     */
    public int[] pickTerrain(double dxPixels, double dyPixels, SurfaceQuery query) {
        // The cursor ray runs from the camera (camX, camY+CAM_HEIGHT, camZ) through the ground-plane point
        // it would hit at magnification 1, i.e. (camX + dx/px, camY, camZ + dy/px).
        double gx = dxPixels / pxPerBlock;
        double gz = dyPixels / pxPerBlock;
        double dirX = gx, dirY = -CAM_HEIGHT, dirZ = gz;
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        // The hit lands at or before the ground plane, but the search also dips up to PICK_SEARCH_DOWN
        // below it, so the budget scales with the ray's ground length — at survey zoom that ground point
        // is hundreds of blocks out, far beyond a fixed step count. Single ray per frame, so the cost is
        // bounded by this one loop.
        long budget = (long) Math.ceil(len * (CAM_HEIGHT + PICK_SEARCH_DOWN) / CAM_HEIGHT) + 8;
        budget = Math.min(budget, PICK_MAX_STEPS);

        double x = camX, y = camY + CAM_HEIGHT, z = camZ;
        double floorY = camY - PICK_SEARCH_DOWN;
        for (int i = 0; i < budget && y > floorY; i++) {
            x += dirX;
            y += dirY;
            z += dirZ;
            int wx = (int) Math.floor(x);
            int wz = (int) Math.floor(z);
            int sy = query.surfaceAt(wx, wz);
            // The column's top face sits at sy + 1; the ray has hit once it descends to/through it.
            if (sy != Integer.MIN_VALUE && y <= sy + 1) {
                return new int[]{wx, sy, wz};
            }
        }
        return null;
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

    /**
     * Conservative world-XZ bounding box of everything that can project into a viewport of the given
     * pixel size. Low terrain (magnification &lt;1) can enter from beyond the ground-level span, so the
     * ground half-span is over-scanned before padding. Returns {@code [minX, minZ, maxX, maxZ]} in world
     * blocks.
     */
    public double[] visibleWorldBounds(int viewportWidthPx, int viewportHeightPx, int pad) {
        double overscan = CAM_HEIGHT / Math.max(MIN_DIST, CAM_HEIGHT - 48); // worst-case de-magnification
        double halfW = (viewportWidthPx / 2.0) / pxPerBlock * overscan + pad;
        double halfH = (viewportHeightPx / 2.0) / pxPerBlock * overscan + pad;
        return new double[]{camX - halfW, camZ - halfH, camX + halfW, camZ + halfH};
    }

    /**
     * Exact ground-plane world-XZ bounds of a viewport — everything {@link #pickGround} can reach, with
     * no perspective overscan. The far-zoom LOD path samples this (not {@link #visibleWorldBounds}) so
     * what it renders is precisely what picking and painting address, and the sample count stays tied to
     * the viewport pixels rather than the perspective frustum.
     */
    public double[] visibleGroundBounds(int viewportWidthPx, int viewportHeightPx, int pad) {
        double halfW = (viewportWidthPx / 2.0) / pxPerBlock + pad;
        double halfH = (viewportHeightPx / 2.0) / pxPerBlock + pad;
        return new double[]{camX - halfW, camZ - halfH, camX + halfW, camZ + halfH};
    }
}
