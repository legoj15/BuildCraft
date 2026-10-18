/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.gametest.framework.GameTestHelper;

//? if >=1.21.10 {
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import buildcraft.robotics.client.render.ZoneMapGeometry;
import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.client.zone.ZonePlannerMapChunk;
import buildcraft.robotics.client.zone.ZonePlannerMapDataClient;
import buildcraft.robotics.zone.ZonePlan;
//?}

/**
 * Game tests for the Zone Planner map's zone overlay as the viewport draws it ({@link ZoneMapGeometry#emitOverlays}),
 * against a real level and its chunk data. The vertex consumer is a counting proxy, so the tests need no renderer:
 * they pin what reaches the GPU &mdash; one double-wound quad (eight vertices) per painted column on mapped terrain
 * inside the view, one per terrain stride cell at survey zoom, nothing for what is out of view or off the mapped
 * terrain, and the in-progress stroke preview in place of its layer.
 *
 * <p>The terrain is a stone floor of one chunk, laid well away from the test arenas (which are void, and which other
 * tests build in), so the columns the overlay may tint are exactly the ones the test placed, and the floor is taken
 * up again afterwards.
 */
public class ZonePlannerOverlayTester {

    // The counting consumer is a proxy for the client-only VertexConsumer, which the older NeoForge loader on the
    // 1.21.1 line refuses to load on the dedicated-server test dist ("invalid dist DEDICATED_SERVER"). There the
    // cell cache these tests lean on is covered by ZoneOverlayCacheTest, and the tests below pass trivially.

    public static void overlayDrawsOnlyPaintedMappedColumnsInView(GameTestHelper helper) {
        //? if >=1.21.10 {
        Impl.inView(helper);
        //?} else {
        /*helper.succeed();*/
        //?}
    }

    public static void overlayAtSurveyZoomDrawsOneQuadPerTerrainSample(GameTestHelper helper) {
        //? if >=1.21.10 {
        Impl.atSurveyZoom(helper);
        //?} else {
        /*helper.succeed();*/
        //?}
    }

    public static void terrainAndOverlayLeaveOutColumnsReachingTheCamera(GameTestHelper helper) {
        //? if >=1.21.10 {
        Impl.reachingTheCamera(helper);
        //?} else {
        /*helper.succeed();*/
        //?}
    }

    public static void surveyOutlineIsDrawnOnTerrainCells(GameTestHelper helper) {
        //? if >=1.21.10 {
        Impl.surveyOutlineLiesOnTerrainCells(helper);
        //?} else {
        /*helper.succeed();*/
        //?}
    }

    public static void lowTerrainAtTheEdgeOfSurveyZoomIsDrawn(GameTestHelper helper) {
        //? if >=1.21.10 {
        Impl.lowTerrainAtSurveyEdge(helper);
        //?} else {
        /*helper.succeed();*/
        //?}
    }

    //? if >=1.21.10 {
    private static final class Impl {

        private static final int LAYER = 5;
        private static final int VERTICES_PER_QUAD = 8;
        /** How far from the arena the floor chunk lies, in blocks (chunk-aligned below). */
        private static final int FLOOR_DISTANCE = 2000;

        /** Counts the vertices emitted through a {@link VertexConsumer} without implementing any of its methods. */
        private static final class VertexCounter implements InvocationHandler {
            int vertices;

