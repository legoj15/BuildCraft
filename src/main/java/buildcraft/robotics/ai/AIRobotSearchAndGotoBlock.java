/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.path.IBlockFilter;

/** Composes {@link AIRobotSearchBlock} (find) and {@link AIRobotGotoBlock} (fly the found path), reporting
 *  the target through {@link #getBlockFound()} and the registry reservation through the search's
 *  {@code takeResource()}. Ported from 7.1.x {@code AIRobotSearchAndGotoBlock}.
 *
 *  <p>Red-baseline skeleton: the compose-logic lands with the search AI in the foundations commit; until
 *  then the inherited {@code update()} terminates on the first cycle.</p> */
public class AIRobotSearchAndGotoBlock extends AIRobot {

    private BlockPos blockFound;
    private boolean taken;

    private IBlockFilter filter;
    private boolean random;
    private double maxDistanceToEnd;

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot, boolean iRandom, IBlockFilter iFilter) {
        this(iRobot, iRandom, iFilter, 64);
    }

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot, boolean iRandom, IBlockFilter iFilter,
            double iMaxDistanceToEnd) {
        super(iRobot);

        filter = iFilter;
        random = iRandom;
        maxDistanceToEnd = iMaxDistanceToEnd;
    }

    public BlockPos getBlockFound() {
        return blockFound;
    }

    /** Reserves the found block (search's {@code takeResource()}); the caller releases it via the registry
     *  when done. */
    public boolean takeResource() {
        // Red-baseline skeleton — search.takeResource() + latch in the foundations commit.
        return false;
    }

    @Override
    public boolean success() {
        return blockFound != null;
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (blockFound != null) {
            int[] arr = {blockFound.getX(), blockFound.getY(), blockFound.getZ()};
            nbt.putIntArray("blockFound", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "blockFound", null);
        if (arr != null && arr.length == 3) {
            blockFound = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
