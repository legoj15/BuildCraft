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
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;

/** The search-board base: each cycle, search for the nearest block matching {@link #isExpectedBlock}
 *  (and not already taken by another robot), remember it as {@code blockFound}, and let the subclass act
 *  on it. Ported from 7.1.x {@code BoardRobotGenericSearchBlock}; the 7.1.x gate filter
 *  ({@code updateFilter}/{@code matchesGateFilter} reading the station's statements) is Ph6, so the
 *  effective block filter is {@code isExpectedBlock && !isTaken} (D1).
 *
 *  <p>Red-baseline skeleton: the {@code AIRobotSearchAndGotoBlock} wiring lands in the AI step; until
 *  then {@link #update()} inherits the base terminate-on-cycle and no block is ever found.</p> */
public abstract class BoardRobotGenericSearchBlock extends RedstoneBoardRobot {

    private BlockPos blockFound;

    public BoardRobotGenericSearchBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** The block predicate this board hunts for. Called with the live state of each candidate cell;
     *  must be pure-state (the search may evaluate it from a background pathfind step). */
    public abstract boolean isExpectedBlock(BlockState state);

    @Override
    public void update() {
        // Red-baseline skeleton — AIRobotSearchAndGotoBlock(robot, false,
        // pos -> isExpectedBlock(state) && !registry.isTaken(ResourceIdBlock) && gate) in the AI step.
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        // Red-baseline skeleton — SearchAndGotoBlock success -> blockFound, failure -> AIRobotGotoSleep,
        // in the AI step.
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
