/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.robots;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

/**
 * Drives the {@link AIRobot} delegation framework with no {@code Level}, no entity and no world — the
 * point of the {@link IRobotAccess} seam (robotics Ph3, Decision 2).
 *
 * <p>7.1.x's AI framework was only reachable through a live {@code EntityRobot}, so the delegation rules —
 * who pays for a cycle, which callback fires when a child finishes versus when it is cut short, what
 * happens to a running child when a new one is started on top of it — were never checked anywhere. Retyping
 * {@code AIRobot.robot} from the entity to a narrow interface makes a hand-written mock plus a real
 * {@link MjBattery} sufficient, which is what every assertion here relies on.
 *
 * <p>The energy rule is the one worth stating out loud: <b>only the leaf pays</b>. {@code cycle()} walks
 * down the delegate chain and charges {@code getPowerCost()} exactly once, at the AI that actually runs. A
 * chain that charged at every level would drain a robot in proportion to how deeply its board happened to
 * nest its sub-tasks, which is not a design anyone chose.
 */
public class AIRobotFrameworkTest {

    private static final String REGISTERED_AI_NAME = "buildcraftunofficial:framework_test_ai";

    @BeforeAll
    static void registerTestAi() {
        RobotManager.registerAIRobot(RegisteredAI.class, REGISTERED_AI_NAME);
    }

    // ── Power: only the leaf pays ───────────────────────────────────────────

    @Test
    public void cycleChargesTheRootWhenItHasNoDelegate() {
        MockRobot robot = new MockRobot();
        SilentAI root = new SilentAI(robot, MjAPI.MJ);

        long before = robot.getBattery().getStored();
        root.cycle();

        Assertions.assertEquals(before - MjAPI.MJ, robot.getBattery().getStored(),
                "a lone AI pays its own power cost once per cycle");
    }

    @Test
    public void cycleChargesOnlyTheLeafOfADelegateChain() {
        MockRobot robot = new MockRobot();
        SilentAI root = new SilentAI(robot, 5 * MjAPI.MJ);
        SilentAI leaf = new SilentAI(robot, MjAPI.MJ / 10);
        root.startDelegateAI(leaf);

        long before = robot.getBattery().getStored();
        root.cycle();

        Assertions.assertEquals(before - MjAPI.MJ / 10, robot.getBattery().getStored(),
                "the running leaf pays, the parent does not — cost must not accumulate down the chain");
    }

    @Test
    public void cycleChargesOnlyTheDeepestLeafOfANestedChain() {
        MockRobot robot = new MockRobot();
        SilentAI root = new SilentAI(robot, 5 * MjAPI.MJ);
        SilentAI middle = new SilentAI(robot, 3 * MjAPI.MJ);
        SilentAI leaf = new SilentAI(robot, MjAPI.MJ / 10);
        root.startDelegateAI(middle);
        middle.startDelegateAI(leaf);

        long before = robot.getBattery().getStored();
        root.cycle();

        Assertions.assertEquals(before - MjAPI.MJ / 10, robot.getBattery().getStored(),
                "nesting depth must not change what a cycle costs");
    }

    @Test
    public void aZeroCostAiDrainsNothing() {
        MockRobot robot = new MockRobot();
        SilentAI free = new SilentAI(robot, 0);

        long before = robot.getBattery().getStored();
        free.cycle();

        Assertions.assertEquals(before, robot.getBattery().getStored(),
                "an AI that declares no cost must not be charged a floor amount — waiting/idle AIs rely on "
                        + "this to not bleed a docked robot dry");
    }

    @Test
    public void anEmptyBatteryStillLetsTheAiRun() {
        // Characterization, and deliberately so: the battery, not the framework, is what stops a flat robot.
        // cycle() charges opportunistically and runs update() regardless, so the shutdown decision stays in
        // one place (the entity's power check) instead of being silently duplicated here.
        MockRobot robot = new MockRobot();
        robot.getBattery().setStored(0);
        SilentAI ai = new SilentAI(robot, MjAPI.MJ);

        ai.cycle();

        Assertions.assertEquals(0L, robot.getBattery().getStored(), "nothing can be drawn from a flat battery");
        Assertions.assertEquals(1, ai.updates, "and the AI still gets its update");
    }

    // ── Delegation: start / active / terminate / abort ──────────────────────