            VertexConsumer consumer() {
                return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                        new Class<?>[] {VertexConsumer.class}, this);
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> "VertexCounter";
                    };
                }
                if (method.getName().startsWith("addVertex")) {
                    vertices++;
                }
                Class<?> type = method.getReturnType();
                if (type.isInstance(proxy)) {
                    return proxy; // the builder chain: setColor(...) and friends return the consumer
                }
                return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
            }
        }

        /** A chunk-aligned stone floor, 16x16, and the planner position the layers are painted relative to. */
        private static final class Floor {
            final ServerLevel level;
            /** Lowest world corner of the floor (a multiple of 16 on both axes). */
            final int x;
            final int y;
            final int z;
            /** The planner: deliberately not chunk-aligned, so tile-relative and world chunks do not line up. */
            final BlockPos planner;

            Floor(GameTestHelper helper) {
                level = helper.getLevel();
                BlockPos arena = helper.absolutePos(BlockPos.ZERO);
                x = ((arena.getX() + FLOOR_DISTANCE) >> 4) << 4;
                z = ((arena.getZ() + FLOOR_DISTANCE) >> 4) << 4;
                y = arena.getY() + 2;
                planner = new BlockPos(x - 3, y, z - 5);
            }

            void lay() {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        level.setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), Blocks.STONE.defaultBlockState());
                    }
                }
            }

            void lift() {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        level.setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }

            /** Marks the world columns {@code [wx0, wx1] x [wz0, wz1]} (offsets from the floor's corner) on a layer. */
            void paint(ZonePlan layer, int wx0, int wz0, int wx1, int wz1) {
                layer.paintRect(x + wx0 - planner.getX(), z + wz0 - planner.getZ(),
                        x + wx1 - planner.getX(), z + wz1 - planner.getZ(), true);
            }

            void paintOne(ZonePlan layer, int wx, int wz) {
                paint(layer, wx, wz, wx, wz);
            }

            ZoneMapCamera camera(double pxPerBlock, int atX, int atZ) {
                ZoneMapCamera cam = new ZoneMapCamera(x + atX, y, z + atZ);
                cam.pxPerBlock = pxPerBlock;
                return cam;
            }

            /** A survey-zoom camera whose plane is {@code above} blocks over the floor's level, centred over the floor
             *  shifted by {@code (atX, atZ)}: the floor is then below the plane, and de-magnified. */
            ZoneMapCamera cameraOver(double pxPerBlock, int atX, int atZ, int above) {
                ZoneMapCamera cam = new ZoneMapCamera(x + atX, y + above, z + atZ);
                cam.pxPerBlock = pxPerBlock;
                return cam;
            }

            /** A camera hovering at an absolute height over the floor, close up like {@link #camera}. */
            ZoneMapCamera cameraAtHeight(double height) {
                ZoneMapCamera cam = new ZoneMapCamera(x + 8, height - ZoneMapCamera.CAM_HEIGHT, z + 8);
                cam.pxPerBlock = 8.0;
                return cam;
            }

            int emitTerrain(ZoneMapCamera cam) {
                VertexCounter counter = new VertexCounter();
                ZoneMapGeometry.emitTerrain(counter.consumer(), new Matrix4f(), cam, 213, 213, level);
                return counter.vertices;
            }

            /** Whether the map has a surface for the world column {@code (wx, wz)}. */
            boolean mapped(int wx, int wz) {
                ZonePlannerMapChunk chunk = ZonePlannerMapDataClient.INSTANCE.getChunk(level, wx >> 4, wz >> 4);
                return chunk != null && chunk.getSurfaceY(wx & 15, wz & 15) != ZonePlannerMapChunk.NO_DATA;
            }

            /** The overlay with no layers, for a planner at {@code at}: just the survey-square outline, if it is in view. */
            int emitOutline(BlockPos at, ZoneMapCamera cam) {
                VertexCounter counter = new VertexCounter();
                ZoneMapGeometry.emitOverlays(counter.consumer(), new Matrix4f(), cam, 213, 213, at, new ZonePlan[16], null,
                        -1, null, level);
                return counter.vertices;
            }

            int emit(ZoneMapCamera cam, ZonePlan[] layers, ZonePlan buffer, int bufferIndex) {
                VertexCounter counter = new VertexCounter();
                ZoneMapGeometry.emitOverlays(counter.consumer(), new Matrix4f(), cam, 213, 213, planner, layers, buffer,
                        bufferIndex, null, level);
                return counter.vertices;
            }
        }

        private static ZonePlan[] layers(ZonePlan layer) {
            ZonePlan[] layers = new ZonePlan[16];
            layers[LAYER] = layer;
            return layers;
        }

        static void inView(GameTestHelper helper) {
            ZonePlannerMapDataClient.INSTANCE.clear();
            ZoneMapGeometry.clearOverlayCache();
            Floor floor = new Floor(helper);
            floor.lay();
            try {
                ZonePlan layer = new ZonePlan();
                floor.paint(layer, 1, 1, 3, 3);              // nine columns on the floor
                floor.paint(layer, 600, 600, 609, 609);      // and a patch far from anything on screen
                ZoneMapCamera cam = floor.camera(8.0, 8, 8);
                int expected = 9 * VERTICES_PER_QUAD;

                helper.assertTrue(floor.emit(cam, layers(layer), null, -1) == expected,
                        "nine painted columns on terrain are nine quads, and the far patch is not drawn");
                helper.assertTrue(floor.emit(cam, layers(layer), null, -1) == expected, "the same again from the cached cells");
                helper.assertTrue(floor.emit(cam, new ZonePlan[16], null, -1) == 0, "no layers, no overlay");

                // The in-progress stroke replaces its layer while it is dragged.
                ZonePlan preview = new ZonePlan();
                floor.paintOne(preview, 5, 5);
                helper.assertTrue(floor.emit(cam, layers(layer), preview, LAYER) == VERTICES_PER_QUAD,
                        "the preview stands in for the layer it is editing");
                helper.assertTrue(floor.emit(cam, layers(layer), preview, LAYER + 1) == expected + VERTICES_PER_QUAD,
                        "a preview for another layer is drawn alongside this one, which it leaves alone");
                helper.assertTrue(floor.emit(cam, layers(layer), null, -1) == expected,
                        "and when the stroke ends the layer is back as it was");

                // A layer edited since the last frame is redrawn as it now is.
                floor.paintOne(layer, 0, 0);
                helper.assertTrue(floor.emit(cam, layers(layer), null, -1) == expected + VERTICES_PER_QUAD,
                        "an edited layer shows its new column");

                // Looking somewhere else draws none of it.
                helper.assertTrue(floor.emit(floor.camera(8.0, 5000, 5000), layers(layer), null, -1) == 0,
                        "columns out of view are not drawn");

                // Painted columns with no mapped terrain under them draw nothing either.
                ZonePlan overVoid = new ZonePlan();
                floor.paint(overVoid, 500, 500, 509, 509);
                helper.assertTrue(floor.emit(floor.camera(8.0, 505, 505), layers(overVoid), null, -1) == 0,
                        "terrain that is not mapped (not loaded) carries no tint");
                helper.succeed();
            } finally {
                floor.lift();
                ZoneMapGeometry.clearOverlayCache();
                ZonePlannerMapDataClient.INSTANCE.clear();
            }
        }

        static void reachingTheCamera(GameTestHelper helper) {
            ZonePlannerMapDataClient.INSTANCE.clear();
            ZoneMapGeometry.clearOverlayCache();
            Floor floor = new Floor(helper);
            floor.lay();
            try {
                ZonePlan layer = new ZonePlan();
                floor.paint(layer, 1, 1, 3, 3); // nine columns on the floor
                ZonePlannerMapChunk chunk = ZonePlannerMapDataClient.INSTANCE.getChunk(floor.level, floor.x >> 4, floor.z >> 4);
                helper.assertTrue(chunk != null, "baseline: the floor's chunk is mapped");
                int surface = chunk.getSurfaceY(2, 2);
                helper.assertTrue(surface != ZonePlannerMapChunk.NO_DATA, "baseline: the floor has a surface");

                // A column's top face is surface + 1. A camera more than NEAR_CLIP above that sees the floor...
                ZoneMapCamera seeing = floor.cameraAtHeight(surface + 1 + ZoneMapCamera.NEAR_CLIP + 1);
                int seenTerrain = floor.emitTerrain(seeing);
                helper.assertTrue(seenTerrain > 0, "baseline: the floor is drawn from above");
                helper.assertTrue(floor.emit(seeing, layers(layer), null, -1) == 9 * VERTICES_PER_QUAD,
                        "baseline: its nine painted columns are nine quads");

                // ...and one level with the top face does not: those columns reach the camera, so neither the
                // terrain nor the zone tint over it is drawn (and picking steps over them, in ZoneMapCameraTest).
                ZoneMapCamera atTop = floor.cameraAtHeight(surface + 1);
                helper.assertTrue(floor.emitTerrain(atTop) < seenTerrain,
                        "the floor's columns are left out of the terrain once they reach the camera");
                helper.assertTrue(floor.emit(atTop, layers(layer), null, -1) == 0,
                        "and so is the tint painted over them");

                // Not yet at the camera is not enough: within the near clip, the projection's clamp would draw the
                // column somewhere the pick ray does not look, so those are left out too.
                ZoneMapCamera nearClip = floor.cameraAtHeight(surface + 1 + ZoneMapCamera.NEAR_CLIP);
                helper.assertTrue(floor.emitTerrain(nearClip) < seenTerrain,
                        "columns within the near clip of the camera are left out of the terrain");
                helper.assertTrue(floor.emit(nearClip, layers(layer), null, -1) == 0, "and carry no tint");

                // A camera below everything mapped sees none of it.
                ZoneMapCamera below = floor.cameraAtHeight(surface - 500);
                helper.assertTrue(floor.emitTerrain(below) == 0, "no terrain at all when every column reaches the camera");
                helper.assertTrue(floor.emit(below, layers(layer), null, -1) == 0, "and no tint");

                // The cull is per frame, not baked into the cached cells: back above, the tint is back.
                helper.assertTrue(floor.emit(seeing, layers(layer), null, -1) == 9 * VERTICES_PER_QUAD,
                        "the tint returns with the camera");
                helper.succeed();
            } finally {
                floor.lift();
                ZoneMapGeometry.clearOverlayCache();
                ZonePlannerMapDataClient.INSTANCE.clear();
            }
        }

        /**
         * Terrain below the planner is de-magnified, so it shows at the edge of the survey view from beyond the
         * ground-level span of the viewport. The survey camera floats {@link ZoneMapCamera#camHeight()} (3,072) blocks
         * up, so a floor 99 blocks under the plane shrinks by only about 3 %: the viewport spans 852 blocks of ground
         * out from the centre, and a little further of the floor. A floor placed just past the ground span, but
         * projecting inside it, must be drawn, and so must the tint over it.
         */
        static void lowTerrainAtSurveyEdge(GameTestHelper helper) {
            ZonePlannerMapDataClient.INSTANCE.clear();
            ZoneMapGeometry.clearOverlayCache();
            Floor floor = new Floor(helper);
            floor.lay();
            try {
                ZonePlan layer = new ZonePlan();
                floor.paint(layer, 0, 0, 15, 15); // the whole floor chunk: four 8x8 samples at survey zoom
                ZonePlannerMapChunk chunk = ZonePlannerMapDataClient.INSTANCE.getChunk(floor.level, floor.x >> 4, floor.z >> 4);
                helper.assertTrue(chunk != null, "baseline: the floor's chunk is mapped");

                // Looking at the floor itself, from straight above.
                ZoneMapCamera overhead = floor.cameraOver(ZoneMapCamera.MIN_PX_PER_BLOCK, 8, 8, 99);
                helper.assertTrue(floor.emitTerrain(overhead) > 0, "baseline: the floor is drawn from straight above");
                helper.assertTrue(floor.emit(overhead, layers(layer), null, -1) == 4 * VERTICES_PER_QUAD,
                        "baseline: its four samples carry the tint");

                // The same floor moved out until its near edge projects 840 blocks out (inside the 852-block ground
                // half-span), which puts its far edge past that span in the world.
                double h = overhead.camHeight();
                int out = (int) Math.floor(840 * (h + 99) / h) + 8; // distance from the camera to the floor's centre
                ZoneMapCamera edge = floor.cameraOver(ZoneMapCamera.MIN_PX_PER_BLOCK, 8 + out, 8, 99);
                helper.assertTrue(out + 8 > 852, "baseline: the floor reaches past the ground span");
                helper.assertTrue(floor.emitTerrain(edge) > 0,
                        "the floor at the edge of the survey view is drawn, though it lies past the ground span");
                helper.assertTrue(floor.emit(edge, layers(layer), null, -1) == 4 * VERTICES_PER_QUAD,
                        "and so is the tint over it, on exactly the floor's four samples");
                helper.succeed();
            } finally {
                floor.lift();
                ZoneMapGeometry.clearOverlayCache();
                ZonePlannerMapDataClient.INSTANCE.clear();
            }
        }

        /**
         * The survey-square outline is drawn on the terrain's own cells: one quad per mapped terrain cell the square's
         * edge crosses, a block wide close up and a stride-wide cell at survey zoom, wherever the planner stands. The
         * planner here puts the square's north edge across the floor, three blocks in and off both grids.
         */
        static void surveyOutlineLiesOnTerrainCells(GameTestHelper helper) {
            ZonePlannerMapDataClient.INSTANCE.clear();
            ZoneMapGeometry.clearOverlayCache();
            Floor floor = new Floor(helper);
            floor.lay();
            try {
                BlockPos planner = new BlockPos(floor.x + 5, floor.y, floor.z + 3 - ZonePlan.SURVEY_MIN_BLOCK);
                helper.assertTrue(floor.mapped(floor.x, floor.z), "baseline: the floor is mapped");

                int columns = 0; // close up: the edge's own row of blocks
                for (int x = floor.x - 64; x < floor.x + 80; x++) {
                    columns += floor.mapped(x, floor.z + 3) ? 1 : 0;
                }
                helper.assertTrue(columns >= 16, "baseline: at least the floor's 16 columns lie on the edge");
                helper.assertTrue(floor.emitOutline(planner, floor.camera(8.0, 8, 8)) == columns * VERTICES_PER_QUAD,
                        "close up, the outline is one quad per mapped column along the north edge (" + columns + ")");

                int cells = 0; // survey zoom: the 8-block terrain cells of the row holding that edge
                for (int x = floor.x - 64; x < floor.x + 80; x += 8) {
                    cells += floor.mapped(x, floor.z) ? 1 : 0;
                }
                helper.assertTrue(cells >= 2, "baseline: at least the floor's two cells lie on the edge");
                helper.assertTrue(floor.emitOutline(planner, floor.camera(ZoneMapCamera.MIN_PX_PER_BLOCK, 8, 8))
                                == cells * VERTICES_PER_QUAD,
                        "at survey zoom, one quad per mapped 8-block terrain cell along that edge (" + cells + ")");
                helper.succeed();
            } finally {
                floor.lift();
                ZoneMapGeometry.clearOverlayCache();
                ZonePlannerMapDataClient.INSTANCE.clear();
            }
        }

        static void atSurveyZoom(GameTestHelper helper) {
            ZonePlannerMapDataClient.INSTANCE.clear();
            ZoneMapGeometry.clearOverlayCache();
            Floor floor = new Floor(helper);
            floor.lay();
            try {
                ZonePlan layer = new ZonePlan();
                // Columns x 1..12, z 2..9 of the floor: with samples every 8 blocks (aligned to the world, and the floor
                // is chunk-aligned) that is the samples at x 0 and 8 by z 0 and 8, four in all.
                floor.paint(layer, 1, 2, 12, 9);
                ZoneMapCamera cam = floor.camera(ZoneMapCamera.MIN_PX_PER_BLOCK, 8, 8);

                helper.assertTrue(floor.emit(cam, layers(layer), null, -1) == 4 * VERTICES_PER_QUAD,
                        "96 painted columns are four terrain samples at survey zoom");

                // Close up, the same columns are a quad each.
                helper.assertTrue(floor.emit(floor.camera(8.0, 8, 8), layers(layer), null, -1) == 96 * VERTICES_PER_QUAD,
                        "close up, each of the 96 painted columns is its own quad");

                // A zone across the whole survey square: drawn per terrain sample, not per block. The samples that
                // carry the tint are the mapped ones (the floor's, and whatever chunks the server has loaded beside
                // it), so count those from the map itself.
                layer.paintRect(ZonePlan.SURVEY_MIN_BLOCK, ZonePlan.SURVEY_MIN_BLOCK,
                        ZonePlan.SURVEY_MAX_BLOCK, ZonePlan.SURVEY_MAX_BLOCK, true);
                int samples = 0;
                for (int ox = floor.x - 256; ox <= floor.x + 256; ox += 8) {
                    for (int oz = floor.z - 256; oz <= floor.z + 256; oz += 8) {
                        ZonePlannerMapChunk chunk = ZonePlannerMapDataClient.INSTANCE.getChunk(floor.level, ox >> 4, oz >> 4);
                        if (chunk != null && chunk.getSurfaceY(ox & 15, oz & 15) != ZonePlannerMapChunk.NO_DATA) {
                            samples++;
                        }
                    }
                }
                helper.assertTrue(samples >= 4, "baseline: at least the floor's four samples are mapped");
                int whole = floor.emit(cam, layers(layer), null, -1);
                helper.assertTrue(whole == samples * VERTICES_PER_QUAD,
                        "a layer over the whole survey square is one quad per mapped terrain sample: " + samples
                                + " samples, found " + whole + " vertices");
                helper.assertTrue(floor.emit(cam, layers(layer), null, -1) == whole, "frame after frame");
                helper.succeed();
            } finally {
                floor.lift();
                ZoneMapGeometry.clearOverlayCache();
                ZonePlannerMapDataClient.INSTANCE.clear();
            }
        }
    }
    //?}
}
