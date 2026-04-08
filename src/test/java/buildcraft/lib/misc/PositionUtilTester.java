package buildcraft.lib.misc;

import java.util.HashSet;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.core.BlockPos;

import buildcraft.lib.misc.PositionUtil;

public class PositionUtilTester {

    public static Stream<Arguments> paths() {
        return Stream.of(
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 0, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 1, 0), new BlockPos(0, 0, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(1, 4, 6) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(1, 0, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(1, 1, 1) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(-50, 45, 34), new BlockPos(-37, 7, -40) })
        );
    }

    public static Stream<Arguments> boxes() {
        return Stream.of(
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 0, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(1, 0, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 1, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 0, 1) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(2, 0, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 2, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 0, 2) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(3, 0, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 3, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 0, 3) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(3, 3, 0) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(3, 0, 3) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(0, 0, 0), new BlockPos(0, 3, 3) }),
            Arguments.of((Object) new BlockPos[]{ new BlockPos(-45, 3, -4), new BlockPos(-38, 16, 16) })
        );
    }

    @ParameterizedTest
    @MethodSource("paths")
    public void testNormalDataPoint(BlockPos[] path) {
        BlockPos from = path[0];
        BlockPos to = path[1];
        System.out.println("All from " + from + " to " + to);
        for (BlockPos p : PositionUtil.getAllOnPath(from, to)) {
            System.out.println("  - " + p);
        }
    }

    @ParameterizedTest
    @MethodSource("boxes")
    public void testBounds(BlockPos[] box) {
        BlockPos min = box[0];
        BlockPos max = box[1];
        System.out.println("Box = [ " + min + " -> " + max + " ]");
        ImmutableList<BlockPos> allOnEdge = PositionUtil.getAllOnEdge(min, max);
        String info = "\nmin = " + min + ",\nmax = " + max + ",\nonEdge = \n"
            + allOnEdge.toString().replace("}, Block", "},\n Block");

        // Ensure that the returned list has no duplicates
        Assertions.assertEquals(new HashSet<>(allOnEdge).size(), allOnEdge.size(), "Duplicates! " + info);
        Assertions.assertEquals(allOnEdge.size(), PositionUtil.getCountOnEdge(min, max), "Count! " + info);

        // Ensure that all of them are valid edges and faces
        for (BlockPos p : allOnEdge) {
            String info2 = "pos = " + p + ", " + info;
            Assertions.assertTrue(PositionUtil.isOnEdge(min, max, p), "isOnEdge mismatch! " + info2);
            Assertions.assertTrue(PositionUtil.isOnFace(min, max, p), "isOnFace mismatch! " + info2);
        }

        // Construct it manually via PositionUtil.isOnEdge
        BlockPos minSub = min.offset(-1, -1, -1);
        BlockPos maxAdd = max.offset(1, 1, 1);
        for (BlockPos p : BlockPos.betweenClosed(minSub, maxAdd)) {
            if (PositionUtil.isOnEdge(min, max, p)) {
                Assertions.assertTrue(allOnEdge.contains(p), "All In Box");
            }
        }
    }
}
