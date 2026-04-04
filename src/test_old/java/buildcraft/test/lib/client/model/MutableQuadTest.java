package buildcraft.test.lib.client.model;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import buildcraft.lib.client.model.MutableQuad;

public class MutableQuadTest {

    @Test
    public void testRotations() {
        for (Direction from : Direction.values()) {
            for (Direction to : Direction.values()) {
                Vec3i vec = from.getUnitVec3i();
                MutableQuad q = new MutableQuad();
                q.vertex_0.positionf(vec.getX(), vec.getY(), vec.getZ());
                q.rotate(from, to, 0, 0, 0);
                float ex = to.getStepX();
                float ey = to.getStepY();
                float ez = to.getStepZ();

                Assert.assertEquals(from + " -> " + to + " [X]", ex, q.vertex_0.position_x, 0.001f);
                Assert.assertEquals(from + " -> " + to + " [Y]", ey, q.vertex_0.position_y, 0.001f);
                Assert.assertEquals(from + " -> " + to + " [Z]", ez, q.vertex_0.position_z, 0.001f);
            }
        }
    }
}
