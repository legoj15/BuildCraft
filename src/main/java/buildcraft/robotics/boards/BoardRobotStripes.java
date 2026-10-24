/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.BlockClaims;
import buildcraft.robotics.JobClaim;
import buildcraft.robotics.ai.AIRobotDisposeItems;
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
 *  their action AIs resume, and re-claim the block before they move. All of them hold the cell through the same
 *  {@link JobClaim}; this board picks {@link JobClaim.OnReload#FORGET}.
 *
 *  <p><b>An item nothing can use</b> (7.1.x looped on it for ever, flying cell to cell with every other item in the
 *  supply chest starved behind it): a single failed use proves nothing - a seed only takes on farmland, a bucket
 *  needs somewhere to pour - so failed uses are counted in a row, and after {@value #GIVE_UP_AFTER_FAILED_USES} of
 *  them the board gives the item back: it goes into the robot's cargo and {@link AIRobotDisposeItems} unloads it at
 *  a station or, with none that will take it, drops it. The item is then refused by the fetch filter for the rest of
 *  the session, so a supply chest that took it back does not simply hand it over again. Neither the count nor the
 *  refused items are saved: a reloaded board spends at most one more round of failures to learn it again.
 *
 *  <p><b>A use a protection mod refused</b> ({@link AIRobotStripesHandler#refused()}, for the robot's deployer or
 *  its home station's owner) is not a failed use: it says nothing about the item, and counting it would give a good
 *  item up inside a claim. The cell is remembered in a {@link WorkRefusals}, as the farmer and planter remember
 *  theirs, so the search leaves it alone while it stays refused, and {@link WorkRefusals#GIVE_UP_AFTER} refusals
 *  in a row send the robot to sleep for a while. Transient as well. */
public class BoardRobotStripes extends RedstoneBoardRobot {

    /** Failed uses in a row after which the held item is given up on: ten random empty cells, each a flight and a
     *  60-cycle use, is long enough that an item with any use at all has worked somewhere. */
    static final int GIVE_UP_AFTER_FAILED_USES = 10;

    /** {@link JobClaim.OnReload#FORGET}: the cell is never saved (see the class doc). */
    private final JobClaim<BlockPos> blockFound = JobClaim.block(robot, JobClaim.OnReload.FORGET);

    /** Uses of the current item that no handler took, since the last one that one did (or the last item fetched). */
    private int failedUses;

    /** One-item copies of the stacks given up on this session; the fetch filter never equips a match again. */
    private final List<ItemStack> refused = new ArrayList<>();

    /** Cells a protection mod refused this robot, skipped by the search, and the run of refusals that sends it to
     *  sleep; the farmer's and planter's memory (see the class doc). */
    private final WorkRefusals refusals = new WorkRefusals(robot);

    public BoardRobotStripes(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotStripesNBT.INSTANCE;
    }

    /** The fetch filter: any item, bar the ones this board has given up on (same item and components, any count). */
    boolean worthEquipping(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemStack given : refused) {
            if (ItemStack.isSameItemSameComponents(given, stack)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void update() {
        if (robot.getHeldItem().isEmpty()) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot, this::worthEquipping));
        } else {
            startDelegateAI(new AIRobotSearchAndGotoBlock(robot, true, pos ->
                    robot.level().isEmptyBlock(pos)
                            && !BlockClaims.isClaimed(robot.getRegistry(), pos)
                            && !refusals.isRefused(robot.level(), pos)));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAI) {
            if (searchAI.success()) {
                blockFound.adopt(searchAI.getBlockFound(), searchAI.holdsReservation());
                startDelegateAI(new AIRobotStripesHandler(robot, blockFound.job()));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotFetchAndEquipItemStack) {
            if (ai.success()) {
                failedUses = 0;
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotStripesHandler stripesAI) {
            BlockPos used = blockFound.job();
            releaseBlockFound();
            if (stripesAI.success()) {
                failedUses = 0;
                refusals.succeeded();
            } else if (stripesAI.refused()) {
                // Not the item's fault: the cell is skipped while it stays refused, and a run of refusals sends the
                // robot to sleep, leaving the count of unusable uses where it was.
                if (refusals.failed(robot.level(), used)) {
                    startDelegateAI(new AIRobotGotoSleep(robot));
                }
            } else if (++failedUses >= GIVE_UP_AFTER_FAILED_USES) {
                giveUpHeldItem();
            }
        }
    }

    /** Takes the item out of the robot's hand for good: refused by the fetch filter from now on, and into the cargo
     *  slots to be disposed of. A cargo that cannot take it all (the four slots are full) drops the rest at the
     *  robot's feet straight away. */
    private void giveUpHeldItem() {
        failedUses = 0;
        ItemStack held = robot.getHeldItem();
        if (held.isEmpty()) {
            return;
        }
        refused.add(held.copyWithCount(1));
        robot.setItemInUse(ItemStack.EMPTY);

        ItemStack left = robot.getTransactor().insert(held, false, false);
        if (!left.isEmpty()) {
            robot.level().addFreshEntity(new ItemEntity(robot.level(),
                    robot.position().x, robot.position().y, robot.position().z, left));
        }
        if (robot.containsItems()) {
            startDelegateAI(new AIRobotDisposeItems(robot));
        }
    }

    /** The cell the stripes handler is using, or null between uses. */
    BlockPos blockFound() {
        return blockFound.job();
    }

    /** A cell the search reported without holding it is re-claimed before the handler uses it, or given up
     *  ({@link JobClaim#preempt}) - the one rule every board follows. */
    @Override
    public void preempt(AIRobot ai) {
        blockFound.preempt(this, ai);
    }

    private void releaseBlockFound() {
        blockFound.release();
    }

    /** Releases AND forgets the block: the same board instance resumes after an abort (a recharge, say), and a
     *  remembered block it no longer holds would be released a second time, by then possibly from under the robot
     *  that took it meanwhile. */
    @Override
    public void end() {
        releaseBlockFound();
    }
}
