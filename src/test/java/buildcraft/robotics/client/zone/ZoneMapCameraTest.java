/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit characterization of {@link ZoneMapCamera}, the Zone Planner viewport's top-down perspective
 * projection. It pulls in no Minecraft types, so it exercises the exact math the renderer draws with and
 * the GUI picks with — the two must agree or the hover/paint cursor drifts off the terrain. Covers the
 * ground-plane projection&harr;pick round-trip (the load-bearing invariant), grab-and-drag panning, the
 * perspective magnification of taller terrain, and zoom clamping.
 */
public class ZoneMapCameraTest {

    private static final double EPS = 1e-6;

    /** Forward-project a ground point to a viewport-centre-relative pixel offset, the inverse of pickGround. */
    private static double[] projectGround(ZoneMapCamera cam, double wx, double wz) {
        double canvasX = cam.canvasX(wx, cam.camY, wz); // ground level: magnification is exactly 1
        double canvasY = cam.canvasY(wx, cam.camY, wz);
        return new double[]{canvasX * cam.pxPerBlock, canvasY * cam.pxPerBlock};
    }

    @Test
    void pickGroundInvertsProjection() {
        ZoneMapCamera cam = new ZoneMapCamera(100.5, 64, -40.5);
        cam.pxPerBlock = 6.0;
        double[] out = new double[2];
        for (int wx = 80; wx <= 120; wx += 7) {
            for (int wz = -60; wz <= -20; wz += 7) {
                double[] px = projectGround(cam, wx, wz);
                cam.pickGround(px[0], px[1], out);
                Assertions.assertEquals(wx, out[0], 1e-4, "worldX round-trip at " + wx + "," + wz);
                Assertions.assertEquals(wz, out[1], 1e-4, "worldZ round-trip at " + wx + "," + wz);
            }
        }
    }

    @Test
    void centrePixelMapsToCameraCentre() {
        ZoneMapCamera cam = new ZoneMapCamera(12.0, 70, 34.0);
        double[] out = new double[2];
        cam.pickGround(0, 0, out);
        Assertions.assertEquals(12.0, out[0], EPS);
        Assertions.assertEquals(34.0, out[1], EPS);
    }

    @Test
    void grabAndDragKeepsWorldPointUnderCursor() {
        // Press at one pixel, note the ground point there; after dragging by the delta, that same world
        // point must sit under the new cursor pixel.
        ZoneMapCamera cam = new ZoneMapCamera(0.0, 64, 0.0);
        cam.pxPerBlock = 4.0;
        double[] grabbed = new double[2];
        double p0x = -30, p0y = 18, p1x = 25, p1y = -12;
        cam.pickGround(p0x, p0y, grabbed);
        cam.panByPixels(p1x - p0x, p1y - p0y);
        double[] now = new double[2];
        cam.pickGround(p1x, p1y, now);
        Assertions.assertEquals(grabbed[0], now[0], 1e-4, "panned worldX");
        Assertions.assertEquals(grabbed[1], now[1], 1e-4, "panned worldZ");
    }

    @Test
    void zoomClampsToRange() {
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        for (int i = 0; i < 100; i++) {
            cam.zoomBy(2.0);
        }
        Assertions.assertEquals(ZoneMapCamera.MAX_PX_PER_BLOCK, cam.pxPerBlock, EPS, "zoom in clamps to max");
        for (int i = 0; i < 100; i++) {
            cam.zoomBy(0.5);
        }
        Assertions.assertEquals(ZoneMapCamera.MIN_PX_PER_BLOCK, cam.pxPerBlock, EPS, "zoom out clamps to min");
    }

    @Test
    void groundColumnsAreUnmagnified() {
        // At the reference plane the magnification is exactly 1, so canvas == world offset (in blocks).
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        Assertions.assertEquals(10.0, cam.canvasX(10, 64, 5), EPS);
        Assertions.assertEquals(5.0, cam.canvasY(10, 64, 5), EPS);
    }

    @Test
    void tallerColumnsMagnifyAndSortInFront() {
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        // An off-centre column drawn taller projects FARTHER from centre (perspective swell) and sorts in
        // front (smaller depth) so it occludes lower terrain.
        double groundX = cam.canvasX(10, 64, 10);
        double highX = cam.canvasX(10, 90, 10);
        Assertions.assertTrue(Math.abs(highX) > Math.abs(groundX), "taller column magnifies outward");
        Assertions.assertTrue(cam.depth(10, 90, 10) < cam.depth(10, 64, 10), "taller column sorts in front");
    }

