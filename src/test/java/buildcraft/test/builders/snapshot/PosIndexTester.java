package buildcraft.test.builders.snapshot;

import java.util.List;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.core.BlockPos;

import buildcraft.builders.snapshot.Snapshot;

public class PosIndexTester {
    private static final BlockPos SIZE = new BlockPos(6, 4, 8);

    public static Stream<BlockPos> positions() {
        ImmutableList.Builder<BlockPos> builder = new ImmutableList.Builder<>();
        for (int z = 0; z < SIZE.getZ(); z++) {
            for (int y = 0; y < SIZE.getY(); y++) {
                for (int x = 0; x < SIZE.getX(); x++) {
                    builder.add(new BlockPos(x, y, z));
                }
            }
        }
        return builder.build().stream();
    }

    @ParameterizedTest
    @MethodSource("positions")
    public void test(BlockPos pos) {
        System.out.println("Testing " + pos + " with size " + SIZE);
        Assertions.assertEquals(
            pos,
            Snapshot.indexToPos(SIZE, Snapshot.posToIndex(SIZE, pos)),
            Integer.toString(Snapshot.posToIndex(SIZE, pos))
        );
    }
}
