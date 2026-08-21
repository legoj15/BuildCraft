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
import buildcraft.api.robots.IRobotAccess;

/** Goes to the nearest unloadable station and empties the robot's tank through
 *  {@link AIRobotUnloadFluids}. Ported from 7.1.x {@code AIRobotGotoStationAndUnloadFluids}. */
public class AIRobotGotoStationAndUnloadFluids extends AIRobot {

    public AIRobotGotoStationAndUnloadFluids(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public void start() {
        startDelegateAI(new AIRobotGotoStationToUnloadFluids(robot));
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationToUnloadFluids) {
            if (ai.success()) {
                startDelegateAI(new AIRobotUnloadFluids(robot));
            } else {
                setSuccess(false);
                terminate();
            }
        } else if (ai instanceof AIRobotUnloadFluids) {
            // The unload's own outcome decides the composite's — without this branch the default success
            // (true) was reported even when the robot still carried everything (station output filled
            // between the search dry-run and the arrival). Mirrors AIRobotGotoStationAndUnload.
            setSuccess(ai.success());
            terminate();
        }
    }
}
