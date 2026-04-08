package buildcraft.core;

import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.misc.data.Box;
import buildcraft.TestHelper;

public class BoxTester {
    private static final BlockPos MIN = new BlockPos(1, 2, 3), MAX = new BlockPos(4, 5, 6);
    private static final BlockPos SIZE = new BlockPos(4, 4, 4);
    private static final BlockPos CENTER = new BlockPos(3, 4, 5);
    private static final Vec3 CENTER_EXACT = new Vec3(3, 4, 5);

    public static Stream<Entry<Vec3, Boolean>> dataContainsVec3() {
        return Stream.of(
            new AbstractMap.SimpleEntry<>(new Vec3(0, 0, 0), false),
            new AbstractMap.SimpleEntry<>(new Vec3(1, 2, 3), true),
            new AbstractMap.SimpleEntry<>(new Vec3(1.3, 2.4, 3.5), true),
            new AbstractMap.SimpleEntry<>(new Vec3(4.9, 5.9, 6.9), true),
            new AbstractMap.SimpleEntry<>(new Vec3(5, 5, 6), false)
        );
    }

    @ParameterizedTest
    @MethodSource("dataContainsVec3")
    public void testContainsVec3(Entry<Vec3, Boolean> entry) {
        Box box = new Box(MIN, MAX);
        Vec3 in = entry.getKey();
        boolean expected = entry.getValue();
        Assertions.assertEquals(expected, box.contains(in));
    }

    @Test
    public void testMin() {
        Box box = new Box(MIN, MAX);
        Assertions.assertEquals(MIN, box.min());
    }

    @Test
    public void testMax() {
        Box box = new Box(MIN, MAX);
        Assertions.assertEquals(MAX, box.max());
    }

    @Test
    public void testSize() {
        Box box = new Box(MIN, MAX);
        Assertions.assertEquals(SIZE, box.size());
    }

    @Test
    public void testCenter() {
        Box box = new Box(MIN, MAX);
        Assertions.assertEquals(CENTER, box.center());
    }

    @Test
    public void testCenterExact() {
        Box box = new Box(MIN, MAX);
        TestHelper.assertVec3Equals(CENTER_EXACT, box.centerExact());
    }

    @Test
    public void testIntersection1() {
        Box box1 = new Box(new BlockPos(0, 0, 0), new BlockPos(2, 2, 2));
        Box box2 = new Box(new BlockPos(1, 1, 1), new BlockPos(3, 3, 3));
        Box inter = new Box(new BlockPos(1, 1, 1), new BlockPos(2, 2, 2));
        Assertions.assertEquals(inter, box1.getIntersect(box2));
        Assertions.assertEquals(inter, box2.getIntersect(box1));
    }

    @Test
    public void testIntersection2() {
        Box box1 = new Box(new BlockPos(0, 0, 0), new BlockPos(2, 2, 2));
        Box box2 = new Box(new BlockPos(0, 0, 0), new BlockPos(3, 3, 3));
        Box inter = new Box(new BlockPos(0, 0, 0), new BlockPos(2, 2, 2));
        Assertions.assertEquals(inter, box1.getIntersect(box2));
        Assertions.assertEquals(inter, box2.getIntersect(box1));
    }

    @Test
    public void testIntersection3() {
        Box box1 = new Box(new BlockPos(1, 1, 1), new BlockPos(2, 2, 2));
        Box box2 = new Box(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));
        Box inter = new Box(new BlockPos(1, 1, 1), new BlockPos(1, 1, 1));
        Assertions.assertEquals(inter, box1.getIntersect(box2));
        Assertions.assertEquals(inter, box2.getIntersect(box1));
    }
}
