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

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;

/** Loads fluid from the robot's station input into the robot's own tank, one bucket per pass, until the
 *  tank is full or the station runs dry. Ported from 7.1.x {@code AIRobotLoadFluids} (7.1.x moved the
 *  fluid through the robot's fluid transactor; the modern equivalent is
 *  {@link IRobotAccess#getFluidHandler()}).
 *
 *  <p>The transfer is an exact, transactional move: the robot's insert and the station's extract happen
 *  under one {@code Transaction}, and the extract asks for exactly what was inserted — so a simulation
 *  (uncommitted close) leaves both sides untouched, and a commit never loses or creates fluid. One bucket
 *  per pass is the 7.1.x rhythm (it also keeps the pump's 40-tick unload check meaningful). */
public class AIRobotLoadFluids extends AIRobot {

    private static final int BUCKET = 1000;

    private IFluidFilter filter;
    private int waitedCycles = 0;

    public AIRobotLoadFluids(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotLoadFluids(IRobotAccess iRobot, IFluidFilter filter) {
        super(iRobot);
        setSuccess(false);

        this.filter = filter;
    }

    @Override
    public void update() {
        if (filter == null) {
            setSuccess(false);
            terminate();
            return;
        }

        waitedCycles++;
        if (waitedCycles > 40) {
            int loaded = load(robot, robot.getDockingStation(), filter, true);
            if (loaded == 0) {
                // Nothing moved (no matching fluid, or the tank is full) — the board re-searches.
                terminate();
            } else {
                setSuccess(true);
                waitedCycles = 0;
            }
        }
    }

    /** Moves up to a bucket of filter-matching fluid from {@code station}'s input into {@code robot}'s
     *  tank. {@code doLoad} simulates (false) or executes (true); returns the mB moved (0 = nothing
     *  possible). The pump's station search dry-runs this. */
    public static int load(IRobotAccess robot, @Nullable DockingStation station, IFluidFilter filter, boolean doLoad) {
        if (station == null || !station.canRobotExtractFluid()) {
            return 0;
        }

        //? if >=1.21.10 {
        ResourceHandler<FluidResource> input = station.getFluidInput();
        FluidResource resource = input.getResource(0);
        if (resource.isEmpty()) {
            return 0;
        }
        int available = input.getAmountAsInt(0);
        if (available <= 0) {
            return 0;
        }
        if (filter != null && !filter.matches(resource.toStack(available))) {
            return 0;
        }
        IFluidHandlerAdv robotTank = robot.getFluidHandler();
        int room = robotTank.getCapacityAsInt(0, resource) - robotTank.getAmountAsInt(0);
        int amount = Math.min(Math.min(available, room), BUCKET);
        if (amount <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = robotTank.insert(0, resource, amount, tx);
            if (inserted <= 0) {
                return 0;
            }
            if (input.extract(0, resource, inserted, tx) < inserted) {
                return 0;
            }
            if (doLoad) {
                tx.commit();
            }
            return inserted;
        }
        //?} else {
        /*IFluidHandler input = station.getFluidInput();
        FluidStack inTank = input.getFluidInTank(0);
        if (inTank.isEmpty()) {
            return 0;
        }
        if (filter != null && !filter.matches(inTank)) {
            return 0;
        }
        IFluidHandlerAdv robotTank = robot.getFluidHandler();
        int room = robotTank.getTankCapacity(0) - robotTank.getFluidInTank(0).getAmount();
        int amount = Math.min(Math.min(inTank.getAmount(), room), BUCKET);
        if (amount <= 0) {
            return 0;
        }
        IFluidHandler.FluidAction action = doLoad ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
        int inserted = robotTank.fill(inTank.copyWithAmount(amount), action);
        if (inserted <= 0) {
            return 0;
        }
        FluidStack drained = input.drain(inTank.copyWithAmount(inserted), action);
        if (drained.getAmount() < inserted) {
            return 0;
        }
        return inserted;*/
        //?}
    }

    @Override
    public long getPowerCost() {
        // Ph5 cost table: 8 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 800_000;
    }
}
