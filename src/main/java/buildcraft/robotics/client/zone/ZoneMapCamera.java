/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
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
    /** Depth-ordering weight for height: negative so taller terrain (nearer the overhead camera) sorts
     *  in front. Kept small so {@code depth * poseScale} stays within the PiP ortho clip. Sign verified
     *  in-client. */
    public static final double DEPTH_PER_HEIGHT = -0.03;

    public static final double MIN_PX_PER_BLOCK = 1.5;
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
}
