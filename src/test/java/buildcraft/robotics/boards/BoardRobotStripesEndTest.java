/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.AIRobotStripesHandler;
import buildcraft.robotics.ai.MockRobotAccess;

/** The stripes board's block is the cell its handler is using right now, and nothing else about it survives the
 *  handler: an ended board (aborted for a recharge, say) gives the block back and forgets it - the same board
 *  instance resumes afterwards - and a board reloaded mid-use has no handler to resume ({@code AIRobotStripesHandler}
 *  is not saved) and no reservation either (the unload freed it), so it must not carry the block into the new
 *  session. A remembered block would be saved and reloaded forever, and released a second time, by then possibly
 *  from under the robot that took it meanwhile. */
public class BoardRobotStripesEndTest extends VanillaSetupBaseTester {

    private static final BlockPos BLOCK = new BlockPos(1, 64, 1);

    /** A board whose search-and-goto just reported {@link #BLOCK}, so its stripes handler is using it. */
    private static BoardRobotStripes boardUsingTheBlock(MockRobotAccess robot) {
        BoardRobotStripes board = new BoardRobotStripes(robot);
        AIRobotSearchAndGotoBlock flight = new AIRobotSearchAndGotoBlock(robot);
        CompoundTag found = new CompoundTag();
        found.putIntArray("blockFound", new int[] { BLOCK.getX(), BLOCK.getY(), BLOCK.getZ() });
        flight.loadSelfFromNBT(found);
        board.delegateAIEnded(flight);
        Assertions.assertEquals(BLOCK, board.blockFound(), "precondition: the board remembers the block it is using");
        Assertions.assertInstanceOf(AIRobotStripesHandler.class, board.getDelegateAI(),
                "precondition: the stripes handler is using the block");
        return board;
    }

    @Test
    public void endForgetsTheBlockItReleased() {
        BoardRobotStripes board = boardUsingTheBlock(new MockRobotAccess());

        board.end();

        Assertions.assertNull(board.blockFound(), "an ended board must not keep the block it released");
        CompoundTag after = new CompoundTag();
        board.writeSelfToNBT(after);
        Assertions.assertFalse(after.contains("blockFound"), "...nor save it");
    }

    @Test
    public void aReloadedBoardDoesNotCarryTheBlockOfTheLastSession() {
        BoardRobotStripes board = boardUsingTheBlock(new MockRobotAccess());
        CompoundTag saved = new CompoundTag();
        board.writeSelfToNBT(saved);

        BoardRobotStripes reloaded = new BoardRobotStripes(new MockRobotAccess());
        reloaded.loadSelfFromNBT(saved);

        Assertions.assertNull(reloaded.blockFound(),
                "the handler that used the block is not resumed and the unload freed its reservation, so the "
                        + "reloaded board has no block to release or work");
    }

    @Test
    public void aSaveFromBeforeTheBoardStoppedSavingItsBlockIsIgnored() {
        CompoundTag legacy = new CompoundTag();
        legacy.putIntArray("blockFound", new int[] { BLOCK.getX(), BLOCK.getY(), BLOCK.getZ() });

        BoardRobotStripes reloaded = new BoardRobotStripes(new MockRobotAccess());
        reloaded.loadSelfFromNBT(legacy);

        Assertions.assertNull(reloaded.blockFound(), "an older save's block is stale for the same reason");
    }
}
