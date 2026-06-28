/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

/**
 * The Zone Planner viewport's camera and isometric-projection math. Pure (no Minecraft client types) so
 * it can be unit-tested and shared between the renderer (which draws terrain through it) and the GUI
 * (which inverts it to turn a mouse position into a world column for hover/paint).
 *
 * <p><b>Projection.</b> The viewport is an oblique <em>isometric</em> view, not a perspective camera.
 * World X/Z are rotated about the camera centre by {@link #YAW_DEG} (so both axes show relief, giving the
 * classic 3D map look), the rotated Z is foreshortened into canvas-Y by {@link #Z_FACTOR}, and block
 * height lifts a column up the canvas by {@link #H_FACTOR}. Canvas units are <em>world blocks</em>; the
 * PiP render scale converts them to pixels, so on-screen pixel offsets from the viewport centre are
 * simply {@code canvas * pxPerBlock} (independent of GUI scale — the offscreen texture and its blit both
 * carry the GUI-scale factor, which cancels).
 *
 * <p><b>Picking</b> intersects the view ray with the ground plane at {@link #camY}: a closed-form 2&times;2
 * inverse of the yaw rotation, exact and the natural behaviour for painting flat map regions (you point
 * at a ground position, not the face of whatever tall block occludes it). The hover box is then drawn at
 * that column's real surface height, so it still hugs the terrain.
 */
public class ZoneMapCamera {
    /** Yaw of the isometric view, degrees. ~30° gives a classic 2:1-ish iso without extreme foreshortening. */
    public static final double YAW_DEG = 30.0;
    /** Rotated-Z foreshortening into canvas-Y. 1.0 = no tilt; lower flattens the view toward top-down. */
    public static final double Z_FACTOR = 0.80;
    /** Block-height relief into canvas-Y: how far one block of elevation lifts a column up the canvas. */
    public static final double H_FACTOR = 0.55;
    /** Depth-ordering weight for height — sign tuned so taller terrain sorts in front (occludes). */
    public static final double DEPTH_PER_HEIGHT = -0.03;
    /** Depth-ordering tiebreak along the rotated Z so equal-height columns sort front-to-back. */
    public static final double DEPTH_PER_Z = 0.006;

    public static final double MIN_PX_PER_BLOCK = 1.5;
    public static final double MAX_PX_PER_BLOCK = 16.0;
    public static final double DEFAULT_PX_PER_BLOCK = 4.0;

    private static final double COS_YAW = Math.cos(Math.toRadians(YAW_DEG));
    private static final double SIN_YAW = Math.sin(Math.toRadians(YAW_DEG));

    /** World block coordinates at the centre of the viewport (the pan target). */
    public double camX;
    public double camZ;
    /** Reference ground height — the plane picking intersects and height relief is measured from. */
    public double camY;
    /** Zoom, in screen pixels per world block. */
    public double pxPerBlock = DEFAULT_PX_PER_BLOCK;

    public ZoneMapCamera(double camX, double camY, double camZ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    // ── Projection (world → canvas, canvas units = blocks) ──────────────────────────────

    /** Yaw-rotated X of a world column relative to the camera centre. */
    private double rotX(double worldX, double worldZ) {
        return (worldX - camX) * COS_YAW - (worldZ - camZ) * SIN_YAW;
    }

    /** Yaw-rotated Z (the into-screen axis) of a world column relative to the camera centre. */
    private double rotZ(double worldX, double worldZ) {
        return (worldX - camX) * SIN_YAW + (worldZ - camZ) * COS_YAW;
    }

    public double canvasX(double worldX, double worldZ) {
        return rotX(worldX, worldZ);
    }

    /** Canvas Y, <b>up-positive</b>: terrain that is farther into the scene or taller sits higher. The
     *  renderer flips this to the texture's down-positive Y; picking consumes the same up-positive value. */
    public double canvasY(double worldX, double worldY, double worldZ) {
        return rotZ(worldX, worldZ) * Z_FACTOR + (worldY - camY) * H_FACTOR;
    }

    /** Depth ordering value (canvas units); smaller draws in front after the render scale is applied. */
    public double depth(double worldX, double worldY, double worldZ) {
        return rotZ(worldX, worldZ) * DEPTH_PER_Z + (worldY - camY) * DEPTH_PER_HEIGHT;
    }

    // ── Picking (pixel offset from viewport centre → world ground column) ────────────────

    /** Fills {@code out[0]=worldX, out[1]=worldZ} for the ground-plane hit under a viewport-centre-relative
     *  pixel offset ({@code dx} right-positive, {@code dy} down-positive). Exact inverse of the projection
     *  at {@code worldY == camY}. */
    public void pickGround(double dxPixels, double dyPixels, double[] out) {
        double rx = dxPixels / pxPerBlock;                 // canvasX in blocks
        double rz = (-dyPixels / pxPerBlock) / Z_FACTOR;   // canvasY (up+) in blocks → rotated-Z at ground
        // Inverse yaw rotation (rotate by -YAW).
        out[0] = camX + rx * COS_YAW + rz * SIN_YAW;
        out[1] = camZ - rx * SIN_YAW + rz * COS_YAW;
    }

    // ── Zoom / pan helpers ──────────────────────────────────────────────────────────────

    public void zoomBy(double factor) {
        pxPerBlock = clampZoom(pxPerBlock * factor);
    }

    public static double clampZoom(double v) {
        return Math.max(MIN_PX_PER_BLOCK, Math.min(MAX_PX_PER_BLOCK, v));
    }

    /** Pan by a screen-pixel drag delta so the world under the cursor follows it. */
    public void panByPixels(double dxPixels, double dyPixels) {
        double rx = dxPixels / pxPerBlock;
        double rz = (-dyPixels / pxPerBlock) / Z_FACTOR;
        camX -= rx * COS_YAW + rz * SIN_YAW;
        camZ -= -rx * SIN_YAW + rz * COS_YAW;
    }

    /**
     * Conservative world-XZ bounding box of everything that can project into a viewport of the given
     * pixel size, padded for height relief (a tall column at the top edge originates from farther into
     * the scene). Returns {@code [minX, minZ, maxX, maxZ]} in world blocks.
     */
    public double[] visibleWorldBounds(int viewportWidthPx, int viewportHeightPx, int reliefPadBlocks) {
        double halfW = viewportWidthPx / 2.0;
        double halfH = viewportHeightPx / 2.0;
        double[] p = new double[2];
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        // Project the four viewport corners back to the ground plane.
        double[][] corners = {{-halfW, -halfH}, {halfW, -halfH}, {-halfW, halfH}, {halfW, halfH}};
        for (double[] c : corners) {
            pickGround(c[0], c[1], p);
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]);
        }
        return new double[]{minX - reliefPadBlocks, minZ - reliefPadBlocks,
                            maxX + reliefPadBlocks, maxZ + reliefPadBlocks};
    }
}
