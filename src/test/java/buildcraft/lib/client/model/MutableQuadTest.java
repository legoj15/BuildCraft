package buildcraft.lib.client.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import buildcraft.lib.client.model.MutableQuad;

public class MutableQuadTest {

    @Test
    public void testRotations() {
        for (Direction from : Direction.values()) {
            for (Direction to : Direction.values()) {
                //? if >=1.21.10 {
                Vec3i vec = from.getUnitVec3i();
                //?} else {
                /*Vec3i vec = from.getNormal();*/
                //?}
                MutableQuad q = new MutableQuad();
                q.vertex_0.positionf(vec.getX(), vec.getY(), vec.getZ());
                q.rotate(from, to, 0, 0, 0);
                float ex = to.getStepX();
                float ey = to.getStepY();
                float ez = to.getStepZ();

                Assertions.assertEquals(ex, q.vertex_0.position_x, 0.001f, from + " -> " + to + " [X]");
                Assertions.assertEquals(ey, q.vertex_0.position_y, 0.001f, from + " -> " + to + " [Y]");
                Assertions.assertEquals(ez, q.vertex_0.position_z, 0.001f, from + " -> " + to + " [Z]");
            }
        }
    }

    /** Per-vertex colour survives the bake: every node carries it differently (a loader's ARGB BakedColors, or the
     *  R-low colour int of the 1.21.1/1.21.10 vertex array), and a channel mix-up swaps red and blue on the tinted
     *  lens glass in the snapshot preview without failing any compile. Distinct R/G/B/A per vertex pins each one. */
    @Test
    public void testBakedColoursRoundTrip() {
        MutableQuad q = new MutableQuad(-1, Direction.UP);
        q.vertex_0.colouri(0x11, 0x22, 0x33, 0x44);
        q.vertex_1.colouri(0xA1, 0xB2, 0xC3, 0xD4);
        q.vertex_2.colouri(0x3F, 0x76, 0xE4, 0xFF);
        q.vertex_3.colouri(0xFE, 0x01, 0x80, 0x7F);

        MutableQuad back = new MutableQuad().fromBakedColours(q.toBakedBlock());

        MutableVertex[] want = {q.vertex_0, q.vertex_1, q.vertex_2, q.vertex_3};
        MutableVertex[] got = {back.vertex_0, back.vertex_1, back.vertex_2, back.vertex_3};
        for (int i = 0; i < 4; i++) {
            Assertions.assertEquals(want[i].colour_r, got[i].colour_r, "vertex " + i + " [R]");
            Assertions.assertEquals(want[i].colour_g, got[i].colour_g, "vertex " + i + " [G]");
            Assertions.assertEquals(want[i].colour_b, got[i].colour_b, "vertex " + i + " [B]");
            Assertions.assertEquals(want[i].colour_a, got[i].colour_a, "vertex " + i + " [A]");
        }
    }
}