    @Test
    public void startDelegateAiStartsTheChildAndMakesItActive() {
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        RecordingAI child = new RecordingAI(robot);

        root.startDelegateAI(child);

        Assertions.assertEquals(1, child.starts, "the child's start() runs immediately");
        Assertions.assertSame(child, root.getDelegateAI(), "the child is the root's delegate");
        Assertions.assertSame(child, root.getActiveAI(), "and the active AI is the leaf, not the root");
    }

    @Test
    public void getActiveAiWalksToTheDeepestDelegate() {
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        RecordingAI middle = new RecordingAI(robot);
        RecordingAI leaf = new RecordingAI(robot);
        root.startDelegateAI(middle);
        middle.startDelegateAI(leaf);

        Assertions.assertSame(leaf, root.getActiveAI(), "getActiveAI resolves the whole chain, not one level");
    }

    @Test
    public void terminatingADelegateReportsItAsEndedAndClearsIt() {
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        RecordingAI child = new RecordingAI(robot);
        root.startDelegateAI(child);

        child.terminate();

        Assertions.assertEquals(1, child.ends, "terminate() runs the child's end()");
        Assertions.assertSame(child, root.ended, "the parent is told which delegate finished");
        Assertions.assertNull(root.aborted, "a natural finish is not an abort");
        Assertions.assertNull(root.getDelegateAI(), "and the finished delegate is cleared off the parent");
        Assertions.assertSame(root, root.getActiveAI(), "so the parent becomes active again");
    }

    @Test
    public void abortingADelegateReportsItAsAbortedAndClearsIt() {
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        RecordingAI child = new RecordingAI(robot);
        root.startDelegateAI(child);

        child.abort();

        Assertions.assertEquals(1, child.ends, "abort() still runs the child's end() so it can clean up");
        Assertions.assertSame(child, root.aborted, "the parent is told the delegate was cut short");
        Assertions.assertNull(root.ended, "an abort is not reported as a natural finish");
        Assertions.assertNull(root.getDelegateAI(), "and the aborted delegate is cleared off the parent");
    }

    @Test
    public void terminatingAParentAbortsItsRunningChildFirst() {
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        RecordingAI child = new RecordingAI(robot);
        root.startDelegateAI(child);

        root.terminate();

        Assertions.assertEquals(1, child.ends, "the child's end() runs — a terminated tree cleans up bottom-up");
        Assertions.assertSame(child, root.aborted, "the child was cut short, so it is reported as aborted");
        Assertions.assertEquals(1, root.ends, "and only then does the parent end");
    }

    @Test
    public void startingASecondDelegateAbortsTheFirst() {
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        RecordingAI first = new RecordingAI(robot);
        RecordingAI second = new RecordingAI(robot);
        root.startDelegateAI(first);

        root.startDelegateAI(second);

        Assertions.assertSame(first, root.aborted, "the displaced delegate is aborted, not silently dropped");
        Assertions.assertEquals(1, first.ends, "so it gets its end() and can release whatever it reserved");
        Assertions.assertSame(second, root.getDelegateAI(), "the new delegate takes over");
        Assertions.assertSame(second, root.getActiveAI());
    }

    @Test
    public void theDefaultUpdateTerminatesTheAi() {
        // AIRobot.update()'s base implementation calls terminate() on purpose: an AI whose whole body lives
        // in start()/end() would otherwise sit in the chain forever doing nothing.
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        AIRobot child = new AIRobot(robot);
        root.startDelegateAI(child);

        root.cycle();

        Assertions.assertSame(child, root.ended, "the default update terminates, which reports back as ended");
        Assertions.assertNull(root.getDelegateAI(), "and the chain unwinds by one level");
    }

    @Test
    public void anAiThatThrowsDuringUpdateIsAbortedRatherThanPropagating() {
        // A board that throws must not take the robot's tick — and therefore the whole entity tick — with it.
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        ThrowingAI child = new ThrowingAI(robot);
        root.startDelegateAI(child);

        Assertions.assertDoesNotThrow(root::cycle, "a throwing AI must be contained inside cycle()");
        Assertions.assertSame(child, root.aborted, "and reported to its parent as aborted");
        Assertions.assertNull(root.getDelegateAI(), "and unhooked so the next cycle is clean");
    }

    @Test
    public void preemptSeesTheCurrentDelegate() {
        MockRobot robot = new MockRobot();
        RecordingAI root = new RecordingAI(robot);
        RecordingAI child = new RecordingAI(robot);
        root.startDelegateAI(child);

        root.cycle();

        Assertions.assertSame(child, root.preempted,
                "preempt() is handed the running delegate so a parent can interrupt it — that is the only "
                        + "hook a supervising AI has");
    }

