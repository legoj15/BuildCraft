/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.RobotManager;

/** The {@link AIRobotMain} preempt ladder, driven against a {@link MockRobotAccess} and a real {@code MjBattery}:
 *  shutdown wins at flat power, recharge takes over below the safety reserve (and backs off on failure), the
 *  overriding AI only runs once power is comfortable. The failure cooldown is private, so the test reads it
 *  reflectively — the observable it pins is that a second low-power preempt does NOT re-arm it. */
public class AIRobotMainTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @BeforeAll
    static void registerTestAis() {
        // Under the full mod load BCRobotics has registered the controller already; a bare run has not.
        if (RobotManager.getAIRobotName(AIRobotMain.class) == null) {
            RobotManager.registerAIRobot(AIRobotMain.class, "aiRobotMain");
        }
        RobotManager.registerAIRobot(SavedOverride.class, "buildcraftunofficial:test_saved_override");
    }

    @Test
    public void flatPowerStartsAShutdown() {
        robot.getBattery().setStored(0); // == SHUTDOWN_POWER, and no power station (null docking station)

        AIRobotMain main = new AIRobotMain(robot);
        main.preempt(null);

        Assertions.assertInstanceOf(AIRobotShutdown.class, main.getDelegateAI(),
                "a flat robot with nowhere to charge is shut down");
    }

    @Test
    public void belowSafetyStartsARecharge() {
        robot.getBattery().setStored(MjAPI.MJ); // above 0, below SAFETY_POWER

        AIRobotMain main = new AIRobotMain(robot);
        main.preempt(null);

        // The recharge unwinds immediately (the mock's registry has no power station), and its failure arms the
        // cooldown — that arm is the observable, since the delegate itself is gone by the time preempt returns.
        Assertions.assertNull(main.getDelegateAI(),
                "a failed recharge leaves no running delegate");
        Assertions.assertEquals(120, rechargeCooldown(main),
                "and its failure arms the recharge cooldown (120 ticks)");
    }

    @Test
    public void rechargeCooldownSuppressesAnImmediateRetry() {
        robot.getBattery().setStored(MjAPI.MJ);
        AIRobotMain main = new AIRobotMain(robot);

        main.preempt(null); // arms cooldown at 120
        Assertions.assertEquals(120, rechargeCooldown(main));
        int afterFirst = rechargeCooldown(main);

        main.preempt(null); // a second low-power preempt within the cooldown

        Assertions.assertEquals(afterFirst - 1, rechargeCooldown(main),
                "the second preempt only decrements the cooldown — it must NOT re-arm a fresh 120");
    }

    @Test
    public void safePowerRunsTheOverridingAi() {
        robot.getBattery().setStored(robot.getBattery().getCapacity()); // full: well above safety

        AIRobotMain main = new AIRobotMain(robot);
        AIRobot override = new AIRobot(robot);
        main.setOverridingAI(override);
        main.preempt(null);

        Assertions.assertSame(override, main.getDelegateAI(),
                "with power comfortable and an override waiting, the override is the active delegate");
    }

    @Test
    public void flatPowerOnAChargingStationRechargesInsteadOfShuttingDown() {
        robot.getBattery().setStored(0);
        robot.setDockingStation(new PowerStationStub());

        AIRobotMain main = new AIRobotMain(robot);
        main.preempt(null);

        // Shutdown is skipped because the station provides power; the recharge branch wins instead (and fails
        // here only because the mock registry has nowhere to recharge from).
        Assertions.assertNull(main.getDelegateAI(), "a flat robot ON power recharges, not shuts down");
        // Without this the test is tautological: deleting the recharge branch leaves the delegate null too.
        // The armed cooldown proves the recharge branch genuinely fired and unwound.
        Assertions.assertEquals(120, rechargeCooldown(main),
                "the recharge branch fired (and its failure armed the cooldown)");
    }

    // ---- the overriding AI across a save/load ----

    /** Saves {@code main} and reads it back the way the robot entity does. */
    private AIRobotMain saveAndLoad(AIRobotMain main) {
        CompoundTag nbt = new CompoundTag();
        main.writeToNbt(nbt);
        return (AIRobotMain) AIRobot.loadAI(nbt, robot);
    }

    /** A gate redirect that was running when the robot unloaded comes back as the controller's tracked
     *  override. Untracked, the next gate pulse saw "no override" and stacked a second redirect over the
     *  restored one, and a recharge that interrupted it dropped it for good. */
    @Test
    public void aRunningOverrideIsStillTheOverrideAfterALoad() {
        robot.getBattery().setStored(robot.getBattery().getCapacity());
        AIRobotMain main = new AIRobotMain(robot);
        main.setOverridingAI(new SavedOverride(robot));
        main.preempt(null);
        Assertions.assertInstanceOf(SavedOverride.class, main.getDelegateAI(), "precondition: the override runs");

        AIRobotMain loaded = saveAndLoad(main);

        Assertions.assertInstanceOf(SavedOverride.class, loaded.getDelegateAI(),
                "the override comes back as the running delegate");
        Assertions.assertNotNull(loaded.getOverridingAI(),
                "...and the controller knows it is an override, so a gate pulse does not stack another");
        Assertions.assertSame(loaded.getDelegateAI(), loaded.getOverridingAI());
    }

    /** The restored override survives the interruption that used to lose it: a recharge takes the delegate
     *  slot, and once power is comfortable the controller starts the tracked override again. */
    @Test
    public void aRestoredOverrideResumesAfterARecharge() {
        robot.getBattery().setStored(robot.getBattery().getCapacity());
        AIRobotMain main = new AIRobotMain(robot);
        main.setOverridingAI(new SavedOverride(robot));
        main.preempt(null);
        AIRobotMain loaded = saveAndLoad(main);
        AIRobot restored = loaded.getDelegateAI();

        robot.getBattery().setStored(MjAPI.MJ); // below the safety reserve
        loaded.preempt(loaded.getDelegateAI());
        Assertions.assertNull(loaded.getDelegateAI(), "the recharge (which fails here) displaced the override");

        robot.getBattery().setStored(robot.getBattery().getCapacity());
        loaded.preempt(loaded.getDelegateAI());

        Assertions.assertSame(restored, loaded.getDelegateAI(),
                "with power back, the tracked override is started again");
    }

    /** Only an override is flagged: a controller running its ordinary board must not read the board back as
     *  an override (it would then re-run the board as a redirect after every interruption). */
    @Test
    public void aBoardDelegateIsNotMistakenForAnOverride() {
        AIRobotMain main = new AIRobotMain(robot);
        main.startDelegateAI(new SavedOverride(robot)); // an ordinary delegate, never set as the override

        AIRobotMain loaded = saveAndLoad(main);

        Assertions.assertInstanceOf(SavedOverride.class, loaded.getDelegateAI(), "precondition: it came back");
        Assertions.assertNull(loaded.getOverridingAI(), "a plain delegate is not an override");
    }

    /** An override that cannot be saved (the base AI has no NBT form) is not saved at all, and leaves no
     *  dangling override behind. */
    @Test
    public void anOverrideThatCannotBeSavedLeavesNoOverrideBehind() {
        robot.getBattery().setStored(robot.getBattery().getCapacity());
        AIRobotMain main = new AIRobotMain(robot);
        main.setOverridingAI(new AIRobot(robot));
        main.preempt(null);
        Assertions.assertNotNull(main.getDelegateAI(), "precondition: the override runs");

        AIRobotMain loaded = saveAndLoad(main);

        Assertions.assertNull(loaded.getDelegateAI(), "the unsavable override is not restored");
        Assertions.assertNull(loaded.getOverridingAI(), "...and nothing is left pretending to be one");
    }

    /** A stand-in for a saveable gate redirect such as the goto-station move. */
    public static class SavedOverride extends AIRobot {
        public SavedOverride(IRobotAccess iRobot) {
            super(iRobot);
        }

        @Override
        public boolean canLoadFromNBT() {
            return true;
        }
    }

    /** Reads the private {@code rechargeCooldown} field. */
    private static int rechargeCooldown(AIRobotMain main) {
        try {
            Field f = AIRobotMain.class.getDeclaredField("rechargeCooldown");
            f.setAccessible(true);
            return f.getInt(main);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not read AIRobotMain.rechargeCooldown", e);
        }
    }

    /** A docking station that claims to provide power, for the "don't shut down on a charging plate" check. */
    private static class PowerStationStub extends buildcraft.api.robots.DockingStation {
        @Override
        public Iterable<buildcraft.api.statements.StatementSlot> getActiveActions() {
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean providesPower() {
            return true;
        }
    }
}
