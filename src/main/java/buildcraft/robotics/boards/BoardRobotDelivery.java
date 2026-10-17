/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.lib.misc.StackUtil;
import buildcraft.robotics.StackRequest;
import buildcraft.robotics.ai.AIRobotDeliverRequested;
import buildcraft.robotics.ai.AIRobotDisposeItems;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotSearchStackRequest;
import buildcraft.robotics.statements.ActionRobotFilter;

/** The delivery board, ported from 7.1.x {@code BoardRobotDelivery}: find an open request on the network,
 *  load the requested goods at any station that supplies them, and hand them to the requester. A failed
 *  load blacklists the request's item until the next successful search (so the board does not spin on an
 *  order it cannot fill), and leftover cargo is disposed of before a new order is taken. */
public class BoardRobotDelivery extends RedstoneBoardRobot {

    private final ArrayList<ItemStack> deliveryBlacklist = new ArrayList<>();

    private StackRequest currentRequest = null;

    /** Whether this board holds the registry reservation on {@link #currentRequest} in this session. Not saved: the
     *  unload that saved a mid-delivery board freed it, so {@link #preempt} re-claims it after a reload. */
    private boolean currentRequestClaimed;

    public BoardRobotDelivery(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotDeliveryNBT.INSTANCE;
    }

    @Override
    public void update() {
        if (robot.containsItems()) {
            startDelegateAI(new AIRobotDisposeItems(robot));
            return;
        }

        if (currentRequest == null) {
            startDelegateAI(new AIRobotSearchStackRequest(robot,
                    ActionRobotFilter.getGateFilter(robot.getLinkedStation()), deliveryBlacklist));
        } else {
            startDelegateAI(new AIRobotGotoStationAndLoad(robot, new IStackFilter() {
                @Override
                public boolean matches(ItemStack stack) {
                    return currentRequest != null && StackUtil.isMatchingItemOrList(stack, currentRequest.getStack());
                }
            }, currentRequest.getStack().getCount()));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchStackRequest) {
            if (!ai.success()) {
                deliveryBlacklist.clear();
                startDelegateAI(new AIRobotGotoSleep(robot));
            } else {
                currentRequest = ((AIRobotSearchStackRequest) ai).request;
                // The search reserved the order before reporting it.
                currentRequestClaimed = true;

                // take() needs the concrete entity (AIRobotGotoStation's rationale): a live board only
                // ever drives a real robot.
                if (!currentRequest.getStation(robot.level()).take((EntityRobotBase) robot)) {
                    releaseCurrentRequest();
                }
            }
        } else if (ai instanceof AIRobotGotoStationAndLoad) {
            if (!ai.success()) {
                deliveryBlacklist.add(currentRequest.getStack());
                releaseCurrentRequest();
            } else {
                startDelegateAI(new AIRobotDeliverRequested(robot, currentRequest));
            }
        } else if (ai instanceof AIRobotDeliverRequested) {
            releaseCurrentRequest();
        }
    }

    /** The order this board is working, or null between orders. */
    StackRequest currentRequest() {
        return currentRequest;
    }

    /** An ended board - most often {@code AIRobotMain} aborting it for a recharge (whose start frees every
     *  reservation anyway), a gate's go-to-station override, or a shutdown - gives its order's claims back but keeps
     *  the order, as 7.1.x's board kept it. The same board instance resumes afterwards, and {@link #preempt} then takes
     *  the order back if it is still free, or gives it up to the robot that took it meanwhile. (The cargo it carried
     *  is disposed of on resume either way, as in 7.1.x: see {@link #update}.) */
    @Override
    public void end() {
        releaseClaims();
    }

    /** Gives up the order: its claims and the board's memory of it. */
    private void releaseCurrentRequest() {
        if (currentRequest != null) {
            releaseClaims();
            currentRequest = null;
        }
    }

    /** Gives back what this board holds for its order - the request's reservation and the requester's station - and
     *  keeps the order itself. Both owner-checked; the station only when it may go: not when it is the robot's home,
     *  and not while the robot is docked there (the delivery flight docks it at the requester), since that claim is
     *  the robot's dock now and undocking is what frees it - as {@code AIRobotGotoStation.end()} leaves it. */
    private void releaseClaims() {
        if (currentRequest == null) {
            return;
        }
        IRobotRegistry registry = robot.getRegistry();
        if (registry != null) {
            registry.release(currentRequest.getResourceId(robot.level()), robot.getRobotId());
            DockingStation station = currentRequest.getStation(robot.level());
            if (station != null && station.robotIdTaking() == robot.getRobotId() && station.canRelease()) {
                station.release((EntityRobotBase) robot);
            }
        }
        currentRequestClaimed = false;
    }

    /** Runs before the delegate on every cycle. A board that comes back to an order without holding it - reloaded
     *  mid-order (the unload freed its claims), or resumed after an abort ({@link #end}) - takes the order and the
     *  requester's station again before the resumed load or delivery moves; when another robot holds either, or the
     *  station is gone, it gives the order up and stops, rather than fill one order twice. Not 7.1.x, which went on
     *  delivering unreserved. */
    @Override
    public void preempt(AIRobot ai) {
        if (currentRequest != null && !currentRequestClaimed) {
            IRobotRegistry registry = robot.getRegistry();
            if (registry == null) {
                return;
            }
            DockingStation station = currentRequest.getStation(robot.level());
            if (station != null && registry.reclaim(currentRequest.getResourceId(robot.level()), robot.getRobotId())
                    && station.take((EntityRobotBase) robot)) {
                currentRequestClaimed = true;
            } else {
                releaseCurrentRequest();
                abortDelegateAI();
            }
        }
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (currentRequest != null) {
            CompoundTag requestNBT = new CompoundTag();
            currentRequest.writeToNBT(requestNBT);
            nbt.put("currentRequest", requestNBT);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);
        if (nbt.contains("currentRequest")) {
            currentRequest = StackRequest.loadFromNBT(
                    buildcraft.api.core.NbtApiUtil.getCompound(nbt, "currentRequest"));
        }
    }
}