    // ── NBT: loadAI ─────────────────────────────────────────────────────────

    @Test
    public void loadAiReturnsNullForAnUnregisteredNameWithoutThrowing() {
        // The robot save path calls this on every load. A mod that removed an AI class leaves its name in
        // the save; 7.1.x returned null here and then NPE'd on the next tick, so the null is load-bearing
        // and the caller must be the one that guards it.
        MockRobot robot = new MockRobot();
        CompoundTag nbt = new CompoundTag();
        nbt.putString("aiName", "buildcraftunofficial:this_ai_does_not_exist");

        AIRobot loaded = Assertions.assertDoesNotThrow(() -> AIRobot.loadAI(nbt, robot),
                "an unknown AI name must not throw out of the entity load path");
        Assertions.assertNull(loaded, "it resolves to no AI at all");
    }

    @Test
    public void loadAiReturnsNullForAnEmptyTagWithoutThrowing() {
        MockRobot robot = new MockRobot();

        AIRobot loaded = Assertions.assertDoesNotThrow(() -> AIRobot.loadAI(new CompoundTag(), robot));
        Assertions.assertNull(loaded, "a tag with no aiName at all is also just 'no AI'");
    }

    @Test
    public void loadAiInstantiatesARegisteredAiAgainstTheGivenRobot() {
        MockRobot robot = new MockRobot();
        CompoundTag nbt = new CompoundTag();
        nbt.putString("aiName", REGISTERED_AI_NAME);

        AIRobot loaded = AIRobot.loadAI(nbt, robot);

        Assertions.assertInstanceOf(RegisteredAI.class, loaded, "a registered name resolves to its class");
        Assertions.assertSame(robot, loaded.robot,
                "and the reflective constructor lookup is the IRobotAccess one — an AI is handed the seam, "
                        + "not the entity");
    }

