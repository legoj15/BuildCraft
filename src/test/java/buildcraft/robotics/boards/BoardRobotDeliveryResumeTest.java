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
import buildcraft.robotics.ai.MockRobotAccess;

/** What a delivery robot does with the cargo it was carrying when its board resumes (after a recharge, a gate's
 *  go-to-station override, a shutdown that powered back up): it disposes of it first - unloads it at a station, or
 *  drops it - and only then looks for an order. That is 7.1.x's behaviour, kept on purpose: its board's
 *  {@code update()} opened with the same {@code containsItems()} branch, and the recharge that aborted the board
 *  released the order it was filling, and the board re-loaded for it after the disposal. (The port keeps the order
 *  too, but takes it back owner-checked before working it, or gives it up if another robot took it meanwhile - see
 *  {@code ReloadedJobReservationTester}.) Keeping the cargo for a re-taken order would be a new feature, not a
 *  repair. */
public class BoardRobotDeliveryResumeTest extends VanillaSetupBaseTester {

    /** A robot that carries something and counts how often anything asks how big its inventory is: only the
     *  cargo-disposal path (which empties the inventory slot by slot when no station will take it) does. */
    private static final class CargoRobot extends MockRobotAccess {
        int inventoryReads;

        @Override
        public boolean containsItems() {
            return true;
        }

        @Override
        public int getInventorySize() {
            inventoryReads++;
            return 0;
        }
    }

    private static CompoundTag rememberedOrder() {
        CompoundTag request = new CompoundTag();
        request.putInt("slot", 0);
        CompoundTag stationIndex = new CompoundTag();
        stationIndex.putInt("i", 1);
        stationIndex.putInt("j", 64);
        stationIndex.putInt("k", 1);
        request.put("stationIndex", stationIndex);
        request.putByte("stationSide", (byte) 1);
        CompoundTag self = new CompoundTag();
        self.put("currentRequest", request);
        return self;
    }

    @Test
    public void aResumedBoardDisposesOfItsCargoBeforeAnythingElse() {
        CargoRobot robot = new CargoRobot();
        BoardRobotDelivery board = new BoardRobotDelivery(robot);
        board.loadSelfFromNBT(rememberedOrder());
        Assertions.assertNotNull(board.currentRequest(), "precondition: the board still remembers an order");

        board.update();

        Assertions.assertTrue(robot.inventoryReads > 0,
                "the carried cargo is disposed of first, even with an order remembered (7.1.x's order of play)");
    }
}
