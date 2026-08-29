/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoadFluids;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnloadFluids;

/** The fluid twin of {@link BoardRobotCarrier}: when its tank is empty it loads filter-matching fluid at a
 *  supply station (the wooden fluid pipe's discovery, {@code DockingStationPipe.getFluidInput}), carries it
 *  to another station, unloads, and sleeps when nothing is available. Ported from 7.1.x
 *  {@code BoardRobotFluidCarrier} — the 7.1.x gate fluid filter reads through the D1 seam
 *  ({@code DockingStation.getRobotFluidFilter}), and the 7.1.x tank read through {@code getTankInfo} is the
 *  modern {@code robot.getFluidHandler()} (as in {@link BoardRobotPump}). */
public class BoardRobotFluidCarrier extends RedstoneBoardRobot {

    public BoardRobotFluidCarrier(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotFluidCarrierNBT.INSTANCE;
    }

    /** The mB the robot is currently carrying (0 when the tank is empty). */
    public int carriedFluid() {
        //? if >=1.21.10 {
        return robot.getFluidHandler().getAmountAsInt(0);
        //?} else {
        /*net.neoforged.neoforge.fluids.FluidStack carried = robot.getFluidHandler().getFluidInTank(0);
        return carried == null ? 0 : carried.getAmount();*/
        //?}
    }

    @Override
    public void update() {
        if (carriedFluid() > 0) {
            startDelegateAI(new AIRobotGotoStationAndUnloadFluids(robot));
        } else {
            DockingStation linked = robot.getLinkedStation();
            IFluidFilter filter = linked != null ? linked.getRobotFluidFilter() : fluid -> true;
            startDelegateAI(new AIRobotGotoStationAndLoadFluids(robot, filter));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationAndLoadFluids) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotGotoStationAndUnloadFluids) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }
}
