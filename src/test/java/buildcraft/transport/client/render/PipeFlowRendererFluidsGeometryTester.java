package buildcraft.transport.client.render;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;

/** Regression test for {@link PipeFlowRendererFluids#computeFaceVertices}, which
 *  encodes the geometry produced by the old {@code ModelUtil.createFace} +
 *  {@code MutableQuad.render} pipeline.
 *
 *  <p>Each face's expected vertex stream was derived by tracing
 *  {@code ModelUtil.createFace} → {@code getPointsForFace} → {@code getPoints} →
 *  {@code addOrNegate} on a unit cube at [0,1]³, then walking
 *  {@code MutableQuad.render}'s vertex_0..vertex_3 iteration order under both
 *  the inverted and non-inverted branches of {@code shouldInvertForRender}.
 *  The asserts below pin those expected values; any silent regression to the
 *  back-face winding or per-face UV mapping fails the test. */
public class PipeFlowRendererFluidsGeometryTester {

    /** Calls {@code computeFaceVertices} for a unit cube with the face-specific
     *  UV box (per {@code ModelUtil.mapBoxToUvs}) and returns the 4×5 vertex array. */
    private static float[] verticesFor(Direction face, float uMin, float vMin, float uMax, float vMax) {
        float[] out = new float[20];
        PipeFlowRendererFluids.computeFaceVertices(face,
            0, 0, 0, 1, 1, 1,
            uMin, vMin, uMax, vMax, out);
        return out;
    }

    private static void assertVertex(float[] verts, int idx,
                                       float x, float y, float z, float u, float v) {
        int base = idx * 5;
        Assertions.assertEquals(x, verts[base],     1e-5f, "vert " + idx + " x");
        Assertions.assertEquals(y, verts[base + 1], 1e-5f, "vert " + idx + " y");
        Assertions.assertEquals(z, verts[base + 2], 1e-5f, "vert " + idx + " z");
        Assertions.assertEquals(u, verts[base + 3], 1e-5f, "vert " + idx + " u");
        Assertions.assertEquals(v, verts[base + 4], 1e-5f, "vert " + idx + " v");
    }

    // For a unit cube [0,1]³ with no offset, ModelUtil.mapBoxToUvs produces these
    // (uMin, vMin, uMax, vMax) tuples — they're what the live renderer passes:
    //   UP    : (0, 1, 1, 0)  (vMin/vMax are flipped: mapBoxToUvs sets minV=maxZ, maxV=minZ)
    //   DOWN  : (0, 0, 1, 1)
    //   NORTH : (1, 0, 0, 1)  (uMin/uMax flipped: 1-minX, 1-maxX)
    //   SOUTH : (0, 0, 1, 1)
    //   WEST  : (0, 0, 1, 1)
    //   EAST  : (1, 0, 0, 1)

    @Test
    public void up_face() {
        float[] verts = verticesFor(Direction.UP, 0, 1, 1, 0);
        // emit d, c, b, a → vertex positions on the top face, UVs (uMax,vMin)(uMax,vMax)(uMin,vMax)(uMin,vMin)
        assertVertex(verts, 0, 1, 1, 1, 1, 1);
        assertVertex(verts, 1, 1, 1, 0, 1, 0);
        assertVertex(verts, 2, 0, 1, 0, 0, 0);
        assertVertex(verts, 3, 0, 1, 1, 0, 1);
    }

    @Test
    public void down_face() {
        float[] verts = verticesFor(Direction.DOWN, 0, 0, 1, 1);
        // emit a, b, c, d
        assertVertex(verts, 0, 0, 0, 1, 0, 0);
        assertVertex(verts, 1, 0, 0, 0, 0, 1);
        assertVertex(verts, 2, 1, 0, 0, 1, 1);
        assertVertex(verts, 3, 1, 0, 1, 1, 0);
    }

    @Test
    public void north_face() {
        float[] verts = verticesFor(Direction.NORTH, 1, 0, 0, 1);
        // emit d, c, b, a
        assertVertex(verts, 0, 1, 1, 0, 0, 0);
        assertVertex(verts, 1, 1, 0, 0, 0, 1);
        assertVertex(verts, 2, 0, 0, 0, 1, 1);
        assertVertex(verts, 3, 0, 1, 0, 1, 0);
    }

    @Test
    public void south_face() {
        float[] verts = verticesFor(Direction.SOUTH, 0, 0, 1, 1);
        // emit a, b, c, d
        assertVertex(verts, 0, 0, 1, 1, 0, 0);
        assertVertex(verts, 1, 0, 0, 1, 0, 1);
        assertVertex(verts, 2, 1, 0, 1, 1, 1);
        assertVertex(verts, 3, 1, 1, 1, 1, 0);
    }

