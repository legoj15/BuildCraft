/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.StatementSlot;

/** {@link AIRobotSearchAndGotoStation}'s {@code tryEveryStation} mode: a failed flight moves on to the next-nearest
 *  matching station until one succeeds or none is left; without it the composition still fails on the first failed
 *  flight (the load/unload searches keep 7.1.x's nearest-only behaviour). The flight is stood in for by a goto that
 *  succeeds only for the stations it is told are reachable. */
public class AIRobotSearchAndGotoStationTest {

    private static final DockingStation NEAR = station(new BlockPos(10, 64, 0));
    private static final DockingStation MIDDLE = station(new BlockPos(20, 64, 0));
    private static final DockingStation FAR = station(new BlockPos(40, 64, 0));

    private final MockRobotAccess robot = new MockRobotAccess();
    private final List<DockingStation> flights = new ArrayList<>();

    {
        robot.setPosition(new Vec3(0.5, 64.5, 0.5));
        robot.setRegistry(new MockRobotAccess.InertRobotRegistry() {
            @Override
            public Collection<DockingStation> getStations() {
                return List.of(FAR, NEAR, MIDDLE);
            }
        });
    }

    @Test
    public void theFirstReachableStationInOrderOfDistanceWins() {
        Tracked ai = new Tracked(robot, true, s -> s == MIDDLE);

        run(ai);

        Assertions.assertEquals(List.of(NEAR, MIDDLE), flights, "nearest first, and no further once one is reached");
        Assertions.assertTrue(ai.success());
    }

    @Test
    public void withNoneReachableItFailsAfterTryingEach() {
        Tracked ai = new Tracked(robot, true, s -> false);

        run(ai);

        Assertions.assertEquals(List.of(NEAR, MIDDLE, FAR), flights, "each matching station is tried exactly once");
        Assertions.assertFalse(ai.success());
    }

    @Test
    public void anExcludedStationIsNeverTried() {
        Tracked ai = new Tracked(robot, true, s -> s == FAR);
        ai.exclude(NEAR);

        run(ai);

        Assertions.assertEquals(List.of(MIDDLE, FAR), flights);
        Assertions.assertTrue(ai.success());
    }

    @Test
    public void withoutTryEveryStationTheFirstFailureEndsIt() {
        Tracked ai = new Tracked(robot, false, s -> s == MIDDLE);

        run(ai);

        Assertions.assertEquals(List.of(NEAR), flights, "the nearest match only, as 7.1.x");
        Assertions.assertFalse(ai.success());
    }

    private void run(Tracked ai) {
        Parent parent = new Parent(robot);
        parent.startDelegateAI(ai);
        Assertions.assertSame(ai, parent.ended, "the whole search ends within start: every leg here is instant");
    }

    private static DockingStation station(BlockPos pos) {
        return new DockingStation(pos, Direction.UP) {
            @Override
            public Iterable<StatementSlot> getActiveActions() {
                return Collections.emptyList();
            }
        };
    }

    private final class Tracked extends AIRobotSearchAndGotoStation {
        private final Predicate<DockingStation> reachable;

        Tracked(IRobotAccess robot, boolean tryEvery, Predicate<DockingStation> reachable) {
            super(robot, s -> true, null, tryEvery);
            this.reachable = reachable;
        }

        @Override
        AIRobotGotoStation gotoStation(DockingStation station) {
            flights.add(station);
            boolean ok = reachable.test(station);
            return new AIRobotGotoStation(robot, station) {
                @Override
                public void start() {
                    setSuccess(ok);
                    terminate();
                }
            };
        }
    }

    private static final class Parent extends AIRobot {
        AIRobot ended;

        Parent(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            ended = ai;
        }
    }
}
