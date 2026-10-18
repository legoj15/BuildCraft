/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.time.Duration;
import java.util.Collections;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;
import buildcraft.lib.inventory.InventoryWrapper;

/** {@link AIRobotLoad} against a station whose {@code canRobotExtractItem} policy refuses stacks (the D1 seam
 *  a Ph6 gate action will drive). A refused stack must be EXCLUDED from the remaining scan — re-extracting the
 *  same refused stack forever is a live-lock (the AI's 40-tick patience never runs out, because
 *  {@code load(...)} itself never returns). The preemptive timeouts are the red-state guards: the buggy shape
 *  fails by HANGING, not by a wrong return value. */
public class AIRobotLoadTest extends VanillaSetupBaseTester {

    /** A station with a real 9-slot input and a settable extract policy. */
    private static class PolicyStation extends DockingStation {
        final SimpleContainer input = new SimpleContainer(9);
        Predicate<ItemStack> policy = stack -> true;

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public Container getItemInput() {
            return input;
        }

        @Override
        public boolean canRobotExtractItem(ItemStack stack) {
            return policy.test(stack);
        }
    }

    /** A mock robot whose transactor fronts a real four-slot container, so what it loaded is observable. */
    private static class LoadingRobot extends MockRobotAccess {
        final SimpleContainer inv = new SimpleContainer(4);
        private final IItemTransactor transactor = new InventoryWrapper(inv);

        @Override
        public IItemTransactor getTransactor() {
            return transactor;
        }
    }

    @Test
    public void refusedStackIsSkippedAndTheAcceptableOneLoads() {
        PolicyStation station = new PolicyStation();
        station.input.setItem(0, new ItemStack(Items.STONE, 64));
        station.input.setItem(1, new ItemStack(Items.DIRT, 10));
        // A Ph6-style gate that forbids stone: the scan must move past it, not grind on it.
        station.policy = stack -> !stack.is(Items.STONE);

        LoadingRobot robot = new LoadingRobot();

        Boolean loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoad.load(robot, station, stack -> true, 5, true),
                "a refused stack must be excluded from the scan — re-requesting it is an infinite loop");

        Assertions.assertTrue(loaded, "with stone refused, the load must still find and take the 5 dirt");
        Assertions.assertEquals(64, station.input.getItem(0).getCount(),
                "the refused stone must be back in the station, untouched");
        Assertions.assertEquals(5, station.input.getItem(1).getCount(),
                "the station keeps the 5 dirt the robot did not ask for");
        Assertions.assertTrue(ItemStack.matches(robot.inv.getItem(0), new ItemStack(Items.DIRT, 5)),
                "the robot carries exactly the 5 dirt: 74 items in play, 64 + 5 + 5 accounted for");
    }

    @Test
    public void refusingEverythingTerminatesAndConservesTheWholeInventory() {
        PolicyStation station = new PolicyStation();
        station.input.setItem(0, new ItemStack(Items.STONE, 64));
        station.input.setItem(1, new ItemStack(Items.DIRT, 10));
        station.policy = stack -> false; // forbids the world

        LoadingRobot robot = new LoadingRobot();

        Boolean loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoad.load(robot, station, stack -> true, 10, true),
                "with every stack refused the load must give up, not spin");

        Assertions.assertFalse(loaded, "nothing the station will give means nothing loaded");
        Assertions.assertEquals(64, station.input.getItem(0).getCount(),
                "refused stacks are put back: the stone is intact");
        Assertions.assertEquals(10, station.input.getItem(1).getCount(), "the dirt is intact too");
        for (int i = 0; i < 4; i++) {
            Assertions.assertTrue(robot.inv.getItem(i).isEmpty(),
                    "the robot took nothing (slot " + i + ")");
        }
    }

    // ── ANY_QUANTITY: one refused stack must not hide the stacks behind it ──────────────────────────────────
    // The carrier and bomber boards load "a single matching stack". The station's gate (Provide Items = Diamond)
    // refuses the chest's first stack, cobblestone; the load used to give up there, so a chest of
    // [cobblestone, diamond] supplied nothing at all. Like the quantity branch it must put the refused stack back,
    // exclude that kind, and look at what is behind it.

    @Test
    public void anyQuantityMovesPastARefusedFirstStackAndLoadsTheNext() {
        PolicyStation station = new PolicyStation();
        station.input.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        station.input.setItem(1, new ItemStack(Items.DIAMOND, 5));
        station.policy = stack -> stack.is(Items.DIAMOND);

        LoadingRobot robot = new LoadingRobot();

        Boolean loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoad.load(robot, station, stack -> true, AIRobotLoad.ANY_QUANTITY, true),
                "a refused stack must be excluded from the scan - re-extracting it is an infinite loop");

        Assertions.assertTrue(loaded, "the diamond behind the refused cobblestone must still load");
        Assertions.assertEquals(64, station.input.getItem(0).getCount(),
                "the refused cobblestone is back in the station, untouched");
        Assertions.assertTrue(station.input.getItem(1).isEmpty(), "the diamonds left the station");
        Assertions.assertTrue(ItemStack.matches(robot.inv.getItem(0), new ItemStack(Items.DIAMOND, 5)),
                "the robot carries the diamonds");
    }

    /** The station search dry-runs the load with {@code doLoad} false; a refused first stack must not fail that
     *  either, or the robot never even flies to the station. */
    @Test
    public void anyQuantityDryRunSeesPastARefusedFirstStackAndTouchesNothing() {
        PolicyStation station = new PolicyStation();
        station.input.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        station.input.setItem(1, new ItemStack(Items.DIAMOND, 5));
        station.policy = stack -> stack.is(Items.DIAMOND);

        LoadingRobot robot = new LoadingRobot();

        Boolean loadable = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoad.load(robot, station, stack -> true, AIRobotLoad.ANY_QUANTITY, false));

        Assertions.assertTrue(loadable, "the dry run must see the diamond behind the refused cobblestone");
        Assertions.assertEquals(64, station.input.getItem(0).getCount(), "a dry run leaves the cobblestone alone");
        Assertions.assertEquals(5, station.input.getItem(1).getCount(), "a dry run leaves the diamonds alone");
        for (int i = 0; i < 4; i++) {
            Assertions.assertTrue(robot.inv.getItem(i).isEmpty(), "a dry run loads nothing (slot " + i + ")");
        }
    }

    @Test
    public void anyQuantityRefusingEverythingTerminatesAndConservesTheInventory() {
        PolicyStation station = new PolicyStation();
        station.input.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        station.input.setItem(1, new ItemStack(Items.DIRT, 10));
        station.policy = stack -> false;

        LoadingRobot robot = new LoadingRobot();

        Boolean loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoad.load(robot, station, stack -> true, AIRobotLoad.ANY_QUANTITY, true),
                "with every stack refused the load must give up, not spin");

        Assertions.assertFalse(loaded, "nothing the station will give means nothing loaded");
        Assertions.assertEquals(64, station.input.getItem(0).getCount(), "the cobblestone is back, intact");
        Assertions.assertEquals(10, station.input.getItem(1).getCount(), "the dirt is back, intact");
        for (int i = 0; i < 4; i++) {
            Assertions.assertTrue(robot.inv.getItem(i).isEmpty(), "the robot took nothing (slot " + i + ")");
        }
    }
}
