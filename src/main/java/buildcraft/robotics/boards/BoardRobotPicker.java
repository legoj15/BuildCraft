/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.Entity;

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
 *  nothing held it goes to sleep. The "this item is being fetched" table keys on {@code ItemEntity.getUUID()}
 *  (D4) rather than 7.1.x's recycled {@code int} ids. */
public class BoardRobotPicker extends RedstoneBoardRobot {

    /** Items currently being fetched, keyed by the dropped item's UUID (globally unique, so one table serves
     *  every dimension), mapped to the fetch that claimed it. 7.1.x kept a bare id SET, released only by the
     *  fetch's {@code end()} — which never runs when the robot's chunk unloads mid-fetch or the robot is
     *  killed, so such a drop stayed untouchable for every robot until the server restarted (the reloaded
     *  robot included: its fetch AI is not saved). Remembering the claimant lets a claim die with its robot.
     *  Server-thread only. Cleared on server start. */
    private static final Map<UUID, AIRobotFetchItem> TARGETS = new HashMap<>();

    public BoardRobotPicker(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** @return true if a robot that is still in the world is fetching the item with this UUID. */
    public static boolean isTargetted(UUID item) {
        AIRobotFetchItem claimant = TARGETS.get(item);
        if (claimant == null) {
            return false;
        }
        if (isStale(claimant)) {
            TARGETS.remove(item);
            return false;
        }
        return true;
    }

    /** Claims {@code item} for {@code claimant}, dropping every claim whose robot has left the world so the
     *  table cannot accumulate dead robots. */
    public static void claimTarget(UUID item, AIRobotFetchItem claimant) {
        TARGETS.values().removeIf(BoardRobotPicker::isStale);
        TARGETS.put(item, claimant);
    }

    /** Releases {@code item} only if {@code claimant} still holds it — a fetch whose stale claim was taken over
     *  by another robot must not free the new holder's lock when it finally ends. */
    public static void releaseTarget(UUID item, AIRobotFetchItem claimant) {
        TARGETS.remove(item, claimant);
    }

    private static boolean isStale(AIRobotFetchItem claimant) {
        return claimant.robot instanceof Entity robot && robot.isRemoved();
    }

    public static void onServerStart() {
        TARGETS.clear();
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
