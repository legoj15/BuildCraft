/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.StackUtil;
import buildcraft.robotics.StackRequest;

/** Flies to the station owning {@code requested} and offers the matching cargo into the provider's request
 *  slot. Ported from 7.1.x {@code AIRobotDeliverRequested}: the offer is the provider's own
 *  {@link IRequestProvider#offerItem} (which caps at the requested quantity and returns the excess), so
 *  the robot only gives up as much as the request still wants. 7.1.x left a "make this not exceed the
 *  requested amount" TODO against this AI — the cap already lives in the provider, so nothing to fix here.
 *  One deliberate hardening over 7.1.x: a provider whose request count has shrunk below the saved slot
 *  index (the request grid was edited while the robot flew) fails cleanly instead of throwing. */
public class AIRobotDeliverRequested extends AIRobot {

    private StackRequest requested;

    public AIRobotDeliverRequested(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotDeliverRequested(IRobotAccess robot, StackRequest request) {
        this(robot);

        requested = request;
    }

    @Override
    public void start() {
        if (requested != null) {
            startDelegateAI(new AIRobotGotoStation(robot, requested.getStation(robot.level())));
        } else {
            setSuccess(false);
            terminate();
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStation) {
            if (!ai.success()) {
                setSuccess(false);
                terminate();
                return;
            }

            IRequestProvider requester = requested.getRequester(robot.level());
            if (requester == null || requester.getRequestsCount() <= requested.getSlot()) {
                setSuccess(false);
                terminate();
                return;
            }

            int count = 0;

            for (int i = 0; i < robot.getInventorySize(); i++) {
                ItemStack current = robot.getInventoryStack(i);
                if (current.isEmpty() || !StackUtil.isMatchingItem(current, requested.getStack())) {
                    continue;
                }
                int before = current.getCount();
                ItemStack leftover = requester.offerItem(requested.getSlot(), current.copy());

                if (leftover.isEmpty()) {
                    robot.setInventoryStack(i, ItemStack.EMPTY);
                    count += before;
                } else if (leftover.getCount() != before) {
                    robot.setInventoryStack(i, leftover);
                    count += before - leftover.getCount();
                }
            }

            setSuccess(count > 0);
            terminate();
        }
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (requested != null) {
            CompoundTag requestNBT = new CompoundTag();
            requested.writeToNBT(requestNBT);
            nbt.put("currentRequest", requestNBT);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);
        if (nbt.contains("currentRequest")) {
            requested = StackRequest.loadFromNBT(NbtApiUtil.getCompound(nbt, "currentRequest"));
        }
    }
}
