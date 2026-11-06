/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.material.Fluids;


import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementSlot;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.transfer.FluidHandlerAdvs;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.robotics.statements.ActionRobotFilter;
import buildcraft.robotics.statements.ActionStationAcceptFluids;
import buildcraft.robotics.statements.ActionStationProvideFluids;

/** The pump board's fluid math: {@link AIRobotLoadFluids#load} and {@link AIRobotUnloadFluids#unload}
 *  moved through a real single-slot station tank against the mock robot's real single-slot tank. Pins the
 *  7.1.x semantics — one bucket per call, filter-gated, simulation via the {@code doLoad}/{@code doUnload}
 *  flag, and exact conservation (nothing created, nothing destroyed). */
public class AIRobotLoadUnloadFluidMathTest extends VanillaSetupBaseTester {

    private static final int BUCKET = 1000;

    private final MockRobotAccess robot = new MockRobotAccess();

    private int robotAmount() {
        return robot.getFluidHandler().getFluidInTank(0).getAmount();
    }

    private void setRobotFluid(int amount) {
        robot.setFluid(amount <= 0 ? BCFluidStack.EMPTY : new BCFluidStack(Fluids.WATER, amount));
    }

    @Test
    public void loadDrainsABucketFromTheStationIntoTheRobot() {
        FluidStation station = new FluidStation(BUCKET);
        station.fill(BUCKET);

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, true));

        Assertions.assertEquals(BUCKET, loaded, "a full station supplies a full bucket");
        Assertions.assertEquals(0, station.getAmount(), "the station was drained of exactly what loaded");
        Assertions.assertEquals(BUCKET, robotAmount(), "the robot's tank holds the loaded bucket");
    }

    @Test
    public void loadIsCappedByTheStationsAvailableFluid() {
        FluidStation station = new FluidStation(BUCKET);
        station.fill(400);

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, true));

        Assertions.assertEquals(400, loaded, "the robot takes all the station has");
        Assertions.assertEquals(0, station.getAmount());
        Assertions.assertEquals(400, robotAmount());
    }

    @Test
    public void loadHonoursTheFilter() {
        FluidStation station = new FluidStation(BUCKET);
        station.fill(BUCKET);

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> f.getFluid() == Fluids.LAVA, true));

        Assertions.assertEquals(0, loaded, "a lava-only filter rejects the station's water");
        Assertions.assertEquals(BUCKET, station.getAmount(), "nothing moved on a filter miss");
        Assertions.assertEquals(0, robotAmount());
    }

    @Test
    public void loadWithDoLoadFalseSimulatesOnly() {
        FluidStation station = new FluidStation(BUCKET);
        station.fill(BUCKET);

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, false));

        Assertions.assertEquals(BUCKET, loaded, "the simulation reports what WOULD load");
        Assertions.assertEquals(BUCKET, station.getAmount(), "but nothing actually left the station");
        Assertions.assertEquals(0, robotAmount(), "…and nothing entered the robot");
    }

    @Test
    public void loadReturnsZeroWithoutAStation() {
        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, null, f -> true, true));

        Assertions.assertEquals(0, loaded, "no station, nothing to load");
        Assertions.assertEquals(0, robotAmount());
    }

    @Test
    public void fluidIsConservedAcrossALoad() {
        FluidStation station = new FluidStation(BUCKET);
        station.fill(BUCKET);
        int before = station.getAmount() + robotAmount();

        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, true));

        Assertions.assertEquals(before, station.getAmount() + robotAmount(),
                "a load moves fluid between tanks — it never creates or destroys any");
    }

    @Test
    public void unloadEmptiesTheRobotIntoTheStation() {
        FluidStation station = new FluidStation(BUCKET);
        setRobotFluid(BUCKET);

        int unloaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, station, true));

        Assertions.assertEquals(BUCKET, unloaded, "the robot hands over a full bucket");
        Assertions.assertEquals(0, robotAmount(), "the robot's tank is empty");
        Assertions.assertEquals(BUCKET, station.getAmount(), "the station holds the bucket");
    }

    @Test
    public void unloadIsCappedByTheRobotsTank() {
        FluidStation station = new FluidStation(BUCKET);
        setRobotFluid(400);

        int unloaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, station, true));

        Assertions.assertEquals(400, unloaded, "the robot gives up all it carries");
        Assertions.assertEquals(0, robotAmount());
        Assertions.assertEquals(400, station.getAmount());
    }

    @Test
    public void unloadIsCappedByTheStationsCapacity() {
        FluidStation station = new FluidStation(600);
        setRobotFluid(BUCKET);

        int unloaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, station, true));

        Assertions.assertEquals(600, unloaded, "the station takes only what it can hold");
        Assertions.assertEquals(400, robotAmount(), "the robot keeps the remainder");
        Assertions.assertEquals(600, station.getAmount());
    }

    @Test
    public void unloadReturnsZeroWithoutAStation() {
        setRobotFluid(BUCKET);

        int unloaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, null, true));

        Assertions.assertEquals(0, unloaded, "no station, nothing to unload");
        Assertions.assertEquals(BUCKET, robotAmount(), "the robot keeps its fluid");
    }

    @Test
    public void unloadingAnEmptyRobotReturnsZero() {
        FluidStation station = new FluidStation(BUCKET);

        int unloaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, station, true));

        Assertions.assertEquals(0, unloaded, "an empty robot has nothing to give");
        Assertions.assertEquals(0, station.getAmount());
    }

    /** The exact loss this pins: a station that REPORTS room (so the amount is sized from it) but then
     *  refuses the fill. The 1.21.1 branch used to drain the robot's tank for real and only then learn the
     *  fill was refused, destroying the carried bucket; the 1.21.10+ branch runs both halves in one
     *  rolled-back transaction. Both nodes must leave the fluid aboard, on the dry run and on the real
     *  call. */
    @Test
    public void unloadKeepsTheCarriedFluidWhenTheStationRefusesTheFill() {
        FluidStation station = new FluidStation(BUCKET, true);
        setRobotFluid(BUCKET);

        int simulated = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, station, false));
        Assertions.assertEquals(0, simulated, "a station that refuses the fill takes nothing, even on the dry run");
        Assertions.assertEquals(BUCKET, robotAmount(), "a dry run never touches the robot's tank");

        int unloaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, station, true));

        Assertions.assertEquals(0, unloaded, "nothing moved: the station refused the fill");
        Assertions.assertEquals(BUCKET, robotAmount(),
                "the refused fill must leave the carried bucket aboard — draining first destroys it");
        Assertions.assertEquals(0, station.getAmount(), "and the station gained nothing");
    }

    /** The load-side mirror of the unload loss above: a station that REPORTS a full bucket (so the amount is
     *  sized from it) but whose drain then yields nothing. The 1.21.1 branch used to fill the robot's tank for
     *  real before draining the station, so the refused drain left the bucket in BOTH tanks - fluid out of thin
     *  air; the 1.21.10+ branch runs both halves in one rolled-back transaction. Both nodes must leave both
     *  tanks exactly as they were, on the dry run and on the real call. */
    @Test
    public void loadMovesNothingWhenTheStationRefusesTheDrain() {
        FluidStation station = new FluidStation(BUCKET, false, 0);
        station.fill(BUCKET);

        int simulated = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, false));
        Assertions.assertEquals(0, simulated, "a station that refuses the drain gives nothing, even on the dry run");
        Assertions.assertEquals(0, robotAmount(), "a dry run never touches the robot's tank");

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, true));

        Assertions.assertEquals(0, loaded, "nothing moved: the station refused the drain");
        Assertions.assertEquals(0, robotAmount(),
                "the refused drain must not leave a bucket in the robot - filling first duplicates it");
        Assertions.assertEquals(BUCKET, station.getAmount(), "and the station still holds its own bucket");
    }

    /** A drain that yields less than the tank reported (a source that over-reports, or changed under us).
     *  Whatever moves must be consistent on both sides - no duplication, no loss: the robot gains exactly what
     *  the station lost, and the call reports exactly that. */
    @Test
    public void loadConservesFluidWhenTheStationDrainYieldsLessThanItReports() {
        FluidStation station = new FluidStation(BUCKET, false, 300);
        station.fill(BUCKET);
        int before = station.getAmount() + robotAmount();

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, true));

        Assertions.assertEquals(before, station.getAmount() + robotAmount(),
                "a short drain must not create or destroy fluid: station + robot is conserved");
        Assertions.assertEquals(loaded, robotAmount(), "the call reports exactly what the robot gained");
        Assertions.assertEquals(BUCKET - loaded, station.getAmount(), "...and exactly what the station lost");
    }

    /** Another mod's station input (a robot-API handler, or on 1.21.1 a classic one) can only simulate, and here
     *  its real drain gives less than its simulation promised. Before the platform layer 1.21.1 drained such a
     *  station for real first and filled the robot with what came out; a robot filled in a transaction whose
     *  station drain only replays at the commit keeps fluid the station never gave. */
    @Test
    public void loadConservesFluidWhenAForeignStationsRealDrainGivesLessThanItsSimulation() {
        LyingTank tank = new LyingTank(BUCKET, 400);
        tank.held = new BCFluidStack(Fluids.WATER, BUCKET);
        ForeignStation station = new ForeignStation(tank);

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, true));

        Assertions.assertEquals(BUCKET, tank.held.getAmount() + robotAmount(),
                "a short real drain must not create fluid: station + robot is conserved");
        Assertions.assertEquals(400, loaded, "the robot took what the station really gave");
        Assertions.assertEquals(loaded, robotAmount());
    }

    /** The unload mirror: a foreign station output whose real fill takes less than its simulation promised.
     *  The robot is drained first, so what the station did not take goes back aboard instead of vanishing. */
    @Test
    public void unloadConservesFluidWhenAForeignStationsRealFillTakesLessThanItsSimulation() {
        LyingTank tank = new LyingTank(BUCKET, 400);
        ForeignStation station = new ForeignStation(tank);
        setRobotFluid(BUCKET);

        int unloaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, station, true));

        Assertions.assertEquals(BUCKET, tank.held.getAmount() + robotAmount(),
                "a short real fill must not destroy fluid: station + robot is conserved");
        Assertions.assertEquals(400, unloaded, "the call reports what the station really took");
        Assertions.assertEquals(BUCKET - 400, robotAmount(), "the rest went back aboard");
    }

    @Test
    public void aForeignStationDryRunMovesNothing() {
        LyingTank tank = new LyingTank(BUCKET, 400);
        tank.held = new BCFluidStack(Fluids.WATER, BUCKET);
        ForeignStation station = new ForeignStation(tank);

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, false));

        Assertions.assertEquals(BUCKET, loaded, "the dry run reports what the station's simulation offers");
        Assertions.assertEquals(BUCKET, tank.held.getAmount(), "but nothing left the station");
        Assertions.assertEquals(0, robotAmount(), "and nothing entered the robot");
    }

    // ── a station that PASSES the gate policy but has no fluid handler ──────
    // A Provide/Accept Fluids gate action makes canRobotExtractFluid/canRobotAcceptFluid say yes, but a
    // station whose pipe has no wooden-fluid-pipe extraction face still hands back a null handler.
    // 7.1.x guarded that with `if (handler == null) return 0;`.

    @Test
    public void loadReturnsZeroWhenTheStationHasNoFluidInput() {
        HandlerlessStation station = new HandlerlessStation(new ActionStationProvideFluids());

        Assertions.assertTrue(station.canRobotExtractFluid(f -> true),
                "the fixture must actually pass the gate policy — otherwise the null handler is never reached");

        int loaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotLoadFluids.load(robot, station, f -> true, true));

        Assertions.assertEquals(0, loaded, "a Provide Fluids gate on a station with no fluid input loads nothing");
        Assertions.assertEquals(0, robotAmount(), "…and the robot's tank is untouched");
    }

    @Test
    public void unloadReturnsZeroWhenTheStationHasNoFluidOutput() {
        HandlerlessStation station = new HandlerlessStation(new ActionStationAcceptFluids());
        setRobotFluid(BUCKET);

        Assertions.assertTrue(station.canRobotAcceptFluid(f -> true),
                "the fixture must actually pass the gate policy — otherwise the null handler is never reached");

        int unloaded = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> AIRobotUnloadFluids.unload(robot, station, true));

        Assertions.assertEquals(0, unloaded, "an Accept Fluids gate on a station with no fluid output unloads nothing");
        Assertions.assertEquals(BUCKET, robotAmount(), "…and the robot keeps its fluid");
    }

    /** A station carrying one always-on, parameter-less fluid gate action — so the D1 policy passes
     *  exactly as {@code DockingStationPipe} would — but with the base class's null
     *  {@code getFluidInput}/{@code getFluidOutput}, which is what a pipe with no wooden-fluid extraction
     *  face really returns. */
    private static final class HandlerlessStation extends DockingStation {
        private final List<StatementSlot> slots;

        HandlerlessStation(IStatement action) {
            super();
            StatementSlot slot = new StatementSlot();
            slot.statement = action;
            slot.parameters = new IStatementParameter[0];
            slots = List.of(slot);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return slots;
        }

        @Override
        public boolean canRobotExtractFluid(IFluidFilter filter) {
            return ActionRobotFilter.canInteractWithFluid(this, filter, ActionStationProvideFluids.class);
        }

        @Override
        public boolean canRobotAcceptFluid(IFluidFilter filter) {
            return ActionRobotFilter.canInteractWithFluid(this, filter, ActionStationAcceptFluids.class);
        }
    }

    // ── another mod's station handler ───────────────────────────────────────

    /** A one-tank robot-API handler that is not one of BuildCraft's adapters, so BuildCraft can only simulate it.
     *  A simulation answers honestly; a real call moves at most {@code realCap}. */
    private static final class LyingTank implements buildcraft.api.core.IFluidHandlerAdv {
        private final int capacity;
        private final int realCap;
        BCFluidStack held = BCFluidStack.EMPTY;

        LyingTank(int capacity, int realCap) {
            this.capacity = capacity;
            this.realCap = realCap;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public BCFluidStack getFluidInTank(int tank) {
            return held.copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return capacity;
        }

        @Override
        public int fill(BCFluidStack resource, boolean simulate) {
            if (resource.isEmpty() || (!held.isEmpty() && !BCFluidStack.isSameFluidSameComponents(held, resource))) {
                return 0;
            }
            int filled = Math.min(resource.getAmount(), capacity - held.getAmount());
            if (!simulate) {
                filled = Math.min(filled, realCap);
                held = resource.copyWithAmount(held.getAmount() + filled);
            }
            return filled;
        }

        @Override
        public BCFluidStack drain(BCFluidStack resource, boolean simulate) {
            if (held.isEmpty() || !BCFluidStack.isSameFluidSameComponents(held, resource)) {
                return BCFluidStack.EMPTY;
            }
            return drain(resource.getAmount(), simulate);
        }

        @Override
        public BCFluidStack drain(int maxDrain, boolean simulate) {
            int drained = Math.min(maxDrain, held.getAmount());
            if (!simulate) {
                drained = Math.min(drained, realCap);
            }
            if (drained <= 0) {
                return BCFluidStack.EMPTY;
            }
            BCFluidStack out = held.copyWithAmount(drained);
            if (!simulate) {
                held = drained == held.getAmount() ? BCFluidStack.EMPTY : held.copyWithAmount(held.getAmount() - drained);
            }
            return out;
        }

        @Override
        public BCFluidStack drain(IFluidFilter filter, int maxDrain, boolean simulate) {
            return held.isEmpty() || !filter.matches(held) ? BCFluidStack.EMPTY : drain(maxDrain, simulate);
        }
    }

    /** A station whose fluid input and output are one foreign handler. */
    private static final class ForeignStation extends DockingStation {
        private final buildcraft.api.core.IFluidHandlerAdv handler;

        ForeignStation(buildcraft.api.core.IFluidHandlerAdv handler) {
            super();
            this.handler = handler;
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public buildcraft.api.core.IFluidHandlerAdv getFluidInput() {
            return handler;
        }

        @Override
        public buildcraft.api.core.IFluidHandlerAdv getFluidOutput() {
            return handler;
        }
    }

    // ── the station test double ─────────────────────────────────────────────
    // A DockingStation whose fluid input AND output are the same real single-slot tank — enough surface
    // for the load/unload math, nothing else.

    private static final class FluidStation extends DockingStation {
        private final BCFluidTank tank;

        FluidStation(int capacity) {
            this(capacity, false, Integer.MAX_VALUE);
        }

        FluidStation(int capacity, boolean refuseFill) {
            this(capacity, refuseFill, Integer.MAX_VALUE);
        }

        /** {@code refuseFill}: the tank still reports its capacity (so a caller sizes the transfer from the
         *  room it sees) but every insert returns 0 — a station that lies about having room.
         *  {@code drainCap}: the tank still reports what it holds but never yields more than this per extract
         *  (0 = refuses outright) — a source that lies about having fluid. */
        FluidStation(int capacity, boolean refuseFill, int drainCap) {
            super();
            tank = new BCFluidTank(1, capacity) {
                @Override
                public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
                    return refuseFill ? 0 : super.insert(index, resource, amount, transaction);
                }

                @Override
                public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
                    return super.extract(index, resource, Math.min(amount, drainCap), transaction);
                }
            };
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public buildcraft.api.core.IFluidHandlerAdv getFluidInput() {
            return FluidHandlerAdvs.of(tank);
        }

        @Override
        public buildcraft.api.core.IFluidHandlerAdv getFluidOutput() {
            return FluidHandlerAdvs.of(tank);
        }

        void fill(int amount) {
            tank.setFluidStack(0, amount <= 0 ? BCFluidStack.EMPTY : new BCFluidStack(Fluids.WATER, amount));
        }

        int getAmount() {
            return tank.getStack(0).getAmount();
        }
    }
}
