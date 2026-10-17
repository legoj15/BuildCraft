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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.StatementSlot;

/** A robot that cannot reach the nearest powered station must try the next one. A goto is capped (the A* weight prune
 *  and the expansion budget keep a path within ~96 blocks), so a robot that wandered further than that from the
 *  nearest powered station used to fail its recharge outright and shut down where it stood, even with another
 *  powered station in reach; going to sleep likewise gave up when home was out of reach. Both now try the powered
 *  stations in order of distance until one flight succeeds.
 *
 *  <p>Every goto here fails at once: the registry resolves no station ({@code getStation} is null, so
 *  {@link AIRobotGotoStation} ends unsuccessful in {@code start()}), and records each station asked for, so the
 *  order of the attempts is what is observed. */
public class AIRobotStationFallbackTest {

    private static final BlockPos HOME = new BlockPos(5, 64, 0);
    private static final BlockPos NEAR = new BlockPos(10, 64, 0);
    private static final BlockPos FAR = new BlockPos(40, 64, 0);

    private final MockRobotAccess robot = new MockRobotAccess();
    private final List<BlockPos> asked = new ArrayList<>();
    private final List<DockingStation> stations = List.of(new Station(FAR, true), new Station(NEAR, true));

    {
        robot.setPosition(new Vec3(0.5, 64.5, 0.5));
        robot.setRegistry(new MockRobotAccess.InertRobotRegistry() {
            @Override
            public Collection<DockingStation> getStations() {
                return stations;
            }

            @Override
            public DockingStation getStation(BlockPos pos, Direction side) {
                asked.add(pos);
                return null; // unreachable: the goto fails
            }
        });
    }

    @Test
    public void rechargeTriesTheNextPoweredStationWhenTheNearestCannotBeReached() {
        Parent parent = new Parent(robot);
        AIRobotRecharge recharge = new AIRobotRecharge(robot);

        parent.startDelegateAI(recharge);

        Assertions.assertEquals(List.of(NEAR, FAR), asked,
                "the recharge tries every powered station, nearest first, until one flight succeeds");
        Assertions.assertSame(recharge, parent.ended, "with none reachable it gives up");
        Assertions.assertFalse(recharge.success());
    }

    @Test
    public void goingToSleepFallsBackToThePoweredStationsWhenHomeCannotBeReached() {
        robot.setLinkedStation(new Station(HOME, false));
        Parent parent = new Parent(robot);

        parent.startDelegateAI(new AIRobotGotoSleep(robot));

        Assertions.assertEquals(List.of(HOME, NEAR, FAR), asked,
                "home first, then every powered station nearest first");
        Assertions.assertTrue(parent.getActiveAI() instanceof AIRobotSleep,
                "with none reachable the robot still sleeps where it is, got " + parent.getActiveAI());
    }

    @Test
    public void aRobotWithNoHomeStillSleepsWhereItIs() {
        Parent parent = new Parent(robot);

        parent.startDelegateAI(new AIRobotGotoSleep(robot));

        Assertions.assertTrue(asked.isEmpty(), "a homeless robot does not go looking for stations to sleep at");
        Assertions.assertTrue(parent.getActiveAI() instanceof AIRobotSleep, "it sleeps in place, as in 7.1.x");
    }

    private static final class Station extends DockingStation {
        private final boolean powered;

        Station(BlockPos pos, boolean powered) {
            super(pos, Direction.UP);
            this.powered = powered;
        }

        @Override
        public boolean providesPower() {
            return powered;
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
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