    @Test
    public void registeringAnAiWithoutTheIRobotAccessConstructorIsRejectedAtRegistration() {
        // Loud at registration (mod init) rather than silently null at world load, which is where the
        // reflective lookup would otherwise fail.
        Assertions.assertThrows(RuntimeException.class,
                () -> RobotManager.registerAIRobot(WrongCtorAI.class, "buildcraftunofficial:wrong_ctor_ai"),
                "an AI class without a public (IRobotAccess) constructor cannot be loaded from NBT, so it "
                        + "must not be accepted in the first place");
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    /** An AI that never terminates itself, with a caller-chosen power cost. */
    private static class SilentAI extends AIRobot {
        private final long cost;
        int updates;

        SilentAI(IRobotAccess robot, long cost) {
            super(robot);
            this.cost = cost;
        }

        @Override
        public long getPowerCost() {
            return cost;
        }

        @Override
        public void update() {
            updates++;
        }
    }

    /** Records every framework callback so the delegation rules can be asserted directly. */
    private static class RecordingAI extends AIRobot {
        int starts;
        int ends;
        AIRobot ended;
        AIRobot aborted;
        AIRobot preempted;

        RecordingAI(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public long getPowerCost() {
            return 0;
        }

        @Override
        public void start() {
            starts++;
        }

        @Override
        public void end() {
            ends++;
        }

        @Override
        public void update() {
            // Deliberately not the default: the default terminates, which would unwind the chain under
            // every test that just wants a cycle to happen.
        }

        @Override
        public void preempt(AIRobot ai) {
            preempted = ai;
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            ended = ai;
        }

        @Override
        public void delegateAIAborted(AIRobot ai) {
            aborted = ai;
        }
    }

    /** Stands in for a misbehaving board. */
    private static class ThrowingAI extends AIRobot {
        ThrowingAI(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public long getPowerCost() {
            return 0;
        }

        @Override
        public void update() {
            throw new IllegalStateException("board blew up");
        }
    }

    /** Public with a public {@code (IRobotAccess)} constructor — what the reflective load path demands. */
    public static class RegisteredAI extends AIRobot {
        public RegisteredAI(IRobotAccess robot) {
            super(robot);
        }
    }

    /** Public, but with no {@code (IRobotAccess)} constructor at all. */
    public static class WrongCtorAI extends AIRobot {
        public WrongCtorAI() {
            super(null);
        }
    }

    /**
     * The whole robot, as far as an AI is concerned: a real {@link MjBattery} and nothing else. Everything
     * the AI framework itself touches is here; the rest exists only because {@link IRobotAccess} declares
     * it, and returning inert values is the honest thing for a test double to do.
     */
    private static class MockRobot implements IRobotAccess {

        /** Deliberately a local number rather than {@code EntityRobotBase.MAX_POWER}: that constant lives on
         *  an {@code Entity} subclass, and class-loading {@code Entity} outside a booted game is not possible
         *  (its {@code AttachmentHolder} base asks FML whether it is in production during static init). Real
         *  capacity is irrelevant here anyway — this file is about who pays for a cycle, not how much fits. */
        private static final long TEST_CAPACITY = 10_000L * MjAPI.MJ;

        private final MjBattery battery = new MjBattery(TEST_CAPACITY);

        MockRobot() {
            battery.addPower(TEST_CAPACITY, false);
        }

        // -- energy: the only live part --

        @Override
        public MjBattery getBattery() {
            return battery;
        }

        @Override
        public long getPower() {
            return battery.getStored();
        }

        // -- entity-derived --

        @Override
        public Level level() {
            return null;
        }

        @Override
        public Vec3 position() {
            return Vec3.ZERO;
        }

        @Override
        public BlockPos blockPosition() {
            return BlockPos.ZERO;
        }

        @Override
        public int getId() {
            return 1;
        }

        @Override
        public Vec3 getDeltaMovement() {
            return Vec3.ZERO;
        }

        @Override
        public void setDeltaMovement(Vec3 movement) {
        }

        @Override
        public AABB getBoundingBox() {
            return null;
        }

        // -- held item / aiming --

        @Override
        public ItemStack getHeldItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItemInUse(ItemStack stack) {
        }

        // The Ph5 fluid/attack seams: the framework tests never drive them, so inert answers are honest.
        @Override
        public IFluidHandlerAdv getFluidHandler() {
            return null;
        }

        @Override
        public void attackTargetEntityWithCurrentItem(Entity target) {
        }

        @Override
        public void setItemActive(boolean active) {
        }

        @Override
        public void aimItemAt(float yaw, float pitch) {
        }

        @Override
        public void aimItemAt(BlockPos pos) {
        }

        @Override
        public float getAimYaw() {
            return 0;
        }

        @Override
        public float getAimPitch() {
            return 0;
        }

        // -- docking --

        @Override
        public void dock(DockingStation station) {
        }

        @Override
        public void undock() {
        }

        @Override
        public DockingStation getDockingStation() {
            return null;
        }

        @Override
        public DockingStation getLinkedStation() {
            return null;
        }

        @Override
        public void setMainStation(DockingStation station) {
        }

        // -- registry / identity --

        @Override
        public IRobotRegistry getRegistry() {
            return null;
        }

        @Override
        public long getRobotId() {
            // == EntityRobotBase.NULL_ROBOT_ID, spelled out for the same class-loading reason as TEST_CAPACITY.
            return Long.MAX_VALUE;
        }

        @Override
        public void releaseResources() {
        }

        // -- zones --

        @Override
        public IZone getZoneToWork() {
            return null;
        }

        @Override
        public IZone getZoneToLoadUnload() {
            return null;
        }

        // -- inventory --

        @Override
        public int getInventorySize() {
            return 0;
        }

        @Override
        public ItemStack getInventoryStack(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setInventoryStack(int slot, ItemStack stack) {
        }

        @Override
        public boolean containsItems() {
            return false;
        }

        @Override
        public boolean hasFreeSlot() {
            return false;
        }

        @Override
        public ItemStack receiveItem(BlockEntity tile, ItemStack stack) {
            return stack;
        }

        /** The framework tests never touch the transactor; a bare inert one satisfies the interface. */
        @Override
        public buildcraft.api.inventory.IItemTransactor getTransactor() {
            return new buildcraft.api.inventory.IItemTransactor() {
                @Override
                public net.minecraft.world.item.ItemStack insert(net.minecraft.world.item.ItemStack stack,
                        boolean allOrNone, boolean simulate) {
                    return stack;
                }

                @Override
                public net.minecraft.world.item.ItemStack extract(IStackFilter filter, int min, int max,
                        boolean simulate) {
                    return ItemStack.EMPTY;
                }
            };
        }

        // -- pathing hints --

        @Override
        public void unreachableEntityDetected(Entity entity) {
        }

        @Override
        public boolean isKnownUnreachable(Entity entity) {
            return false;
        }

        // -- board / motion --

        @Override
        public RedstoneBoardRobot getBoard() {
            return null;
        }

        @Override
        public boolean isMoving() {
            return false;
        }
    }
}
