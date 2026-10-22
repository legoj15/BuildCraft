/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotRegistryProvider;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.ResourceIdRequest;
import buildcraft.api.robots.RobotManager;
import buildcraft.robotics.StackRequest;
import buildcraft.robotics.ai.AIRobotSearchStackRequest;
import buildcraft.robotics.ai.MockRobotAccess;

/** The delivery board adopts the order its search reserved and takes the requester's station for it. Every other
 *  reader of an order's station ({@code OrderClaimer}'s reclaim and release, {@code getResourceId}) tolerates the
 *  station being gone; the adopt was the one that dereferenced it blind. The live search always hands over an order
 *  whose station it just set, so this is a guard, not a reachable crash - pinned here with an order whose station does
 *  not resolve (the by-value link a reloaded order has, against a registry that no longer holds the station). Giving
 *  such an order up must still free the reservation the search took: its id is the station's position and side, which
 *  the order keeps by value, so a station that is gone cannot leave the order reserved for nobody. */
public class BoardRobotDeliveryAdoptTest extends VanillaSetupBaseTester {

    private static CompoundTag savedOrder() {
        CompoundTag request = new CompoundTag();
        request.putInt("slot", 0);
        CompoundTag stationIndex = new CompoundTag();
        stationIndex.putInt("i", 1);
        stationIndex.putInt("j", 64);
        stationIndex.putInt("k", 1);
        request.put("stationIndex", stationIndex);
        request.putByte("stationSide", (byte) 1);
        return request;
    }

    @Test
    public void anAdoptedOrderWhoseStationIsGoneIsGivenUpInsteadOfThrowing() {
        IRobotRegistryProvider previous = RobotManager.registryProvider;
        MockRobotAccess robot = new MockRobotAccess();
        // A registry that holds no station at all (the order's by-value link resolves to nothing) but still holds the
        // order's reservation for this robot, as the search left it.
        Set<ResourceId> reserved = new HashSet<>();
        reserved.add(new ResourceIdRequest(new BlockPos(1, 64, 1), Direction.UP, 0));
        MockRobotAccess.InertRobotRegistry registry = new MockRobotAccess.InertRobotRegistry() {
            @Override
            public long robotIdTaking(ResourceId resourceId) {
                return reserved.contains(resourceId) ? robot.getRobotId() : EntityRobotBase.NULL_ROBOT_ID;
            }

            @Override
            public void release(ResourceId resourceId) {
                reserved.remove(resourceId);
            }
        };
        robot.setRegistry(registry);
        RobotManager.registryProvider = world -> registry;
        try {
            BoardRobotDelivery board = new BoardRobotDelivery(robot);
            AIRobotSearchStackRequest search = new AIRobotSearchStackRequest(robot);
            search.request = StackRequest.loadFromNBT(savedOrder());
            Assertions.assertNotNull(search.request, "precondition: the order loads");
            Assertions.assertTrue(search.success(), "precondition: the search reports the order as found");
            Assertions.assertNull(search.request.getStation(null), "precondition: its station no longer resolves");

            Assertions.assertDoesNotThrow(() -> board.delegateAIEnded(search),
                    "an order with no station must not NPE the board's search-end handler");

            Assertions.assertNull(board.currentRequest(),
                    "the order is given up, not kept: there is no requester to deliver to");
            Assertions.assertTrue(reserved.isEmpty(),
                    "giving the order up frees its reservation although the station is gone: " + reserved);
        } finally {
            RobotManager.registryProvider = previous;
        }
    }
}
