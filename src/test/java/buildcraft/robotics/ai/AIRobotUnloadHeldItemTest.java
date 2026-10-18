/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.Collections;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.transport.IInjectable;

/** What {@link AIRobotUnload} reports when it was asked to empty the robot's HAND (a worn tool the combat and break
 *  boards unload before they carry on). {@code success} used to be "no cargo left", which says nothing about the hand:
 *  a station that would not take the broken sword (its gate refuses it, its pipe is full) still produced a
 *  successful unload, so the boards that sleep on a failed unload started the same unload again, forever.
 *
 *  <p>The cargo unload ({@code new AIRobotUnload(robot)}, what the carrier, picker and dispose AIs run) keeps its old
 *  meaning: they never asked for the hand, and a robot that has emptied its cargo has done their job whatever it holds.
 *  The unload is driven the way its parent drives it: cycled until it ends (it waits 40 cycles on arrival). */
public class AIRobotUnloadHeldItemTest extends VanillaSetupBaseTester {

    /** A station with a one-stack pipe behind its output, whose gate and room the test sets. */
    private static final class OutputStation extends DockingStation {
        Predicate<ItemStack> accepts = stack -> true;
        boolean pipeHasRoom = true;
        final ItemStack[] received = { ItemStack.EMPTY };

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public EnumPipePart getItemOutputSide() {
            return EnumPipePart.DOWN;
        }

        @Override
        public boolean canRobotAcceptItem(ItemStack stack) {
            return accepts.test(stack);
        }

        @Override
        public IInjectable getItemOutput() {
            return new IInjectable() {
                @Override
                public boolean canInjectItems(Direction from) {
                    return true;
                }

                @Override
                public ItemStack injectItem(ItemStack stack, boolean doAdd, Direction from, DyeColor color,
                        double speed) {
                    if (!pipeHasRoom) {
                        return stack;
                    }
                    if (doAdd) {
                        received[0] = stack.copy();
                    }
                    return ItemStack.EMPTY;
                }
            };
        }
    }

    private static ItemStack wornSword() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(sword.getMaxDamage());
        return sword;
    }

    private static MockRobotAccess dockedRobotHolding(OutputStation station, ItemStack held) {
        MockRobotAccess robot = new MockRobotAccess();
        robot.setDockingStation(station);
        robot.setHeldItem(held);
        return robot;
    }

    /** Cycles {@code ai} under a parent until it ends; returns whether it reported success. */
    private static boolean run(IRobotAccess robot, AIRobot ai) {
        boolean[] ended = new boolean[1];
        AIRobot parent = new AIRobot(robot) {
            @Override
            public void delegateAIEnded(AIRobot done) {
                ended[0] = true;
            }
        };
        parent.startDelegateAI(ai);
        for (int i = 0; i < 100 && !ended[0]; i++) {
            parent.cycle();
        }
        Assertions.assertTrue(ended[0], "fixture: the unload ends within 100 cycles");
        return ai.success();
    }

    @Test
    public void aHeldItemTheStationRefusesIsAFailedUnload() {
        OutputStation station = new OutputStation();
        station.accepts = stack -> false; // the gate refuses everything
        MockRobotAccess robot = dockedRobotHolding(station, wornSword());

        Assertions.assertFalse(run(robot, new AIRobotUnload(robot, true)),
                "the sword is still in the hand: the unload of the hand failed");
        Assertions.assertFalse(robot.getHeldItem().isEmpty(), "fixture: and the sword is still there");
    }

    @Test
    public void aHeldItemTheFullPipeCannotTakeIsAFailedUnload() {
        OutputStation station = new OutputStation();
        station.pipeHasRoom = false;
        MockRobotAccess robot = dockedRobotHolding(station, wornSword());

        Assertions.assertFalse(run(robot, new AIRobotUnload(robot, true)),
                "the pipe took nothing, so the sword is still in the hand");
    }

    @Test
    public void aHeldItemTheStationTakesIsASuccessfulUnload() {
        OutputStation station = new OutputStation();
        MockRobotAccess robot = dockedRobotHolding(station, wornSword());

        Assertions.assertTrue(run(robot, new AIRobotUnload(robot, true)), "the sword went into the station");
        Assertions.assertTrue(robot.getHeldItem().isEmpty(), "the hand is empty");
        Assertions.assertEquals(Items.IRON_SWORD, station.received[0].getItem(), "the station got the sword");
    }

    @Test
    public void anEmptyHandIsASuccessfulUnloadOfTheHand() {
        OutputStation station = new OutputStation();
        MockRobotAccess robot = dockedRobotHolding(station, ItemStack.EMPTY);

        Assertions.assertTrue(run(robot, new AIRobotUnload(robot, true)), "nothing in the hand: nothing left to fail");
    }

    /** The cargo unload does not ask for the hand: the carrier (and the picker and dispose AIs) report on their cargo
     *  alone, so a robot that still holds a tool - which this station would not take - has still emptied its cargo. */
    @Test
    public void theCargoUnloadIgnoresWhatIsInTheHand() {
        OutputStation station = new OutputStation();
        station.accepts = stack -> false;
        MockRobotAccess robot = dockedRobotHolding(station, wornSword());

        Assertions.assertTrue(run(robot, new AIRobotUnload(robot)),
                "the cargo is empty; the unload of the cargo succeeded whatever the hand holds");
        Assertions.assertFalse(robot.getHeldItem().isEmpty(), "and the hand is left alone, as ever");
    }

    // ── The composite the boards actually start ─────────────────────────────────────────────────────────────

    /** {@link AIRobotGotoStationAndUnload} arrives and unloads; the unload's own outcome decides its. Its flight is
     *  stood in for by handing it a finished one (the station is already the robot's dock), then the unload is cycled
     *  to its end. */
    private static boolean runComposite(MockRobotAccess robot, AIRobotGotoStationAndUnload composite) {
        AIRobot arrived = new AIRobotGotoStation(robot) {
            @Override
            public boolean success() {
                return true;
            }
        };
        composite.delegateAIEnded(arrived); // the flight is over; the composite starts the unload
        Assertions.assertTrue(composite.getDelegateAI() instanceof AIRobotUnload, "fixture: the unload is running");
        for (int i = 0; i < 100 && composite.getDelegateAI() != null; i++) {
            composite.cycle();
        }
        Assertions.assertNull(composite.getDelegateAI(), "fixture: the composite ends within 100 cycles");
        return composite.success();
    }

    @Test
    public void theHeldItemCompositeFailsWhenTheSwordStaysInTheHand() {
        OutputStation station = new OutputStation();
        station.accepts = stack -> false;
        MockRobotAccess robot = dockedRobotHolding(station, wornSword());

        Assertions.assertFalse(runComposite(robot, AIRobotGotoStationAndUnload.forHeldItem(robot)),
                "what the knight, butcher and break boards start: it must fail so they go to sleep");
    }

    @Test
    public void theCargoCompositeStillSucceedsWithAToolInHand() {
        OutputStation station = new OutputStation();
        station.accepts = stack -> false;
        MockRobotAccess robot = dockedRobotHolding(station, wornSword());

        Assertions.assertTrue(runComposite(robot, new AIRobotGotoStationAndUnload(robot)),
                "what the carrier, picker and dispose AIs start: unchanged, the cargo is what they asked about");
    }
}
