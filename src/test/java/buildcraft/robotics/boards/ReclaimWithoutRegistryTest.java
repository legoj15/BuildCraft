/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.MockRobotAccess;

/** The re-claim a reloaded job runs in {@code preempt} goes through {@code robot.getRegistry()}, which is null when
 *  the robot's level has no registry (a client-side robot, or a robot whose level is gone). Every re-claim site must
 *  skip the check then - as {@code BoardRobotGenericSearchBlock} does - instead of throwing out of the AI cycle, which
 *  aborts the robot's whole AI tree. The remembered job is kept: there is no registry to lose it to. */
public class ReclaimWithoutRegistryTest extends VanillaSetupBaseTester {

    private static final BlockPos BLOCK = new BlockPos(3, 64, 3);

    /** A robot whose level has no robot registry. */
    private static final class NoRegistryRobot extends MockRobotAccess {
        @Override
        public IRobotRegistry getRegistry() {
            return null;
        }
    }

    private static CompoundTag blockFoundTag() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("blockFound", new int[] { BLOCK.getX(), BLOCK.getY(), BLOCK.getZ() });
        return tag;
    }

    @Test
    public void aReloadedFlightSkipsTheReclaimWithoutARegistry() {
        AIRobotSearchAndGotoBlock flight = new AIRobotSearchAndGotoBlock(new NoRegistryRobot());
        flight.loadSelfFromNBT(blockFoundTag());

        Assertions.assertDoesNotThrow(() -> flight.preempt(null), "no registry means nothing to re-claim against");
        Assertions.assertEquals(BLOCK, flight.getBlockFound(), "...and nothing to lose the block to");
    }

    @Test
    public void reloadedActionBoardsSkipTheReclaimWithoutARegistry() {
        checkActionBoard("farmer", BoardRobotFarmer::new, b -> ((BoardRobotFarmer) b).blockFound());
        checkActionBoard("planter", BoardRobotPlanter::new, b -> ((BoardRobotPlanter) b).blockFound());
        checkActionBoard("pump", BoardRobotPump::new, b -> ((BoardRobotPump) b).blockFound());
    }

    private static void checkActionBoard(String name, Function<MockRobotAccess, RedstoneBoardRobot> factory,
            Function<RedstoneBoardRobot, BlockPos> blockFound) {
        NoRegistryRobot robot = new NoRegistryRobot();
        RedstoneBoardRobot board = factory.apply(robot);
        board.loadSelfFromNBT(blockFoundTag());
        AIRobot resumedAction = new AIRobot(robot);

        Assertions.assertDoesNotThrow(() -> board.preempt(resumedAction),
                name + ": no registry means nothing to re-claim against");
        Assertions.assertEquals(BLOCK, blockFound.apply(board), name + ": ...and nothing to lose the block to");
    }

    @Test
    public void aReloadedDeliverySkipsTheReclaimWithoutARegistry() {
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

        BoardRobotDelivery board = new BoardRobotDelivery(new NoRegistryRobot());
        board.loadSelfFromNBT(self);
        Assertions.assertNotNull(board.currentRequest(), "precondition: the board reloads its order");

        Assertions.assertDoesNotThrow(() -> board.preempt(null), "no registry means nothing to re-claim against");
        Assertions.assertNotNull(board.currentRequest(), "...and nothing to lose the order to");
    }
}
