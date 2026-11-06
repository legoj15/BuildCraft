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

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.lib.transfer.DeferredFluidCalls;
import buildcraft.lib.transfer.FluidHandlerAdvs;

/** Loads fluid from the robot's station input into the robot's own tank, one bucket per pass, until the
 *  tank is full or the station runs dry. Ported from 7.1.x {@code AIRobotLoadFluids} (7.1.x moved the
 *  fluid through the robot's fluid transactor; the modern equivalent is
 *  {@link IRobotAccess#getFluidHandler()}).
 *
 *  <p>The transfer is an exact, transactional move: the robot's insert and the station's extract happen
 *  under one {@code Transaction}, and the extract asks for exactly what was inserted — so a simulation
 *  (uncommitted close) leaves both sides untouched, and a commit never loses or creates fluid. One bucket
 *  per pass is the 7.1.x rhythm (it also keeps the pump's 40-tick unload check meaningful). Every node runs
 *  this transaction, except when the station input is a handler that can only simulate (another mod's
 *  classic handler on 1.21.1, another mod's robot-API handler): its real drain would replay only after the
 *  robot's tank committed, so that move is made as 1.21.1 made it before the platform layer, outside any
 *  transaction: both halves simulated, the station drained for real, the robot filled with what it gave,
 *  and the rest handed back ({@link DeferredFluidCalls#drainInto}). A station whose real drain gives less
 *  than its simulation promised then cannot create fluid. */
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
        if (station == null || !station.canRobotExtractFluid(filter)) {
            return 0;
        }

        // A station whose pipe has no wooden-fluid-pipe extraction face has NO fluid input, even when a
        // Provide Fluids gate action makes canRobotExtractFluid say yes — 7.1.x guarded exactly this with
        // `if (handler == null) return 0;`. Without it every fluid-loading robot's station search (which
        // dry-runs this) throws on the server thread.
        BCFluidStorage input = FluidHandlerAdvs.storage(station.getFluidInput());
        if (input == null) {
            return 0;
        }
        BCFluidStack resource = input.getStack(0);
        if (resource.isEmpty()) {
            return 0;
        }
        int available = resource.getAmount();
        if (filter != null && !filter.matches(resource)) {
            return 0;
        }
        BCFluidStorage robotTank = FluidHandlerAdvs.storage(robot.getFluidHandler());
        int room = robotTank.getCapacity(0, resource) - robotTank.getAmount(0);
        int amount = Math.min(Math.min(available, room), BUCKET);
        if (amount <= 0) {
            return 0;
        }
        if (input instanceof DeferredFluidCalls.Owner deferred) {
            return deferred.deferredCalls().drainInto(robotTank, resource, amount, doLoad);
        }
        try (BCTransaction tx = BCTransaction.openRoot()) {
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
    }

    @Override
    public long getPowerCost() {
        // Ph5 cost table: 8 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 800_000;
    }
}
