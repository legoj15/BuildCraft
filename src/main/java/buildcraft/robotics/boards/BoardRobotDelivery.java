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
import buildcraft.robotics.JobClaim;
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

    /** The order being filled, or none between orders. Its claim is the request's reservation AND the requester's
     *  station; saved (the board's {@code update()} resumes the load for it), the claims are not. */
    private final JobClaim<StackRequest> currentRequest = new JobClaim<>(robot, JobClaim.OnReload.RECLAIM,
            new OrderClaimer());

    public BoardRobotDelivery(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** An order's two claims: the request's registry reservation and the requester's station. */
    private static final class OrderClaimer implements JobClaim.Claimer<StackRequest> {
        /** Both, owner-checked, or neither: a reservation re-taken for a station another robot holds goes back. */
        @Override
        public boolean reclaim(IRobotAccess robot, IRobotRegistry registry, StackRequest order) {
            DockingStation station = order.getStation(robot.level());
            if (station == null || !registry.reclaim(order.getResourceId(robot.level()), robot.getRobotId())) {
                return false;
            }
            // take() needs the concrete entity (AIRobotGotoStation's rationale): a live board only ever drives a
            // real robot.
            if (station.take((EntityRobotBase) robot)) {
                return true;
            }
            registry.release(order.getResourceId(robot.level()), robot.getRobotId());
            return false;
        }

        /** The station only when it may go: not when it is the robot's home, and not while the robot is docked there
         *  (the delivery flight docks it at the requester), since that claim is the robot's dock now and undocking is
         *  what frees it - as {@code AIRobotGotoStation.end()} leaves it. */
        @Override
        public void release(IRobotAccess robot, IRobotRegistry registry, StackRequest order) {
            registry.release(order.getResourceId(robot.level()), robot.getRobotId());
            DockingStation station = order.getStation(robot.level());
            if (station != null && station.robotIdTaking() == robot.getRobotId() && station.canRelease()) {
                station.release((EntityRobotBase) robot);
            }
        }
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

        StackRequest order = currentRequest.job();
        if (order == null) {
            startDelegateAI(new AIRobotSearchStackRequest(robot,
                    ActionRobotFilter.getGateFilter(robot.getLinkedStation()), deliveryBlacklist));
        } else {
            startDelegateAI(new AIRobotGotoStationAndLoad(robot, new IStackFilter() {
                @Override
                public boolean matches(ItemStack stack) {
                    StackRequest current = currentRequest.job();
                    // The order's stack is the FILTER (a List template names what it wants), the candidate second.
                    return current != null && StackUtil.isMatchingItemOrList(current.getStack(), stack);
                }
            }, order.getStack().getCount()));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchStackRequest) {
            if (!ai.success()) {
                deliveryBlacklist.clear();
                startDelegateAI(new AIRobotGotoSleep(robot));
            } else {
                // The search reserved the order before reporting it.
                StackRequest order = ((AIRobotSearchStackRequest) ai).request;
                currentRequest.adopt(order, true);

                // take() needs the concrete entity (AIRobotGotoStation's rationale): a live board only
                // ever drives a real robot.
                if (!order.getStation(robot.level()).take((EntityRobotBase) robot)) {
                    releaseCurrentRequest();
                }
            }
        } else if (ai instanceof AIRobotGotoStationAndLoad) {
            if (!ai.success()) {
                if (currentRequest.job() != null) {
                    deliveryBlacklist.add(currentRequest.job().getStack());
                }
                releaseCurrentRequest();
            } else {
                startDelegateAI(new AIRobotDeliverRequested(robot, currentRequest.job()));
            }
        } else if (ai instanceof AIRobotDeliverRequested) {
            releaseCurrentRequest();
        }
    }

    /** The order this board is working, or null between orders. */
    StackRequest currentRequest() {
        return currentRequest.job();
    }

    /** An ended board - most often {@code AIRobotMain} aborting it for a recharge (whose start frees every
     *  reservation anyway), a gate's go-to-station override, or a shutdown - gives its order's claims back but keeps
     *  the order, as 7.1.x's board kept it. The same board instance resumes afterwards, and {@link #preempt} then takes
     *  the order back if it is still free, or gives it up to the robot that took it meanwhile. (The cargo it carried
     *  is disposed of on resume either way, as in 7.1.x: see {@link #update}.) */
    @Override
    public void end() {
        currentRequest.releaseClaims();
    }

    /** Gives up the order: its claims and the board's memory of it. */
    private void releaseCurrentRequest() {
        currentRequest.release();
    }

    /** Runs before the delegate on every cycle. A board that comes back to an order without holding it - reloaded
     *  mid-order (the unload freed its claims), or resumed after an abort ({@link #end}) - takes the order and the
     *  requester's station again before the resumed load or delivery moves; when another robot holds either, or the
     *  station is gone, it gives the order up and stops, rather than fill one order twice ({@link JobClaim#preempt}).
     *  Not 7.1.x, which went on delivering unreserved. */
    @Override
    public void preempt(AIRobot ai) {
        currentRequest.preempt(this, ai);
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (currentRequest.shouldSave()) {
            CompoundTag requestNBT = new CompoundTag();
            currentRequest.job().writeToNBT(requestNBT);
            nbt.put("currentRequest", requestNBT);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);
        if (nbt.contains("currentRequest")) {
            currentRequest.restore(StackRequest.loadFromNBT(
                    buildcraft.api.core.NbtApiUtil.getCompound(nbt, "currentRequest")));
        }
    }
}
