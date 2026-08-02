/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.model;

import java.util.Arrays;
import java.util.Comparator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.model.MutableVertex;

/**
 * Pins the docking-station pedestal's geometry and UVs against the 1.7.10 originals.
 *
 * <p>This matters more than a normal geometry test because the baked chunk quads and the per-frame
 * state overlay come from this one function: if the two ever disagreed on a mirror or a flip, the
 * ring texture's single asymmetric texel would make the overlay visibly jump the instant a robot
 * reserved the station. Sharing the source makes that structural — and this test is what pins the
 * source itself to 7.1.x's numbers.
 *
 * <p>The expected UV rects come from {@code ModelUtil.mapBoxToUvs}, i.e. the same world-axis box
 * projection 1.7.10's {@code RenderBlocks.renderStandardBlock} applied. Several are deliberately
 * REVERSED (min &gt; max): that is how {@code mapBoxToUvs} encodes the mirror it applies on +X, +Y
 * and -Z, and flattening them to sorted ranges would silently mirror those faces.
 */
public class RobotStationModelTester {

    /** 7.1.x {@code zFightOffset}: the hull-ward extent of each box overshoots by this much. */
    private static final float ZF = 1f / 4096f;
    /** The overlay's separation from the baked pedestal it re-emits over. Kept equal to
     *  {@code PlugRobotStationRenderer.PROUD} so the test exercises the production value, though the
     *  pure function's contract holds for any offset. */
    private static final float PROUD = 1f / 256f;

    private static final float LO = 0.4325f;
    private static final float HI = 0.5675f;

    /** WEST-canonical, block units — 7.1.x's DOWN-canonical y maps to x here. */
    private static final AABB PLATE = new AABB(3 / 16f, 4 / 16f, 4 / 16f, 4 / 16f + ZF, 12 / 16f, 12 / 16f);
    private static final AABB POST = new AABB(0, LO, LO, 3 / 16f + ZF, HI, HI);

