/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.IRobotRegistryProvider;
import buildcraft.api.robots.RobotManager;
import buildcraft.robotics.StackRequest;
import buildcraft.robotics.ai.AIRobotSearchStackRequest;
import buildcraft.robotics.ai.MockRobotAccess;

/** The delivery board adopts the order its search reserved and takes the requester's station for it. Every other
 *  reader of an order's station ({@code OrderClaimer}'s reclaim and release, {@code getResourceId}) tolerates the
 *  station being gone; the adopt was the one that dereferenced it blind. The live search always hands over an order
 *  whose station it just set, so this is a guard, not a reachable crash - pinned here with an order whose station does
 *  not resolve (the by-value link a reloaded order has, against a registry that no longer holds the station). */
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
        // A registry that holds no station at all: the order's by-value link resolves to nothing.
        RobotManager.registryProvider = world -> new MockRobotAccess.InertRobotRegistry();
        try {
            MockRobotAccess robot = new MockRobotAccess();
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
        } finally {
            RobotManager.registryProvider = previous;
        }
    }
}
