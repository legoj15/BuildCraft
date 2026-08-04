/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.ai.AIRobotFetchItem;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;

/** Picks up dropped items within 250 blocks and carries them to a station to unload. When nothing to fetch and
 *  nothing held it goes to sleep. The "this item is being fetched" set keys on {@code ItemEntity.getUUID()}
 *  (D4) rather than 7.1.x's recycled {@code int} ids. */
public class BoardRobotPicker extends RedstoneBoardRobot {

    /** Items currently being fetched by some picker, keyed by the dropped item's UUID — globally unique where
     *  7.1.x's {@code int} entity ids were not. Cleared on server start. */
    public static final Set<UUID> targettedItems = new HashSet<>();

    public BoardRobotPicker(IRobotAccess iRobot) {
        super(iRobot);
    }

    public static void onServerStart() {
        targettedItems.clear();
    }

    private void fetchNewItem() {
        DockingStation linked = robot.getLinkedStation();
        IStackFilter filter = linked != null ? linked.getRobotItemFilter() : stack -> true;
        startDelegateAI(new AIRobotFetchItem(robot, 250, filter, robot.getZoneToWork()));
    }

    @Override
    public void update() {
        fetchNewItem();
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotFetchItem) {
            if (ai.success()) {
                // if we find an item - that may have been cancelled.
                // let's try to get another one
                fetchNewItem();
            } else if (robot.containsItems()) {
                startDelegateAI(new AIRobotGotoStationAndUnload(robot));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotGotoStationAndUnload) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotPickerNBT.INSTANCE;
    }
}