    /** Quad order is load-bearing: {@code PlugRobotStationRenderer} lights index 10 (the post's
     *  outward tip, the one face the chunk builder samples from the NEIGHBOURING block) separately
     *  from the ten interior faces. */
    private static final AABB[] EXPECTED_BOX = {
        PLATE, PLATE, PLATE, PLATE, PLATE, PLATE,
        POST, POST, POST, POST, POST,
    };
    private static final Direction[] EXPECTED_FACE = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
    };

    /** {@code { minU, minV, maxU, maxV }} in 16ths, per quad, in the order above. */
    private static final float[][] EXPECTED_UV_16 = {
        { 3, 4, 4.00390625f, 12 },              // plate DOWN — 1px rim
        { 3, 12, 4.00390625f, 4 },              // plate UP — V mirrored on +Y
        { 13, 4, 11.99609375f, 12 },            // plate NORTH — U mirrored on -Z
        { 3, 4, 4.00390625f, 12 },              // plate SOUTH
        { 4, 4, 12, 12 },                       // plate WEST — the ring, with the four state dots
        { 12, 4, 4, 12 },                       // plate EAST — same ring, U mirrored on +X
        { 0, 6.92f, 3.00390625f, 9.08f },       // post DOWN — left edge stripe
        { 0, 9.08f, 3.00390625f, 6.92f },       // post UP
        { 16, 6.92f, 12.99609375f, 9.08f },     // post NORTH — right edge stripe
        { 0, 6.92f, 3.00390625f, 9.08f },       // post SOUTH
        { 6.92f, 6.92f, 9.08f, 9.08f },         // post WEST (tip) — centre 2x2
    };

    private static final float EPS = 1e-5f;

    @Test
    public void testQuadCountAndFaces() {
        MutableQuad[] quads = RobotStationModel.buildGeometry(0f);
        Assertions.assertEquals(11, quads.length,
            "pedestal must be 11 quads: the plate's 6 faces plus the post's 5 (EAST abuts the plate)");
        for (int i = 0; i < quads.length; i++) {
            Assertions.assertEquals(EXPECTED_FACE[i], quads[i].getFace(), "quad " + i + " face");
        }
        // The post's EAST face is the one omission — everything else is a full box.
        long postEast = Arrays.stream(quads, 6, 11).filter(q -> q.getFace() == Direction.EAST).count();
        Assertions.assertEquals(0, postEast, "the post's EAST face must not be emitted (it abuts the plate)");
    }

    @Test
    public void testVertexExtentsMatchTheSevenOneBoxes() {
        MutableQuad[] quads = RobotStationModel.buildGeometry(0f);
        for (int i = 0; i < quads.length; i++) {
            float[][] expected = faceCorners(EXPECTED_BOX[i], EXPECTED_FACE[i], 0f);
            float[][] actual = corners(quads[i]);
            sortCorners(expected);
            sortCorners(actual);
            for (int c = 0; c < 4; c++) {
                for (int axis = 0; axis < 3; axis++) {
                    Assertions.assertEquals(expected[c][axis], actual[c][axis], EPS,
                        "quad " + i + " (" + EXPECTED_FACE[i] + ") corner " + c + " axis " + axis);
                }
            }
        }
    }

    @Test
    public void testUvsMatchTheBoxProjection() {
        MutableQuad[] quads = RobotStationModel.buildGeometry(0f);
        for (int i = 0; i < quads.length; i++) {
            float[] uv = readUv16(quads[i], EXPECTED_FACE[i]);
            for (int k = 0; k < 4; k++) {
                Assertions.assertEquals(EXPECTED_UV_16[i][k], uv[k], 1e-3f,
                    "quad " + i + " (" + EXPECTED_FACE[i] + ") uv[" + k + "]"
                        + " — expected " + Arrays.toString(EXPECTED_UV_16[i])
                        + ", got " + Arrays.toString(uv));
            }
        }
    }

    @Test
    public void testProudMovesGeometryOnlyAndKeepsUvs() {
        MutableQuad[] flat = RobotStationModel.buildGeometry(0f);
        MutableQuad[] proud = RobotStationModel.buildGeometry(PROUD);
        Assertions.assertEquals(flat.length, proud.length, "proud offset must not change the quad count");

        for (int i = 0; i < flat.length; i++) {
            Direction face = EXPECTED_FACE[i];
            float[][] before = corners(flat[i]);
            float[][] after = corners(proud[i]);
            sortCorners(before);
            sortCorners(after);
            for (int c = 0; c < 4; c++) {
                float dx = after[c][0] - before[c][0];
                float dy = after[c][1] - before[c][1];
                float dz = after[c][2] - before[c][2];
                // Expanding the radius moves every corner outward by `proud` on all three axes...
                Assertions.assertEquals(PROUD, Math.abs(dx), EPS, "quad " + i + " corner " + c + " |dx|");
                Assertions.assertEquals(PROUD, Math.abs(dy), EPS, "quad " + i + " corner " + c + " |dy|");
                Assertions.assertEquals(PROUD, Math.abs(dz), EPS, "quad " + i + " corner " + c + " |dz|");
                // ...and the component along the face normal is outward, which is what separates the
                // overlay from the baked quad underneath it.
                float alongNormal = dx * face.getStepX() + dy * face.getStepY() + dz * face.getStepZ();
                Assertions.assertEquals(PROUD, alongNormal, EPS,
                    "quad " + i + " (" + face + ") corner " + c + " must move outward along its normal");
            }

            float[] uvBefore = readUv16(flat[i], face);
            float[] uvAfter = readUv16(proud[i], face);
            for (int k = 0; k < 4; k++) {
                Assertions.assertEquals(uvBefore[k], uvAfter[k], 1e-6f,
                    "quad " + i + " uv[" + k + "] must be projected from the UNexpanded box");
            }
        }
    }

    @Test
    public void testNormalsAreTrueFaceNormals() {
        // multShade() reads the normal, so the pure geometry has to carry the real outward normal —
        // flattening it to straight-up is the overlay's LAST step, never the builder's.
        MutableQuad[] quads = RobotStationModel.buildGeometry(0f);
        for (int i = 0; i < quads.length; i++) {
            Direction face = EXPECTED_FACE[i];
            for (MutableVertex v : vertices(quads[i])) {
                Assertions.assertEquals(face.getStepX(), v.normal_x, EPS, "quad " + i + " normal x");
                Assertions.assertEquals(face.getStepY(), v.normal_y, EPS, "quad " + i + " normal y");
                Assertions.assertEquals(face.getStepZ(), v.normal_z, EPS, "quad " + i + " normal z");
            }
        }
    }

    // ############################
    //
    // Helpers
    //
    // ############################

    private static MutableVertex[] vertices(MutableQuad q) {
        return new MutableVertex[] { q.vertex_0, q.vertex_1, q.vertex_2, q.vertex_3 };
    }

    private static float[][] corners(MutableQuad q) {
        MutableVertex[] vs = vertices(q);
        float[][] out = new float[4][3];
        for (int i = 0; i < 4; i++) {
            out[i][0] = vs[i].position_x;
            out[i][1] = vs[i].position_y;
            out[i][2] = vs[i].position_z;
        }
        return out;
    }

    private static void sortCorners(float[][] corners) {
        Arrays.sort(corners, Comparator.<float[]>comparingDouble(c -> c[0])
            .thenComparingDouble(c -> c[1])
            .thenComparingDouble(c -> c[2]));
    }

    /** The four corners of {@code box}'s {@code face}, expanded outward on every axis by {@code proud}. */
    private static float[][] faceCorners(AABB box, Direction face, float proud) {
        float minX = (float) box.minX - proud;
        float minY = (float) box.minY - proud;
        float minZ = (float) box.minZ - proud;
        float maxX = (float) box.maxX + proud;
        float maxY = (float) box.maxY + proud;
        float maxZ = (float) box.maxZ + proud;
        float[][] out = new float[4][3];
        int n = 0;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                switch (face.getAxis()) {
                    case X -> out[n] = new float[] {
                        face == Direction.WEST ? minX : maxX, a == 0 ? minY : maxY, b == 0 ? minZ : maxZ };
                    case Y -> out[n] = new float[] {
                        a == 0 ? minX : maxX, face == Direction.DOWN ? minY : maxY, b == 0 ? minZ : maxZ };
                    case Z -> out[n] = new float[] {
                        a == 0 ? minX : maxX, b == 0 ? minY : maxY, face == Direction.NORTH ? minZ : maxZ };
                }
                n++;
            }
        }
        return out;
    }

    /** Recovers {@code { minU, minV, maxU, maxV }} in 16ths from a built quad.
     *
     *  <p>{@code ModelUtil.createFace} hands minU to the corner lowest on the face's U axis and minV to
     *  the corner HIGHEST on its V axis (V runs downward in texture space) — uniformly on all six
     *  faces. Reading the rect back through that mapping rather than through {@code Math.min} is what
     *  lets a reversed range survive, which is the whole point of the check. */
    private static float[] readUv16(MutableQuad q, Direction face) {
        Direction.Axis uAxis = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction.Axis vAxis = face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
        MutableVertex[] vs = vertices(q);
        float uLo = Float.MAX_VALUE;
        float uHi = -Float.MAX_VALUE;
        float vLo = Float.MAX_VALUE;
        float vHi = -Float.MAX_VALUE;
        for (MutableVertex v : vs) {
            uLo = Math.min(uLo, along(v, uAxis));
            uHi = Math.max(uHi, along(v, uAxis));
            vLo = Math.min(vLo, along(v, vAxis));
            vHi = Math.max(vHi, along(v, vAxis));
        }
        MutableVertex minCorner = pick(vs, uAxis, uLo, vAxis, vHi);
        MutableVertex maxCorner = pick(vs, uAxis, uHi, vAxis, vLo);
        return new float[] {
            minCorner.tex_u * 16f, minCorner.tex_v * 16f,
            maxCorner.tex_u * 16f, maxCorner.tex_v * 16f,
        };
    }

    private static MutableVertex pick(MutableVertex[] vs, Direction.Axis uAxis, float u,
                                       Direction.Axis vAxis, float v) {
        for (MutableVertex vertex : vs) {
            if (Math.abs(along(vertex, uAxis) - u) < EPS && Math.abs(along(vertex, vAxis) - v) < EPS) {
                return vertex;
            }
        }
        throw new AssertionError("no quad corner at " + uAxis + "=" + u + ", " + vAxis + "=" + v);
    }

    private static float along(MutableVertex v, Direction.Axis axis) {
        return switch (axis) {
            case X -> v.position_x;
            case Y -> v.position_y;
            case Z -> v.position_z;
        };
    }
}