    @Test
    void centreColumnStaysCentredAtAnyHeight() {
        // A column directly under the camera projects to the centre no matter how tall (top-down nadir).
        ZoneMapCamera cam = new ZoneMapCamera(50, 64, -20);
        Assertions.assertEquals(0.0, cam.canvasX(50, 64, -20), EPS);
        Assertions.assertEquals(0.0, cam.canvasX(50, 120, -20), EPS, "tall centre column still centred in X");
        Assertions.assertEquals(0.0, cam.canvasY(50, 120, -20), EPS, "tall centre column still centred in Y");
    }

    @Test
    void terrainPickInvertsProjectionAtSurfaceHeight() {
        // The load-bearing fix for cursor/highlight detachment: a column drawn at its real surface height
        // must ray-pick back to itself. (A ground-plane pick drifts here because the surface is above the
        // reference plane, so perspective shifts its projected pixel away from its footprint — worst at the
        // off-centre, zoomed-out corners exercised below.)
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        cam.pxPerBlock = 3.0;
        int h = 84; // 20 above the reference plane → magnification clearly != 1
        ZoneMapCamera.SurfaceQuery flat = (x, z) -> h;
        for (int wx = -24; wx <= 24; wx += 8) {
            for (int wz = -24; wz <= 24; wz += 8) {
                double sx = cam.canvasX(wx, h, wz) * cam.pxPerBlock;
                double sy = cam.canvasY(wx, h, wz) * cam.pxPerBlock;
                int[] hit = cam.pickTerrain(sx, sy, flat);
                Assertions.assertNotNull(hit, "ray hit at " + wx + "," + wz);
                Assertions.assertEquals(wx, hit[0], 1, "picked X near " + wx + "," + wz);
                Assertions.assertEquals(wz, hit[2], 1, "picked Z near " + wx + "," + wz);
                Assertions.assertEquals(h, hit[1], "picked surface height");
            }
        }
    }

    @Test
    void terrainPickMissesOverEmptyColumns() {
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        Assertions.assertNull(cam.pickTerrain(20, 15, (x, z) -> Integer.MIN_VALUE), "no data → no hit");
    }

    @Test
    void columnsReachingTheCameraHeightAreAboveIt() {
        // The camera floats CAM_HEIGHT (96) above camY. A column's top face is surface + 1, so a surface of
        // camY + 94 tops out one block under the camera and camY + 95 tops out exactly at it.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        Assertions.assertFalse(cam.isAtOrAboveCamera(64 + 94), "a top one block below the camera is in view");
        Assertions.assertTrue(cam.isAtOrAboveCamera(64 + 95), "a top exactly at the camera is above it");
        Assertions.assertTrue(cam.isAtOrAboveCamera(64 + 300), "a top far above the camera is above it");
        Assertions.assertFalse(cam.isAtOrAboveCamera(64), "ground level is in view");
        Assertions.assertFalse(cam.isAtOrAboveCamera(Integer.MIN_VALUE), "a column with no data is not 'above' anything");

        ZoneMapCamera low = new ZoneMapCamera(0, 10, 0);
        Assertions.assertFalse(low.isAtOrAboveCamera(10 + 94), "the threshold follows the camera's own height");
        Assertions.assertTrue(low.isAtOrAboveCamera(10 + 95), "the threshold follows the camera's own height");
    }

    @Test
    void terrainPickStepsOverATowerAtTheCameraHeight() {
        // A tall column next to the planner (a mountain, a skyscraper) tops out above the camera. The cursor ray
        // starts at the camera, so it began inside that column and locked on to it, however far from the tower
        // the cursor actually was. The tower is not drawn, so it cannot be a target: the ray must carry on down to
        // the ground it can see.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        cam.pxPerBlock = 4.0;
        int ground = 64;
        int towerTop = 64 + 140; // far above the camera at 64 + 96
        ZoneMapCamera.SurfaceQuery tower = (x, z) -> (x >= -3 && x <= 3 && z >= -3 && z <= 3) ? towerTop : ground;

        // 40 px right of centre is 10 blocks out at ground level: well clear of the tower, but the ray crosses
        // the tower's columns on its way down from the camera.
        int[] hit = cam.pickTerrain(40, 0, tower);
        Assertions.assertNotNull(hit, "the ground beyond the tower is pickable");
        Assertions.assertEquals(ground, hit[1], "the ray hits the ground, not the tower it passed over");
        Assertions.assertEquals(9, hit[0], 1, "the ground column under the cursor (10 blocks out, minus the plane offset)");
        Assertions.assertEquals(0, hit[2], 1, "straight east of the centre");
    }

