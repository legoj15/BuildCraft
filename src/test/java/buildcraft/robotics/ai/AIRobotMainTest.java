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
        RobotManager.registerAIRobot(CountingOverride.class, "buildcraftunofficial:test_counting_override");
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

    /** A low robot whose home's chunk is not loaded, once a recharge has failed, waits at no cost instead of going
     *  back to its board (which ran it flat and shut it down in the field). It keeps retrying the recharge on the
     *  back-off while it waits, retries at once when the home is back, and works as before from then on. */
    @Test
    public void aLowRobotWithAnUnloadedHomeWaitsForIt() {
        robot.getBattery().setStored(MjAPI.MJ);
        HomeStub home = new HomeStub();
        robot.setLinkedStation(home);
        AIRobotMain main = new AIRobotMain(robot);

        main.cycle(); // the recharge finds nowhere to charge
        Assertions.assertInstanceOf(AIRobotWaitForHome.class, main.getDelegateAI(),
                "with its home out of reach, the robot waits rather than going back to work");
        for (int i = 0; i < 300; i++) { // past two recharge retries
            main.cycle();
        }
        Assertions.assertInstanceOf(AIRobotWaitForHome.class, main.getDelegateAI(),
                "a recharge retry that fails again goes back to waiting");
        Assertions.assertEquals(MjAPI.MJ, robot.getPower(), "waiting costs nothing");

        home.loaded = true;
        main.cycle(); // the wait sees the home back and ends
        Assertions.assertEquals(0, rechargeCooldown(main), "the home is back, so the recharge is retried at once");
        main.cycle(); // which fails here (the mock has no charger), with the home now in reach
        Assertions.assertEquals(120, rechargeCooldown(main), "the retry ran");
        Assertions.assertFalse(main.getDelegateAI() instanceof AIRobotWaitForHome,
                "with the home loaded the robot does not wait");
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

    /** A redirect a low-power recharge displaced is still the controller's override (the recharge aborted it, but
     *  the controller resumes it once power is back), so it must survive a save taken before that happens. Not
     *  being the delegate, it was not saved at all: a reload mid-recharge dropped the redirect. It comes back
     *  tracked but idle, and is started only once power is comfortable again. */
    @Test
    public void anOverrideDisplacedByARechargeSurvivesASaveMidRecharge() {
        robot.getBattery().setStored(robot.getBattery().getCapacity());
        AIRobotMain main = new AIRobotMain(robot);
        main.setOverridingAI(new CountingOverride(robot));
        main.preempt(null);
        robot.getBattery().setStored(MjAPI.MJ); // below the safety reserve
        main.preempt(main.getDelegateAI());
        Assertions.assertNotSame(main.getOverridingAI(), main.getDelegateAI(),
                "precondition: the recharge displaced the override, which is no longer the delegate");

        AIRobotMain loaded = saveAndLoad(main);

        AIRobot restored = loaded.getOverridingAI();
        Assertions.assertInstanceOf(CountingOverride.class, restored, "the displaced override comes back tracked");
        Assertions.assertNull(loaded.getDelegateAI(), "...not as a running delegate");
        Assertions.assertEquals(0, ((CountingOverride) restored).starts, "...and not started by the load");

        loaded.preempt(loaded.getDelegateAI()); // still low on power: the recharge goes first
        Assertions.assertNotSame(restored, loaded.getDelegateAI(), "a low robot recharges before it resumes");

        robot.getBattery().setStored(robot.getBattery().getCapacity());
        loaded.preempt(loaded.getDelegateAI());
        Assertions.assertSame(restored, loaded.getDelegateAI(), "with power back, the override resumes");
        Assertions.assertEquals(1, ((CountingOverride) restored).starts, "started exactly once");
    }

    /** A displaced override that cannot be saved leaves nothing behind, as a running one does. */
    @Test
    public void aDisplacedOverrideThatCannotBeSavedIsNotRestored() {
        robot.getBattery().setStored(robot.getBattery().getCapacity());
        AIRobotMain main = new AIRobotMain(robot);
        main.setOverridingAI(new AIRobot(robot));
        main.preempt(null);
        robot.getBattery().setStored(MjAPI.MJ);
        main.preempt(main.getDelegateAI());

        AIRobotMain loaded = saveAndLoad(main);

        Assertions.assertNull(loaded.getOverridingAI(), "an unsavable override is not restored");
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

    // ---- the back-off after a failed override ----

    /** An override that fails the moment it starts (a goto to a refused or vanished station) arms a back-off: a
     *  gate that holds re-issues its redirect every tick, and without the back-off each one aborted the board for a
     *  redirect that failed again at once. New overrides are refused until the back-off has run its cycles. */
    @Test
    public void aFailedOverrideBacksOffBeforeTheNextOneIsAccepted() {
        robot.getBattery().setStored(robot.getBattery().getCapacity());
        AIRobotMain main = new AIRobotMain(robot);
        main.setOverridingAI(new FailingOverride(robot));
        main.preempt(null);
        Assertions.assertNull(main.getOverridingAI(), "precondition: the failed override is no longer tracked");

        main.setOverridingAI(new AIRobot(robot));
        Assertions.assertNull(main.getOverridingAI(), "a redirect right after a failed one is refused");

        for (int i = 0; i < AIRobotMain.FAILED_OVERRIDE_COOLDOWN - 1; i++) {
            main.preempt(null);
        }
        main.setOverridingAI(new AIRobot(robot));
        Assertions.assertNull(main.getOverridingAI(), "still refused one cycle before the back-off runs out");

        main.preempt(null);
        AIRobot accepted = new AIRobot(robot);
        main.setOverridingAI(accepted);
        Assertions.assertSame(accepted, main.getOverridingAI(), "once the back-off has run out, a redirect is taken");
    }

    /** Only a failure backs off: an override that ended well leaves the slot open for the next one at once. */
    @Test
    public void aSuccessfulOverrideDoesNotBackOff() {
        robot.getBattery().setStored(robot.getBattery().getCapacity());
        AIRobotMain main = new AIRobotMain(robot);
        main.setOverridingAI(new SucceedingOverride(robot));
        main.preempt(null);
        Assertions.assertNull(main.getOverridingAI(), "precondition: the finished override is no longer tracked");

        AIRobot next = new AIRobot(robot);
        main.setOverridingAI(next);
        Assertions.assertSame(next, main.getOverridingAI(), "a redirect after a successful one is taken at once");
    }

    /** Ends unsuccessfully in its own {@code start()}, as a refused goto-station does. */
    private static class FailingOverride extends AIRobot {
        FailingOverride(IRobotAccess iRobot) {
            super(iRobot);
        }

        @Override
        public void start() {
            setSuccess(false);
            terminate();
        }
    }

    /** Ends successfully in its own {@code start()}, as a goto to the station the robot already sits at does. */
    private static class SucceedingOverride extends AIRobot {
        SucceedingOverride(IRobotAccess iRobot) {
            super(iRobot);
        }

        @Override
        public void start() {
            terminate();
        }
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

    /** A saveable redirect that counts how often it is started. */
    public static class CountingOverride extends AIRobot {
        int starts;

        public CountingOverride(IRobotAccess iRobot) {
            super(iRobot);
        }

        @Override
        public void start() {
            starts++;
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

    /** A home station whose chunk is loaded only when the test says so. */
    private static class HomeStub extends buildcraft.api.robots.DockingStation {
        boolean loaded;

        @Override
        public Iterable<buildcraft.api.statements.StatementSlot> getActiveActions() {
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean isChunkLoaded() {
            return loaded;
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
