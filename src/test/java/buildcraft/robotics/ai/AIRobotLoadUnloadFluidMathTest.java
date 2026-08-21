/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.material.Fluids;

//? if >=1.21.10 {
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
//?} else {
/*import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;*/
//?}

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;

/** The pump board's fluid math: {@link AIRobotLoadFluids#load} and {@link AIRobotUnloadFluids#unload}
 *  moved through a real single-slot station tank against the mock robot's real single-slot tank. Pins the
 *  7.1.x semantics — one bucket per call, filter-gated, simulation via the {@code doLoad}/{@code doUnload}
 *  flag, and exact conservation (nothing created, nothing destroyed). Red until the fluid step lands the
 *  real implementations (the skeletons return 0). */
public class AIRobotLoadUnloadFluidMathTest extends VanillaSetupBaseTester {

    private static final int BUCKET = 1000;

    private final MockRobotAccess robot = new MockRobotAccess();

    //? if >=1.21.10 {
    private int robotAmount() {
        return robot.getFluidHandler().getAmountAsInt(0);
    }

    private void setRobotFluid(int amount) {
        if (amount <= 0) {
            robot.setFluid(FluidResource.EMPTY, 0);
        } else {
            robot.setFluid(FluidResource.of(new FluidStack(Fluids.WATER, amount)), amount);
        }
    }
    //?} else {
    /*private int robotAmount() {
        return ((IFluidHandler) robot.getFluidHandler()).getFluidInTank(0).getAmount();
    }

    private void setRobotFluid(int amount) {
        robot.setFluid(amount <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, amount));
    }*/
    //?}

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

    // ── the station test double ─────────────────────────────────────────────
    // A DockingStation whose fluid input AND output are the same real single-slot tank — enough surface
    // for the load/unload math, nothing else.

    //? if >=1.21.10 {
    private static final class FluidStation extends DockingStation {
        private final FluidStacksResourceHandler tank;

        FluidStation(int capacity) {
            super();
            tank = new FluidStacksResourceHandler(1, capacity);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public ResourceHandler<FluidResource> getFluidInput() {
            return tank;
        }

        @Override
        public ResourceHandler<FluidResource> getFluidOutput() {
            return tank;
        }

        void fill(int amount) {
            if (amount <= 0) {
                tank.set(0, FluidResource.EMPTY, 0);
            } else {
                tank.set(0, FluidResource.of(new FluidStack(Fluids.WATER, amount)), amount);
            }
        }

        int getAmount() {
            return tank.getAmountAsInt(0);
        }
    }
    //?} else {
    /*private static final class FluidStation extends DockingStation {
        private final FluidTank tank;

        FluidStation(int capacity) {
            super();
            tank = new FluidTank(capacity);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        @Override
        public IFluidHandler getFluidInput() {
            return tank;
        }

        @Override
        public IFluidHandler getFluidOutput() {
            return tank;
        }

        void fill(int amount) {
            tank.fill(new FluidStack(Fluids.WATER, amount), IFluidHandler.FluidAction.EXECUTE);
        }

        int getAmount() {
            return tank.getFluidInTank(0).getAmount();
        }
    }*/
    //?}
}
