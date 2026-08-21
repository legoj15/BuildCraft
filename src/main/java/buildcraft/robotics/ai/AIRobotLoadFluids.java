/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;

/** Fills the robot's tank from its docking station's fluid input, up to a bucket per attempt, while the
 *  station can supply a fluid matching {@code filter}. Ported from 7.1.x {@code AIRobotLoadFluids} (7.1.x
 *  drained the station through the classic {@code IFluidHandler} and filled the robot through its
 *  {@code IFluidHandler} face; the modern equivalent is the station's
 *  {@code ResourceHandler<FluidResource>} input and the robot's
 *  {@link IRobotAccess#getFluidHandler()}). The 7.1.x gate-fluid check
 *  ({@code ActionRobotFilter.canInteractWithFluid}) is Ph6, so the D1 station policy stands in.
 *
 *  <p>Red-baseline skeleton: {@link #load} returns 0 (moves nothing) until the fluid step lands the
 *  transfer; the AI itself terminates on the first cycle via the inherited {@code update()}.</p> */
public class AIRobotLoadFluids extends AIRobot {

    private int waitedCycles = 0;
    private IFluidFilter filter;

    public AIRobotLoadFluids(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotLoadFluids(IRobotAccess iRobot, IFluidFilter iFilter) {
        this(iRobot);

        filter = iFilter;
        setSuccess(false);
    }

    /** Moves up to a bucket of {@code filter}-matching fluid from {@code station}'s fluid input into
     *  {@code robot}'s tank. {@code doLoad} simulates (false) or executes (true); returns the mB moved. */
    public static int load(IRobotAccess robot, DockingStation station, IFluidFilter filter, boolean doLoad) {
        // Red-baseline skeleton — the transfer (station fluid input -> robot.getFluidHandler()) lands in
        // the fluid step.
        return 0;
    }
}
