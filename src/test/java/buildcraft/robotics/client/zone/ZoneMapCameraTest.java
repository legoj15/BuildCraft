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
    /** The build floor of a 26.x overworld, which the renderer's window and the picker both reach down to. */
    private static final int MIN_BUILD_Y = -64;

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
    void surveyZoomReliefStaysNearItsFootprint() {
        // Zooming out must keep the field of view sane: with a fixed-height camera, a 30-block tree at the
        // edge of a ~1,700-block survey view was flung hundreds of blocks outward, smearing the map into
        // radial streaks. Relief should shift a column by at most a couple of pixels at the survey floor.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        cam.pxPerBlock = ZoneMapCamera.MIN_PX_PER_BLOCK;
        double edge = 106 / cam.pxPerBlock; // the 213-px viewport's half-width, in ground blocks
        double shiftPx = (cam.canvasX(edge, 94, 0) - cam.canvasX(edge, 64, 0)) * cam.pxPerBlock;
        Assertions.assertTrue(shiftPx > 0, "relief still swells outward");
        Assertions.assertTrue(shiftPx < 2.0, "relief shift at survey zoom was " + shiftPx + " px");
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
                int[] hit = cam.pickTerrain(sx, sy, flat, MIN_BUILD_Y);
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
        Assertions.assertNull(cam.pickTerrain(20, 15, (x, z) -> Integer.MIN_VALUE, MIN_BUILD_Y), "no data → no hit");
    }

    @Test
    void columnsNearTheCameraHeightAreClipped() {
        // The camera floats CAM_HEIGHT (96) above camY, and a column's top face is surface + 1. Columns whose top
        // comes within NEAR_CLIP (9) of the camera are left out: a surface of camY + 85 tops out 10 blocks under
        // the camera and is in view, camY + 86 tops out 9 under it and is clipped.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        Assertions.assertFalse(cam.isNearClipped(64 + 85), "a top ten blocks below the camera is in view");
        Assertions.assertTrue(cam.isNearClipped(64 + 86), "a top nine blocks below the camera is clipped");
        Assertions.assertTrue(cam.isNearClipped(64 + 94), "a top one block below the camera is clipped");
        Assertions.assertTrue(cam.isNearClipped(64 + 95), "a top exactly at the camera is clipped");
        Assertions.assertTrue(cam.isNearClipped(64 + 300), "a top far above the camera is clipped");
        Assertions.assertFalse(cam.isNearClipped(64), "ground level is in view");
        Assertions.assertFalse(cam.isNearClipped(Integer.MIN_VALUE), "a column with no data is not 'near' anything");

        ZoneMapCamera low = new ZoneMapCamera(0, 10, 0);
        Assertions.assertFalse(low.isNearClipped(10 + 85), "the threshold follows the camera's own height");
        Assertions.assertTrue(low.isNearClipped(10 + 86), "the threshold follows the camera's own height");
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
        int[] hit = cam.pickTerrain(40, 0, tower, MIN_BUILD_Y);
        Assertions.assertNotNull(hit, "the ground beyond the tower is pickable");
        Assertions.assertEquals(ground, hit[1], "the ray hits the ground, not the tower it passed over");
        Assertions.assertEquals(9, hit[0], 1, "the ground column under the cursor (10 blocks out, minus the plane offset)");
        Assertions.assertEquals(0, hit[2], 1, "straight east of the centre");
    }

    @Test
    void terrainPickNeverReturnsAColumnNearOrAboveTheCamera() {
        // The edge of the rule: surface 150 tops out 9 blocks under the camera (160) and is skipped; surface 149
        // tops out 10 under it and is still the thing the cursor lands on.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        Assertions.assertNull(cam.pickTerrain(0, 0, (x, z) -> 64 + 95, MIN_BUILD_Y),
                "a world whose every top reaches the camera has nothing in view to pick");
        Assertions.assertNull(cam.pickTerrain(0, 0, (x, z) -> 64 + 86, MIN_BUILD_Y),
                "nor one whose every top is within the near clip of it");
        int[] hit = cam.pickTerrain(0, 0, (x, z) -> 64 + 85, MIN_BUILD_Y);
        Assertions.assertNotNull(hit, "a top just outside the near clip is still in view");
        Assertions.assertEquals(64 + 85, hit[1]);
    }

    // ── Picking reaches as deep as the renderer draws ────────────────────────────────────────

    @Test
    void terrainFarBelowThePlannerIsPickableWhereItIsDrawn() {
        // A planner on a mountain (Y 150) over lowland (Y 40): the renderer draws that lowland (its window reaches
        // the build floor), 110 blocks under the planner. The pick used to stop 80 blocks under the planner, so the
        // terrain was on screen but could not be hovered or painted.
        ZoneMapCamera cam = new ZoneMapCamera(0.5, 150, 0.5);
        cam.pxPerBlock = 3.0;
        int lowland = 40;
        int[][] columns = {{0, 0}, {12, -9}, {-20, 15}, {30, 22}, {-31, -27}};
        for (int[] column : columns) {
            double sx = cam.canvasX(column[0] + 0.5, lowland + 1, column[1] + 0.5) * cam.pxPerBlock;
            double sy = cam.canvasY(column[0] + 0.5, lowland + 1, column[1] + 0.5) * cam.pxPerBlock;
            int[] hit = cam.pickTerrain(sx, sy, (x, z) -> lowland, MIN_BUILD_Y);
            Assertions.assertNotNull(hit, "the lowland column drawn at " + column[0] + "," + column[1] + " is pickable");
            Assertions.assertEquals(column[0], hit[0], 1, "picked X");
            Assertions.assertEquals(column[1], hit[2], 1, "picked Z");
            Assertions.assertEquals(lowland, hit[1], "picked surface height");
        }
    }

    @Test
    void terrainAtTheBuildFloorIsPickable() {
        // The deepest thing the renderer can draw is the floor itself; a world flattened to it is still pickable.
        ZoneMapCamera cam = new ZoneMapCamera(0.5, 150, 0.5);
        cam.pxPerBlock = 3.0;
        int[] hit = cam.pickTerrain(30, -20, (x, z) -> MIN_BUILD_Y, MIN_BUILD_Y);
        Assertions.assertNotNull(hit, "bedrock level terrain 214 blocks under the planner is in view");
        Assertions.assertEquals(MIN_BUILD_Y, hit[1]);
    }

    @Test
    void theSearchEndsAtTheBuildFloor() {
        // No data under the cursor is a miss, and the miss costs one march that ends at the floor: one step per
        // block of ray, so as many steps as the ray is long to the floor (its length to the plane, scaled by the
        // extra depth).
        ZoneMapCamera cam = new ZoneMapCamera(0.5, 150, 0.5);
        cam.pxPerBlock = ZoneMapCamera.MIN_PX_PER_BLOCK;
        int[] calls = {0};
        Assertions.assertNull(cam.pickTerrain(100, 40, (x, z) -> {
            calls[0]++;
            return Integer.MIN_VALUE;
        }, MIN_BUILD_Y));
        double h = cam.camHeight();
        double toPlane = Math.hypot(Math.hypot(100 / cam.pxPerBlock, 40 / cam.pxPerBlock), h);
        double toFloor = toPlane * (h + 150 - MIN_BUILD_Y) / h;
        Assertions.assertEquals(toFloor, calls[0], 2.0, "the march ran from the camera to the floor, no further");
    }

    @Test
    void anAbsurdlyDeepFloorStillCostsABoundedMarch() {
        // A datapack world with a build floor a million blocks down must not turn one hover into a million steps.
        ZoneMapCamera cam = new ZoneMapCamera(0.5, 150, 0.5);
        cam.pxPerBlock = ZoneMapCamera.MIN_PX_PER_BLOCK;
        int[] calls = {0};
        Assertions.assertNull(cam.pickTerrain(106, 50, (x, z) -> {
            calls[0]++;
            return Integer.MIN_VALUE;
        }, -1_000_000));
        Assertions.assertTrue(calls[0] <= 32768, "the march is capped (" + calls[0] + " steps)");
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
                int[] hit = cam.pickTerrain(px, py, flat, MIN_BUILD_Y);
                Assertions.assertNotNull(hit, "far pick reached at px " + px + "," + py);
                double gx = px / cam.pxPerBlock;
                double gz = py / cam.pxPerBlock;
                // The flat top face sits 1 block above the plane, so the ray stops 1/camHeight short of it.
                double reach = (cam.camHeight() - 1) / cam.camHeight();
                Assertions.assertEquals(gx * reach, hit[0], 3.0, "far pick X at px " + px + "," + py);
                Assertions.assertEquals(gz * reach, hit[2], 3.0, "far pick Z at px " + px + "," + py);
                Assertions.assertEquals(h, hit[1], "far pick surface height");
            }
        }
    }

    @Test
    void worldBoundsAreTheGroundSpanWhenNothingIsBelowThePlane() {
        // With the world's floor at the planner's own height there is no de-magnified terrain, so the bounds are the
        // plain viewport span in blocks, (pixels/2)/pxPerBlock, plus any requested padding.
        ZoneMapCamera cam = new ZoneMapCamera(10, 64, -10);
        cam.pxPerBlock = 0.5;
        double[] b = cam.visibleWorldBounds(213, 100, 0, 64);
        Assertions.assertEquals(10 - 213, b[0], EPS, "west edge");
        Assertions.assertEquals(-10 - 100, b[1], EPS, "north edge");
        Assertions.assertEquals(10 + 213, b[2], EPS, "east edge");
        Assertions.assertEquals(-10 + 100, b[3], EPS, "south edge");

        double[] padded = cam.visibleWorldBounds(213, 100, 16, 64);
        Assertions.assertEquals(10 - 213 - 16, padded[0], EPS, "padded west edge");
        Assertions.assertArrayEquals(b, cam.visibleWorldBounds(213, 100, 0, 200), EPS,
                "a floor above the plane changes nothing: terrain above it only projects out sooner");
    }

    @Test
    void worldBoundsGrowWithTheDepthOfTheBuildFloor() {
        // A floor one camera height under the plane is magnified by h/2h = 1/2: it reaches in from twice as far.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        cam.pxPerBlock = 0.5;
        int h = (int) cam.camHeight();
        double[] b = cam.visibleWorldBounds(200, 100, 0, 64 - h);
        Assertions.assertEquals(400.0, b[2], EPS, "east half-span: 100 px / 0.5 px per block = 200 blocks, doubled");
        Assertions.assertEquals(200.0, b[3], EPS, "south half-span: 50 px / 0.5 = 100 blocks, doubled");
        double[] deeper = cam.visibleWorldBounds(200, 100, 0, 64 - 2 * h);
        Assertions.assertEquals(b[2] * 1.5, deeper[2], EPS, "a floor two camera heights under reaches in from three times as far");
    }

    @Test
    void visibleBoundsContainCameraCentre() {
        ZoneMapCamera cam = new ZoneMapCamera(200.0, 64, -300.0);
        cam.pxPerBlock = 3.0;
        double[] b = cam.visibleWorldBounds(213, 100, 64, MIN_Y);
        Assertions.assertTrue(b[0] <= cam.camX && cam.camX <= b[2], "camX within bounds");
        Assertions.assertTrue(b[1] <= cam.camZ && cam.camZ <= b[3], "camZ within bounds");
        Assertions.assertTrue(b[2] - b[0] > 0 && b[3] - b[1] > 0, "non-empty bounds");
    }

    // ── The scanned window covers everything that can project into the viewport ───────────

    private static final int MIN_Y = -64;
    private static final int VIEW_W = 213;
    private static final int VIEW_H = 100;

    /** World X of the point that projects to canvas offset {@code canvasX} (blocks) when drawn at height {@code y}. */
    private static double worldXAt(ZoneMapCamera cam, double canvasX, double y) {
        double magnification = cam.canvasX(cam.camX + 1, y, cam.camZ);
        return cam.camX + canvasX / magnification;
    }

    private static double worldZAt(ZoneMapCamera cam, double canvasY, double y) {
        double magnification = cam.canvasY(cam.camX, y, cam.camZ + 1);
        return cam.camZ + canvasY / magnification;
    }

    private static boolean inWindow(int[] window, double wx, double wz) {
        int cx = (int) Math.floor(wx) >> 4;
        int cz = (int) Math.floor(wz) >> 4;
        return cx >= window[0] && cx <= window[2] && cz >= window[1] && cz <= window[3];
    }

    @Test
    void windowCoversLowTerrainAtTheViewportEdgeAtEveryZoom() {
        // Terrain BELOW the planner plane is de-magnified: a column far outside the ground span still projects
        // inside the viewport. The window must reach out far enough for the deepest terrain the world can hold
        // (the build floor), in the survey (far) zoom and the per-column near zoom alike.
        for (double camY : new double[] {40, 100, 250}) {
            for (double zoom : new double[] {0.125, 0.25, 0.5, 0.99, 1.0, 2.0, 4.0, 16.0}) {
                ZoneMapCamera cam = new ZoneMapCamera(1234.5, camY, -777.5);
                cam.pxPerBlock = zoom;
                int[] window = cam.visibleChunkWindow(VIEW_W, VIEW_H, MIN_Y);
                // Tops of columns from the floor up to just under the near clip.
                for (double y = MIN_Y + 1; y < camY + cam.camHeight() - 10; y += 7) {
                    for (int px = -VIEW_W / 2; px <= VIEW_W / 2; px += 53) {
                        for (int py = -VIEW_H / 2; py <= VIEW_H / 2; py += 25) {
                            double wx = worldXAt(cam, px / zoom, y);
                            double wz = worldZAt(cam, py / zoom, y);
                            Assertions.assertTrue(inWindow(window, wx, wz),
                                    "terrain at y=" + y + " drawn at pixel " + px + "," + py + " (world " + wx + "," + wz
                                            + ") is outside the window " + java.util.Arrays.toString(window)
                                            + " at planner y=" + camY + ", " + zoom + " px/block");
                        }
                    }
                }
            }
        }
    }

    @Test
    void windowIsNoWiderThanTheDeepestTerrainNeeds() {
        // A world whose floor is at the planner's own height has no low terrain: the window is the ground span (plus
        // padding), not the span of a deep world.
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        cam.pxPerBlock = ZoneMapCamera.MIN_PX_PER_BLOCK;
        int[] shallow = cam.visibleChunkWindow(VIEW_W, VIEW_H, 64);
        int[] deep = cam.visibleChunkWindow(VIEW_W, VIEW_H, MIN_Y);
        Assertions.assertTrue(shallow[2] - shallow[0] < deep[2] - deep[0], "a deeper world needs a wider window");
        double ground = VIEW_W / ZoneMapCamera.MIN_PX_PER_BLOCK / 16.0; // chunks across the viewport at the plane
        Assertions.assertTrue(shallow[2] - shallow[0] + 1 <= ground + 8,
                "with nothing below the plane, only the ground span plus padding");
    }

    @Test
    void anAbsurdlyWideWindowIsClippedAroundTheCameraNotOffOneSide() {
        // A datapack world thousands of blocks deep would ask for a huge scan; it is capped, but around the
        // camera, so neither the west nor the east of the view is the side that goes missing.
        ZoneMapCamera cam = new ZoneMapCamera(0.5, 300, 0.5);
        cam.pxPerBlock = ZoneMapCamera.MIN_PX_PER_BLOCK;
        int[] window = cam.visibleChunkWindow(VIEW_W, VIEW_H, -2000);
        Assertions.assertTrue(window[2] - window[0] + 1 <= ZoneMapCamera.MAX_CHUNK_SPAN, "the scan is capped");
        Assertions.assertEquals(0, window[0] + window[2], 1, "the cap is symmetric about the camera chunk (X)");
        Assertions.assertEquals(0, window[1] + window[3], 1, "the cap is symmetric about the camera chunk (Z)");
    }

    // ── Render and pick agree on what is where ─────────────────────────────────────────────

    @Test
    void pickLandsOnEveryColumnTheRendererDraws() {
        // The renderer draws a column's top face at (column - cam) * magnification(top); the pick ray must come
        // down on that same column. A column whose top is within MIN_DIST of the camera was drawn at a CLAMPED
        // magnification while the ray used the true geometry, so the cursor sat beside the drawn column.
        ZoneMapCamera cam = new ZoneMapCamera(0.5, 64, 0.5);
        cam.pxPerBlock = 4.0;
        int[][] columns = {{3, 2}, {-5, 7}, {6, -4}, {-2, -3}};
        for (int surface = 64 - 30; surface <= 64 + 100; surface++) {
            final int s = surface;
            if (cam.isNearClipped(s)) {
                Assertions.assertNull(cam.pickTerrain(10, 10, (x, z) -> s, MIN_BUILD_Y),
                        "a world of undrawn columns has nothing to pick (surface " + s + ")");
                continue;
            }
            for (int[] column : columns) {
                double sx = cam.canvasX(column[0] + 0.5, s + 1, column[1] + 0.5) * cam.pxPerBlock;
                double sy = cam.canvasY(column[0] + 0.5, s + 1, column[1] + 0.5) * cam.pxPerBlock;
                int[] hit = cam.pickTerrain(sx, sy, (x, z) -> s, MIN_BUILD_Y);
                Assertions.assertNotNull(hit, "a drawn column at surface " + s + " is pickable");
                Assertions.assertEquals(column[0], hit[0], 1, "picked X for the column drawn there, surface " + s);
                Assertions.assertEquals(column[1], hit[2], 1, "picked Z for the column drawn there, surface " + s);
            }
        }
    }

    @Test
    void strideFollowsTheZoom() {
        ZoneMapCamera cam = new ZoneMapCamera(0, 64, 0);
        for (double zoom : new double[] {16, 4, 1}) {
            cam.pxPerBlock = zoom;
            Assertions.assertEquals(1, cam.stride(), zoom + " px/block");
        }
        double[][] far = {{0.9, 2}, {0.5, 2}, {0.3, 4}, {0.25, 4}, {0.2, 8}, {0.125, 8}};
        for (double[] c : far) {
            cam.pxPerBlock = c[0];
            Assertions.assertEquals((int) c[1], cam.stride(), c[0] + " px/block");
        }
    }

    @Test
    void farPickReadsTheSampleTheRendererDraws() {
        // Below 1 px/block the renderer draws one cell per stride x stride columns, at the height of the cell's
        // lowest-corner sample. The pick must report that height (and find the cell where it is drawn), not the exact
        // surface of whichever column the ray reaches, which in a cell with a rise inside it is a different height.
        ZoneMapCamera cam = new ZoneMapCamera(0.5, 64, 0.5);
        cam.pxPerBlock = ZoneMapCamera.MIN_PX_PER_BLOCK;
        int stride = cam.stride();
        Assertions.assertEquals(8, stride);
        // Samples (cell origins) sit at 70; every other column of a cell stands at 80.
        ZoneMapCamera.SurfaceQuery exact =
                (x, z) -> (Math.floorMod(x, stride) == 0 && Math.floorMod(z, stride) == 0) ? 70 : 80;
        for (int cellX = -64; cellX <= 64; cellX += 24) {
            for (int cellZ = -32; cellZ <= 32; cellZ += 16) {
                double cx = cellX + stride / 2.0;
                double cz = cellZ + stride / 2.0;
                double sx = cam.canvasX(cx, 71, cz) * cam.pxPerBlock;
                double sy = cam.canvasY(cx, 71, cz) * cam.pxPerBlock;
                int[] hit = cam.pickTerrain(sx, sy, exact, MIN_BUILD_Y);
                Assertions.assertNotNull(hit, "cell " + cellX + "," + cellZ + " is pickable");
                Assertions.assertEquals(70, hit[1], "the height drawn for the cell, not a taller column in it");
                Assertions.assertEquals(cellX, Math.floorDiv(hit[0], stride) * stride, "the cell it was drawn in (X)");
                Assertions.assertEquals(cellZ, Math.floorDiv(hit[2], stride) * stride, "the cell it was drawn in (Z)");
            }
        }
    }
}
