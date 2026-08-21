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
import buildcraft.api.robots.IRobotAccess;

/** {@link AIRobotGotoStationToLoadFluids} then {@link AIRobotLoadFluids}: go to a loadable station and
 *  actually fill the robot's tank with filter-matching fluid, bucket by bucket. Ported from 7.1.x
 *  {@code AIRobotGotoStationAndLoadFluids}. */
public class AIRobotGotoStationAndLoadFluids extends AIRobot {

    private IFluidFilter filter;

    public AIRobotGotoStationAndLoadFluids(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotGotoStationAndLoadFluids(IRobotAccess iRobot, IFluidFilter filter) {
        this(iRobot);

        this.filter = filter;
    }

    @Override
    public void start() {
        startDelegateAI(new AIRobotGotoStationToLoadFluids(robot, filter));
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationToLoadFluids) {
            if (filter != null && ai.success()) {
                startDelegateAI(new AIRobotLoadFluids(robot, filter));
            } else {
                setSuccess(false);
                terminate();
            }
        } else if (ai instanceof AIRobotLoadFluids) {
            // The load's own outcome decides the composite's — the item twin (AIRobotGotoStationAndLoad)
            // propagates it the same way.
            setSuccess(ai.success());
            terminate();
        }
    }
}