    @Test
    public void west_face() {
        float[] verts = verticesFor(Direction.WEST, 0, 0, 1, 1);
        // emit a, b, c, d
        assertVertex(verts, 0, 0, 1, 0, 0, 0);
        assertVertex(verts, 1, 0, 0, 0, 0, 1);
        assertVertex(verts, 2, 0, 0, 1, 1, 1);
        assertVertex(verts, 3, 0, 1, 1, 1, 0);
    }

    @Test
    public void east_face() {
        float[] verts = verticesFor(Direction.EAST, 1, 0, 0, 1);
        // emit d, c, b, a
        assertVertex(verts, 0, 1, 1, 1, 0, 0);
        assertVertex(verts, 1, 1, 0, 1, 0, 1);
        assertVertex(verts, 2, 1, 0, 0, 1, 1);
        assertVertex(verts, 3, 1, 1, 0, 1, 0);
    }

    /** All emitted vertices for a face lie on that face's plane (i.e. share the
     *  face's normal-axis coordinate). Guards against accidentally flipping a
     *  cuboid corner across the face plane. */
    @Test
    public void vertices_lie_on_face_plane() {
        for (Direction face : Direction.values()) {
            // Use a non-degenerate UV box so any face produces the expected geometry.
            float[] verts = verticesFor(face, 0, 0, 1, 1);
            float expectedPlane = switch (face) {
                case UP, EAST, SOUTH -> 1f;
                case DOWN, WEST, NORTH -> 0f;
            };
            int axisIdx = switch (face.getAxis()) {
                case X -> 0;
                case Y -> 1;
                case Z -> 2;
            };
            for (int v = 0; v < 4; v++) {
                Assertions.assertEquals(expectedPlane, verts[v * 5 + axisIdx], 1e-5f,
                    face + " vert " + v + " not on plane");
            }
        }
    }

    /** Verifies that the two distinct vertex positions on each face axis span the
     *  full cuboid (i.e. each face shows both min and max of the two non-axis
     *  coordinates). Guards against a face accidentally collapsing along one of
     *  its in-plane axes. */
    @Test
    public void faces_span_full_box() {
        for (Direction face : Direction.values()) {
            float[] verts = verticesFor(face, 0, 0, 1, 1);
            int axisIdx = switch (face.getAxis()) {
                case X -> 0;
                case Y -> 1;
                case Z -> 2;
            };
            for (int otherAxis = 0; otherAxis < 3; otherAxis++) {
                if (otherAxis == axisIdx) continue;
                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;
                for (int v = 0; v < 4; v++) {
                    float val = verts[v * 5 + otherAxis];
                    if (val < min) min = val;
                    if (val > max) max = val;
                }
                Assertions.assertEquals(0f, min, 1e-5f, face + " axis " + otherAxis + " min");
                Assertions.assertEquals(1f, max, 1e-5f, face + " axis " + otherAxis + " max");
            }
        }
    }

    /** UV winding sanity: across the 4 emitted vertices the U coordinates span
     *  both {@code uMin} and {@code uMax}, and likewise for V. Guards against
     *  the per-face dispatch accidentally using the same UV corner twice. */
    @Test
    public void uv_winding_covers_both_corners() {
        for (Direction face : Direction.values()) {
            // Use distinct values so we can verify both ends are present.
            float[] verts = verticesFor(face, 0.1f, 0.2f, 0.9f, 0.8f);
            boolean sawUMin = false, sawUMax = false, sawVMin = false, sawVMax = false;
            float expectedUMin = face == Direction.NORTH || face == Direction.EAST ? 0.9f : 0.1f;
            float expectedUMax = face == Direction.NORTH || face == Direction.EAST ? 0.1f : 0.9f;
            float expectedVMin = face == Direction.UP ? 0.8f : 0.2f;
            float expectedVMax = face == Direction.UP ? 0.2f : 0.8f;
            for (int v = 0; v < 4; v++) {
                float u = verts[v * 5 + 3];
                float vv = verts[v * 5 + 4];
                if (Math.abs(u - expectedUMin) < 1e-5f) sawUMin = true;
                if (Math.abs(u - expectedUMax) < 1e-5f) sawUMax = true;
                if (Math.abs(vv - expectedVMin) < 1e-5f) sawVMin = true;
                if (Math.abs(vv - expectedVMax) < 1e-5f) sawVMax = true;
            }
            Assertions.assertTrue(sawUMin, face + " did not emit uMin");
            Assertions.assertTrue(sawUMax, face + " did not emit uMax");
            Assertions.assertTrue(sawVMin, face + " did not emit vMin");
            Assertions.assertTrue(sawVMax, face + " did not emit vMax");
        }
    }
}
