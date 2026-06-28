/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
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
    void visibleBoundsContainCameraCentre() {
        ZoneMapCamera cam = new ZoneMapCamera(200.0, 64, -300.0);
        cam.pxPerBlock = 3.0;
        double[] b = cam.visibleWorldBounds(213, 100, 64);
        Assertions.assertTrue(b[0] <= cam.camX && cam.camX <= b[2], "camX within bounds");
        Assertions.assertTrue(b[1] <= cam.camZ && cam.camZ <= b[3], "camZ within bounds");
        Assertions.assertTrue(b[2] - b[0] > 0 && b[3] - b[1] > 0, "non-empty bounds");
    }
}
