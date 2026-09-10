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

    /** A gate slot carrying {@code action} with {@code stacks} as its item-stack parameters. */
    private static StatementSlot gateSlot(buildcraft.api.statements.IStatement action, ItemStack... stacks) {
        StatementSlot slot = new StatementSlot();
        slot.statement = action;
        buildcraft.api.statements.IStatementParameter[] params =
                new buildcraft.api.statements.IStatementParameter[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            params[i] = new buildcraft.api.statements.StatementParameterItemStack(stacks[i]);
        }
        slot.parameters = params;
        return slot;
    }

    @Test
    public void theToolGateFilterNarrowsWhatIsEquipped() {
        SupplyStation station = new SupplyStation();
        station.input.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        station.input.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        // "Filter Tool: iron pickaxe" — the station's standing instruction about which tool to carry.
        station.actions = List.of(gateSlot(
                new buildcraft.robotics.statements.ActionRobotFilterTool(),
                new ItemStack(Items.IRON_PICKAXE)));

        LinkedRobot robot = new LinkedRobot();
        robot.setDockingStation(station);
        robot.linked = station;

        // The board's own predicate: any pickaxe will do. The gate narrows it to the iron one.
        AIRobotFetchAndEquipItemStack ai = new AIRobotFetchAndEquipItemStack(robot,
                stack -> stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.IRON_PICKAXE));
        runUntilEquipped(ai, robot);

        Assertions.assertEquals(Items.IRON_PICKAXE, robot.getHeldItem().getItem(),
                "the Filter Tool action must be AND-ed into the board's tool predicate — the station said "
                        + "iron pickaxes, so the diamond one must be left alone");
        Assertions.assertFalse(station.input.getItem(0).isEmpty(),
                "the diamond pickaxe the gate excluded stays in the station");
    }

    @Test
    public void anUnsetToolGateFilterPassesEverything() {
        SupplyStation station = new SupplyStation();
        station.input.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));

        LinkedRobot robot = new LinkedRobot();
        robot.setDockingStation(station);
        robot.linked = station; // linked, but its gate holds no Filter Tool action

        AIRobotFetchAndEquipItemStack ai = new AIRobotFetchAndEquipItemStack(robot,
                stack -> stack.is(Items.DIAMOND_PICKAXE));
        runUntilEquipped(ai, robot);

        Assertions.assertEquals(Items.DIAMOND_PICKAXE, robot.getHeldItem().getItem(),
                "a station with no Filter Tool action restricts nothing");
    }

    @Test
    public void noLinkedStationIsNotACrash() {
        SupplyStation station = new SupplyStation();
        station.input.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));

        LinkedRobot robot = new LinkedRobot();
        robot.setDockingStation(station);
        robot.linked = null; // a robot mid-flight, or one that lost its home

        AIRobotFetchAndEquipItemStack ai = new AIRobotFetchAndEquipItemStack(robot,
                stack -> stack.is(Items.DIAMOND_PICKAXE));
        runUntilEquipped(ai, robot);

        Assertions.assertEquals(Items.DIAMOND_PICKAXE, robot.getHeldItem().getItem(),
                "a robot with no linked station must still be able to equip — the gate filter is simply "
                        + "not consulted");
    }
}
