/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;

/** {@link AIRobotFetchAndEquipItemStack}: what a robot ends up HOLDING after a trip to its station.
 *
 *  <p>7.1.x equipped exactly ONE item ({@code AIRobotLoad.takeSingle}, {@code decreaseStackInSlot(1)}) and
 *  the AIs downstream assume it — {@code AIRobotPlant} plants one seed and DROPS whatever is left in the
 *  hand, so equipping a whole stack means a planter spills 63 seeds on the ground every cycle. */
public class AIRobotFetchAndEquipItemStackTest extends VanillaSetupBaseTester {

    /** A station with a real 9-slot input, and a settable active-action list. */
    static class SupplyStation extends DockingStation {
        final SimpleContainer input = new SimpleContainer(9);
        List<StatementSlot> actions = Collections.emptyList();

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return actions;
        }

        @Override
        public Container getItemInput() {
            return input;
        }
    }

    /** A robot whose LINKED station can be set independently of the docking station. */
    static class LinkedRobot extends MockRobotAccess {
        DockingStation linked;

        @Override
        public DockingStation getLinkedStation() {
            return linked;
        }
    }

    /** Runs the AI's update loop past its 40-tick patience until it equips something (or gives up). */
    static void runUntilEquipped(AIRobotFetchAndEquipItemStack ai, LinkedRobot robot) {
        for (int tick = 0; tick < 80 && robot.getHeldItem().isEmpty(); tick++) {
            ai.update();
        }
    }

    @Test
    public void equipTakesExactlyOneItem() {
        SupplyStation station = new SupplyStation();
        station.input.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 64));

        LinkedRobot robot = new LinkedRobot();
        robot.setDockingStation(station);

        AIRobotFetchAndEquipItemStack ai = new AIRobotFetchAndEquipItemStack(robot, stack -> true);
        runUntilEquipped(ai, robot);

        Assertions.assertEquals(1, robot.getHeldItem().getCount(),
                "7.1.x's takeSingle equipped ONE item — a planter plants one seed and drops the rest of "
                        + "its hand, so a whole stack in hand means 63 seeds spilled per cycle");
        Assertions.assertEquals(63, station.input.getItem(0).getCount(),
                "the station keeps everything the robot did not take");
    }
}
