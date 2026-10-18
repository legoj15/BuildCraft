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

/** Goes to a station (either {@code station} if given, or the nearest unloadable one) and empties the robot
 *  through {@link AIRobotUnload}. */
public class AIRobotGotoStationAndUnload extends AIRobot {

    private DockingStation station;
    private boolean emptiesHand;

    /** The unload a board starts to get rid of the tool in the robot's hand (a worn sword, a worn pickaxe): it fails
     *  unless the hand ends up empty, which is what sends such a board to sleep when no station will take the tool.
     *  {@code new AIRobotGotoStationAndUnload(robot)} is the cargo unload, which reports on the cargo alone. */
    public static AIRobotGotoStationAndUnload forHeldItem(IRobotAccess iRobot) {
        AIRobotGotoStationAndUnload unload = new AIRobotGotoStationAndUnload(iRobot);
        unload.emptiesHand = true;
        return unload;
    }

    /** Whether this unload was asked to empty the robot's hand ({@link #forHeldItem}) as well as its cargo. */
    public boolean emptiesHand() {
        return emptiesHand;
    }

    public AIRobotGotoStationAndUnload(IRobotAccess iRobot) {
        super(iRobot);

        station = null;
    }

    public AIRobotGotoStationAndUnload(IRobotAccess iRobot, DockingStation iStation) {
        super(iRobot);

        station = iStation;
    }

    @Override
    public void start() {
        if (station == null) {
            startDelegateAI(new AIRobotGotoStationToUnload(robot));
        } else {
            startDelegateAI(new AIRobotGotoStation(robot, station));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationToUnload) {
            if (ai.success()) {
                startDelegateAI(new AIRobotUnload(robot, emptiesHand));
            } else {
                setSuccess(false);
                terminate();
            }
        } else if (ai instanceof AIRobotGotoStation) {
            if (ai.success()) {
                startDelegateAI(new AIRobotUnload(robot, emptiesHand));
            } else {
                setSuccess(false);
                terminate();
            }
        } else if (ai instanceof AIRobotUnload) {
            // The unload's own outcome decides the composite's — without this branch the default success
            // (true) was reported even when the robot still carried everything (station output filled
            // between the search dry-run and the arrival), so the boards re-entered fetch/unload instead
            // of sleeping. Mirrors the load twin's propagation in AIRobotGotoStationAndLoad.
            setSuccess(ai.success());
            terminate();
        }
    }
}
