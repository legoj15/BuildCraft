/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import net.minecraft.core.BlockPos;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.AIRobotStripesHandler;

/** The stripes board: equips ANY item from a station, flies to a random empty (unreserved) cell, and
 *  uses the held item there through the stripes handler registry — a mobile stripes pipe. Ported from
 *  7.1.x {@code BoardRobotStripes}; the reservation lifecycle rides the port's
 *  {@link AIRobotSearchAndGotoBlock}, which takes the found cell's reservation on search success and
 *  leaves releasing it to this board (7.1.x's AI did the same through {@code takeResource}).
 *
 *  <p>The block is the cell the stripes handler is using at this moment and is deliberately NOT saved. The handler
 *  ({@link AIRobotStripesHandler}) is not resumable, so a board reloaded mid-use has nothing to continue on that
 *  cell, and the unload freed its reservation: carrying the block into the new session would only leave a stale
 *  cell to save again and to release (a second time) at the end. The farmer, planter and pump keep theirs because
 *  their action AIs resume, and re-claim the block before they move. */
public class BoardRobotStripes extends RedstoneBoardRobot {

    private BlockPos blockFound;

    public BoardRobotStripes(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotStripesNBT.INSTANCE;
    }

    @Override
    public void update() {
        if (robot.getHeldItem().isEmpty()) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot, stack -> !stack.isEmpty()));
        } else {
            startDelegateAI(new AIRobotSearchAndGotoBlock(robot, true, pos ->
                    robot.level().isEmptyBlock(pos)
                            && !robot.getRegistry().isTaken(new ResourceIdBlock(pos))));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAI) {
            if (searchAI.success()) {
                blockFound = searchAI.getBlockFound();
                startDelegateAI(new AIRobotStripesHandler(robot, blockFound));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotFetchAndEquipItemStack) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotStripesHandler) {
            releaseBlockFound();
        }
    }

    /** The cell the stripes handler is using, or null between uses. */
    BlockPos blockFound() {
        return blockFound;
    }

    private void releaseBlockFound() {
        if (blockFound != null) {
            robot.getRegistry().release(new ResourceIdBlock(blockFound), robot.getRobotId());
            blockFound = null;
        }
    }

    /** Releases AND forgets the block: the same board instance resumes after an abort (a recharge, say), and a
     *  remembered block it no longer holds would be released a second time, by then possibly from under the robot
     *  that took it meanwhile. */
    @Override
    public void end() {
        releaseBlockFound();
    }
}
