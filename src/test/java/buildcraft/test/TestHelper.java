package buildcraft.test;

import org.junit.jupiter.api.Assertions;

import net.minecraft.world.phys.Vec3;

public class TestHelper {
    public static void assertVec3Equals(Vec3 expected, Vec3 centerExact2) {
        if (expected.distanceTo(centerExact2) > 1e-12) {
            Assertions.fail(centerExact2 + " was not equal to expected " + expected);
        }
    }
}