    @Test
    void terrainPickNeverReturnsAColumnAtOrAboveTheCamera() {
        // The edge of the rule: surface 159 tops out at the camera's height (160) and is skipped; surface 158
        // tops out one block under it and is still the thing the cursor lands on.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        Assertions.assertNull(cam.pickTerrain(0, 0, (x, z) -> 64 + 95),
                "a world whose every top reaches the camera has nothing in view to pick");
        int[] hit = cam.pickTerrain(0, 0, (x, z) -> 64 + 94);
        Assertions.assertNotNull(hit, "a top just under the camera is still in view");
        Assertions.assertEquals(64 + 94, hit[1]);
    }

    @Test
    void surveyZoomPicksReachViewportCorners() {
        // At the 7.1.x survey floor (0.125 px/block) a viewport corner maps to ~850 blocks from the
        // camera, and the ray needs that many steps to descend to the plane — beyond the old fixed
        // march budget, which silently returned null there. The budget must scale with zoom.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        cam.pxPerBlock = ZoneMapCamera.MIN_PX_PER_BLOCK;
        int h = 64; // flat world at the reference plane
        ZoneMapCamera.SurfaceQuery flat = (x, z) -> h;
        // Half-extents of the real 213x100 viewport, swept coarsely.
        for (int px = -106; px <= 106; px += 53) {
            for (int py = -50; py <= 50; py += 25) {
                int[] hit = cam.pickTerrain(px, py, flat);
                Assertions.assertNotNull(hit, "far pick reached at px " + px + "," + py);
                double gx = px / cam.pxPerBlock;
                double gz = py / cam.pxPerBlock;
                // The flat top face sits 1 block above the plane, so the ray hits ~95/96 of the way out.
                Assertions.assertEquals(gx * 95 / 96.0, hit[0], 3.0, "far pick X at px " + px + "," + py);
                Assertions.assertEquals(gz * 95 / 96.0, hit[2], 3.0, "far pick Z at px " + px + "," + py);
                Assertions.assertEquals(h, hit[1], "far pick surface height");
            }
        }
    }

    @Test
    void groundBoundsSpanExactlyTheViewport() {
        // The far-zoom LOD samples the ground plane with no perspective overscan, so its bounds are the
        // plain viewport span in blocks: (pixels/2)/pxPerBlock, plus any requested padding.
        ZoneMapCamera cam = new ZoneMapCamera(10, 64, -10);
        cam.pxPerBlock = 0.5;
        double[] b = cam.visibleGroundBounds(213, 100, 0);
        Assertions.assertEquals(10 - 213, b[0], EPS, "west edge");
        Assertions.assertEquals(-10 - 100, b[1], EPS, "north edge");
        Assertions.assertEquals(10 + 213, b[2], EPS, "east edge");
        Assertions.assertEquals(-10 + 100, b[3], EPS, "south edge");

        double[] padded = cam.visibleGroundBounds(213, 100, 16);
        Assertions.assertEquals(10 - 213 - 16, padded[0], EPS, "padded west edge");
    }

    @Test
    void groundBoundsStayInsidePerspectiveBounds() {
        // The LOD grid is a subset of the conservative frustum box, so near-mode culling can't regress.
        ZoneMapCamera cam = new ZoneMapCamera(200, 64, -300);
        cam.pxPerBlock = 0.75;
        double[] ground = cam.visibleGroundBounds(213, 100, 0);
        double[] full = cam.visibleWorldBounds(213, 100, 0);
        Assertions.assertTrue(ground[0] >= full[0] && ground[2] <= full[2], "X span nested");
        Assertions.assertTrue(ground[1] >= full[1] && ground[3] <= full[3], "Z span nested");
    }

    @Test
    void visibleBoundsContainCameraCentre() {
        ZoneMapCamera cam = new ZoneMapCamera(200.0, 64, -300.0);
        cam.pxPerBlock = 3.0;
        double[] b = cam.visibleWorldBounds(213, 100, 64);
        Assertions.assertTrue(b[0] <= cam.camX && cam.camX <= b[2], "camX within bounds");
        Assertions.assertTrue(b[1] <= cam.camZ && cam.camZ <= b[3], "camZ within bounds");
        Assertions.assertTrue(b[2] - b[0] > 0 && b[3] - b[1] > 0, "non-empty bounds");
    }
}
