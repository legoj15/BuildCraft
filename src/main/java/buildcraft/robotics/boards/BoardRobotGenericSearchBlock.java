/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;

/** The search-board base: each cycle, search for the nearest block matching {@link #isExpectedBlock}
 *  (and not already taken by another robot), remember it as {@code blockFound}, and let the subclass act
 *  on it. Ported from 7.1.x {@code BoardRobotGenericSearchBlock}; the 7.1.x gate filter
 *  ({@code updateFilter}/{@code matchesGateFilter} reading the station's {@code ActionRobotFilter}
 *  statements) is Ph6, so the effective block filter is {@code isExpectedBlock && !isTaken} (D1) — when the
 *  statements land, the filter gains one more conjunct in this single place.
 *
 *  <p>A failed search sends the robot home to sleep ({@code AIRobotGotoSleep}) and the search starts again
 *  on the next cycle, exactly as 7.1.x. Subclasses act on {@link #blockFound()} in their own
 *  {@code update()} and only fall through to {@code super.update()} when nothing is found yet. */
public abstract class BoardRobotGenericSearchBlock extends RedstoneBoardRobot {

    private BlockPos blockFound;

    public BoardRobotGenericSearchBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** The block predicate this board hunts for. Called with the live state of each candidate cell;
     *  must be pure-state (it is evaluated for every scanned cell, and the state is the only input a
     *  worldless property check gets). */
    public abstract boolean isExpectedBlock(BlockState state);

    /** The neighbour-aware seam the search actually calls. It defaults to the pure-state predicate, which
     *  is right for every board whose property is a function of the candidate state alone (logs, ores,
     *  dirt). A board whose property genuinely reads the surrounding blocks — the harvester, whose
     *  stacking crops are only ripe relative to the block below — overrides this; answering such a
     *  property from the state alone silently reports "no" forever. */
    public boolean isExpectedBlock(BlockGetter access, BlockPos pos) {
        return isExpectedBlock(access.getBlockState(pos));
    }

    @Override
    public void update() {
        Level level = robot.level();
        startDelegateAI(new AIRobotSearchAndGotoBlock(robot, false,
                pos -> level != null && isExpectedBlock(level, pos)
                        && !robot.getRegistry().isTaken(new ResourceIdBlock(pos))));
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAndGoto) {
            if (searchAndGoto.success()) {
                blockFound = searchAndGoto.getBlockFound();
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }

    @Override
    public void end() {
        releaseBlockFound();
    }

    protected BlockPos blockFound() {
        return blockFound;
    }

    /** Releases the registry reservation on the found block (7.1.x did the same in {@code end} and after
     *  a finished action AI). */
    protected void releaseBlockFound() {
        if (blockFound != null) {
            robot.getRegistry().release(new ResourceIdBlock(blockFound));
            blockFound = null;
        }
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (blockFound != null) {
            int[] arr = {blockFound.getX(), blockFound.getY(), blockFound.getZ()};
            nbt.putIntArray("indexStored", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "indexStored", null);
        if (arr != null && arr.length == 3) {
            blockFound = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
