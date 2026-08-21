/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;

/** Empties the robot's tank into its docking station's fluid output, up to a bucket per attempt. Ported
 *  from 7.1.x {@code AIRobotUnloadFluids} (7.1.x filled the station through the classic
 *  {@code IFluidHandler} and drained the robot through its {@code IFluidHandler} face; the modern
 *  equivalent is the station's {@code ResourceHandler<FluidResource>} output and the robot's
 *  {@link IRobotAccess#getFluidHandler()}). The 7.1.x gate-fluid check
 *  ({@code ActionRobotFilter.canInteractWithFluid}) is Ph6, so the D1 station policy stands in.
 *
 *  <p>Red-baseline skeleton: {@link #unload} returns 0 (moves nothing) until the fluid step lands the
 *  transfer; the AI itself terminates on the first cycle via the inherited {@code update()}.</p> */
public class AIRobotUnloadFluids extends AIRobot {

    private int waitedCycles = 0;

    public AIRobotUnloadFluids(IRobotAccess iRobot) {
        super(iRobot);

        setSuccess(false);
    }

    /** Moves up to a bucket of the robot's tank fluid into {@code station}'s fluid output. {@code doUnload}
     *  simulates (false) or executes (true); returns the mB moved. */
    public static int unload(IRobotAccess robot, DockingStation station, boolean doUnload) {
        // Red-baseline skeleton — the transfer (robot.getFluidHandler() -> station fluid output) lands in
        // the fluid step.
        return 0;
    }
}
