/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * {@link StationIndex} is a hash-map key inside {@link RobotRegistry}; these pin the equals/hashCode contract it
 * was authored to satisfy (equal iff same position and side).
 */
public class StationIndexTest {

    @Test
    public void testEqualWhenPosAndSideMatch() {
        StationIndex a = new StationIndex(Direction.NORTH, new BlockPos(1, 2, 3));
        StationIndex b = new StationIndex(Direction.NORTH, new BlockPos(1, 2, 3));

        Assertions.assertEquals(a, b, "same pos + side are equal");
        Assertions.assertEquals(a.hashCode(), b.hashCode(), "equal indices share a hash code");
    }

    @Test
    public void testDifferentSideOrPosAreNotEqual() {
        StationIndex base = new StationIndex(Direction.NORTH, new BlockPos(1, 2, 3));

        Assertions.assertNotEquals(base, new StationIndex(Direction.SOUTH, new BlockPos(1, 2, 3)),
                "differing side breaks equality");
        Assertions.assertNotEquals(base, new StationIndex(Direction.NORTH, new BlockPos(9, 2, 3)),
                "differing position breaks equality");
    }

    @Test
    public void testUsableAsMapKey() {
        Map<StationIndex, String> map = new HashMap<>();
        map.put(new StationIndex(Direction.UP, new BlockPos(4, 5, 6)), "value");

        Assertions.assertEquals("value", map.get(new StationIndex(Direction.UP, new BlockPos(4, 5, 6))),
                "a freshly built equal key retrieves the stored value");
        Assertions.assertNull(map.get(new StationIndex(Direction.DOWN, new BlockPos(4, 5, 6))),
                "a non-equal key misses");
    }
}
