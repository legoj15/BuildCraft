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

    private void releaseCurrentRequest() {
        if (currentRequest != null) {
            robot.getRegistry().release(currentRequest.getResourceId(robot.level()), robot.getRobotId());
            DockingStation station = currentRequest.getStation(robot.level());
            if (station != null) {
                station.release((EntityRobotBase) robot);
            }
            currentRequest = null;
            currentRequestClaimed = false;
        }
    }

    /** Runs before the delegate on every cycle. A board reloaded mid-order remembers the order, but the unload freed
     *  its reservation: re-claim it before the resumed load or delivery moves, or - when another robot took the
     *  order meanwhile, or its station is gone - drop it and stop, rather than fill one order twice. Not 7.1.x,
     *  which delivered unreserved. */
    @Override
    public void preempt(AIRobot ai) {
        if (currentRequest != null && !currentRequestClaimed) {
            if (robot.getRegistry().reclaim(currentRequest.getResourceId(robot.level()), robot.getRobotId())) {
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
