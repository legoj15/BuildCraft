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

import net.neoforged.neoforge.fluids.FluidStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;

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

        //? if >=1.21.10 {
        IFluidHandlerAdv robotTank = robot.getFluidHandler();
        FluidResource resource = robotTank.getResource(0);
        if (resource.isEmpty()) {
            return 0;
        }
        int carried = robotTank.getAmountAsInt(0);
        if (carried <= 0) {
            return 0;
        }
        // 7.1.x gated the unload on ActionRobotFilter.canInteractWithFluid(station,
        // SimpleFluidFilter(carried), ActionStationAcceptFluids.class) — the D1 policy takes the filter.
        if (!station.canRobotAcceptFluid(stack -> FluidStack.isSameFluid(stack, resource.toStack(1)))) {
            return 0;
        }
        // A station whose pipe carries no fluid flow has NO fluid output, even when an Accept Fluids gate
        // action makes canRobotAcceptFluid say yes — 7.1.x guarded exactly this with
        // `if (fluidHandler == null) return 0;`.
        ResourceHandler<FluidResource> output = station.getFluidOutput();
        if (output == null) {
            return 0;
        }
        int room = output.getCapacityAsInt(0, resource) - output.getAmountAsInt(0);
        int amount = Math.min(Math.min(carried, room), BUCKET);
        if (amount <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = robotTank.extract(0, resource, amount, tx);
            if (extracted <= 0) {
                return 0;
            }
            if (output.insert(0, resource, extracted, tx) < extracted) {
                return 0;
            }
            if (doUnload) {
                tx.commit();
            }
            return extracted;
        }
        //?} else {
        /*IFluidHandlerAdv robotTank = robot.getFluidHandler();
        FluidStack carriedStack = robotTank.getFluidInTank(0);
        if (carriedStack.isEmpty()) {
            return 0;
        }
        if (!station.canRobotAcceptFluid(stack -> FluidStack.isSameFluid(stack, carriedStack))) {
            return 0;
        }
        IFluidHandler output = station.getFluidOutput();
        if (output == null) {
            return 0;
        }
        int room = output.getTankCapacity(0) - output.getFluidInTank(0).getAmount();
        int amount = Math.min(Math.min(carriedStack.getAmount(), room), BUCKET);
        if (amount <= 0) {
            return 0;
        }
        // The classic getFluidInTank may ALIAS the handler's internal stack (FluidTank does), which
        // drain() then shrinks — so the request is copied up front, and the fill always uses a fresh stack
        // a drain returned, never carriedStack (copyWithAmount on an emptied alias hands fill an EMPTY
        // stack and 0 comes back).
        FluidStack wanted = carriedStack.copyWithAmount(amount);
        // Same order as the 1.21.10+ branch's rolled-back transaction: prove BOTH halves in simulation
        // first, and only then execute. Draining the robot first and learning afterwards that the station
        // refuses the fill destroys the carried fluid.
        FluidStack simulated = robotTank.drain(wanted, IFluidHandler.FluidAction.SIMULATE);
        int extractable = simulated.getAmount();
        if (extractable <= 0) {
            return 0;
        }
        if (output.fill(simulated, IFluidHandler.FluidAction.SIMULATE) < extractable) {
            return 0;
        }
        if (!doUnload) {
            return extractable;
        }
        FluidStack drained = robotTank.drain(wanted.copyWithAmount(extractable), IFluidHandler.FluidAction.EXECUTE);
        int extracted = drained.getAmount();
        if (extracted <= 0) {
            return 0;
        }
        int filled = output.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled < extracted) {
            // The station changed between the dry run and the real fill (cannot happen inside one server
            // tick, but a destroyed bucket is worse than a defensive line): hand the rest back.
            robotTank.fill(drained.copyWithAmount(extracted - filled), IFluidHandler.FluidAction.EXECUTE);
        }
        return filled;*/
        //?}
    }

    @Override
    public long getPowerCost() {
        // Ph5 cost table: 10 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 1_000_000;
    }
}
