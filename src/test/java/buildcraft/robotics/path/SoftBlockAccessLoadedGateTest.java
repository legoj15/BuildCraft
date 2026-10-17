/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** The production {@link SoftBlockAccess} must never read a block whose chunk is not loaded: on a server, reading
 *  one ({@code Level.getBlockState}) loads - or generates - that chunk synchronously on the server thread, and a
 *  robot near the edge of view distance would do that every time its search or path touched the boundary. The
 *  gate is {@link SoftBlockAccess#loadedOnly}, the composition {@link SoftBlockAccess#of} is built from; here the
 *  block read is a recording double, so a read of an unloaded cell is seen directly. Chunk x=0 is loaded, x=1
 *  (block x 16 and up) is not. */
public class SoftBlockAccessLoadedGateTest {

    private static final int Y = 64;

    private final List<BlockPos> reads = new ArrayList<>();
    private final Set<BlockPos> solid = new HashSet<>();
    /** Everything on the y=64 layer is open air, loaded or not, except the {@link #solid} cells: only the gate can
     *  keep the robot out of x >= 16. */
    private final Predicate<BlockPos> airLayer = pos -> {
        reads.add(pos);
        return pos.getY() == Y && !solid.contains(pos);
    };
    private final SoftBlockAccess access = SoftBlockAccess.loadedOnly(pos -> pos.getX() < 16, airLayer);

    private void assertNoUnloadedRead() {
        for (BlockPos pos : reads) {
            Assertions.assertTrue(pos.getX() < 16, "a block in an unloaded chunk was read: " + pos);
        }
    }

    @Test
    public void anUnloadedCellIsNotSoftAndIsNeverRead() {
        Assertions.assertFalse(access.isSoft(new BlockPos(16, Y, 0)), "an unloaded cell is not somewhere to fly");
        Assertions.assertFalse(access.isChunkLoaded(new BlockPos(16, Y, 0)));
        Assertions.assertTrue(access.isSoft(new BlockPos(15, Y, 0)), "a loaded air cell still is");
        assertNoUnloadedRead();
    }

    @Test
    public void aPathIntoAnUnloadedChunkFailsWithoutReadingIt() {
        PathFinding path = new PathFinding(access, new BlockPos(12, Y, 8), new BlockPos(20, Y, 8), 0, 64f);
        for (int i = 0; i < 100 && !path.isDone(); i++) {
            path.iterate();
        }

        Assertions.assertTrue(path.isDone());
        Assertions.assertTrue(path.getResult().isEmpty(), "the target lies past the loaded edge: no path");
        assertNoUnloadedRead();
    }

    @Test
    public void aSearchTargetOnTheLoadedEdgeDoesNotReadItsUnloadedNeighbour() {
        BlockPos target = new BlockPos(15, Y, 8);
        // Walled in on every loaded side: the only open neighbour it could have lies in the unloaded chunk.
        solid.add(target.west());
        solid.add(target.north());
        solid.add(target.south());
        PathFindingSearch search = new PathFindingSearch(access, new BlockPos(10, Y, 8),
                List.of(target.subtract(new BlockPos(10, Y, 8))).iterator(), target::equals, 0, 64f, null,
                new HashSet<>());
        for (int i = 0; i < 100 && !search.isDone(); i++) {
            search.iterate();
        }

        Assertions.assertNull(search.getResultTarget(),
                "a target reachable only through an unloaded chunk is not a target");
        assertNoUnloadedRead();
    }
}
