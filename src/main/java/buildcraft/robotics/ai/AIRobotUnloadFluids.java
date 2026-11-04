/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import javax.annotation.Nullable;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.robotics.RobotFluids;

/** Unloads fluid from the robot's own tank into its station output, one bucket per pass, until the tank
 *  is empty or the station is full. Ported from 7.1.x {@code AIRobotUnloadFluids} (7.1.x moved the fluid
 *  through the robot's fluid transactor; the modern equivalent is
 *  {@link IRobotAccess#getFluidHandler()}).
 *
 *  <p>The transfer is an exact, transactional move (the robot's extract and the station's insert under one
 *  {@code Transaction}, the insert asking for exactly what was extracted), and the 7.1.x rhythm of NOT
 *  resetting the wait counter between buckets is kept. The 1.21.1 branch has no transactions, so it gets
 *  the same guarantee by ordering: simulate the drain, simulate the fill, execute only when the fill
 *  accepts the whole amount — a station that refuses never costs the robot its fluid. */
public class AIRobotUnloadFluids extends AIRobot {

    private static final int BUCKET = 1000;

    private int waitedCycles = 0;

    public AIRobotUnloadFluids(IRobotAccess iRobot) {
        super(iRobot);
        setSuccess(false);
    }

    @Override
    public void update() {
        waitedCycles++;
        if (waitedCycles > 40) {
            int unloaded = unload(robot, robot.getDockingStation(), true);
            if (unloaded == 0) {
                // Nothing moved (tank empty, or the station is full) — the board re-searches.
                terminate();
            } else {
                setSuccess(true);
            }
        }
    }

    /** Moves up to a bucket of the robot's carried fluid into {@code station}'s output. {@code doUnload}
     *  simulates (false) or executes (true); returns the mB moved (0 = nothing possible). The pump's
     *  station search dry-runs this. */
    public static int unload(IRobotAccess robot, @Nullable DockingStation station, boolean doUnload) {
        if (station == null) {
            return 0;
        }

        BCFluidStorage robotTank = RobotFluids.tank(robot);
        BCFluidStack carried = robotTank.getStack(0);
        if (carried.isEmpty()) {
            return 0;
        }
        // 7.1.x gated the unload on ActionRobotFilter.canInteractWithFluid(station,
        // SimpleFluidFilter(carried), ActionStationAcceptFluids.class) — the D1 policy takes the filter.
        if (!station.canRobotAcceptFluid(stack -> BCFluidStack.isSameFluid(stack, carried))) {
            return 0;
        }
        // A station whose pipe carries no fluid flow has NO fluid output, even when an Accept Fluids gate
        // action makes canRobotAcceptFluid say yes — 7.1.x guarded exactly this with
        // `if (fluidHandler == null) return 0;`.
        BCFluidStorage output = RobotFluids.output(station);
        if (output == null) {
            return 0;
        }
        int room = output.getCapacity(0, carried) - output.getAmount(0);
        int amount = Math.min(Math.min(carried.getAmount(), room), BUCKET);
        if (amount <= 0) {
            return 0;
        }
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int extracted = robotTank.extract(0, carried, amount, tx);
            if (extracted <= 0) {
                return 0;
            }
            if (output.insert(0, carried, extracted, tx) < extracted) {
                return 0;
            }
            if (doUnload) {
                tx.commit();
            }
            return extracted;
        }
    }

    @Override
    public long getPowerCost() {
        // Ph5 cost table: 10 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 1_000_000;
    }
}
