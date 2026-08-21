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
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;

/** The planter: keeps a seed equipped (fetching one from the station when none is held), searches for
 *  a spot that is not {@code "replaceable"} and can sustain the planted seed, then plants it with
 *  {@code AIRobotPlant}. Ported from 7.1.x {@code BoardRobotPlanter} (the 7.1.x gate filter is Ph6,
 *  so the effective block filter is {@code !replaceable && canSustainPlant && !isTaken} — D1).
 *
 *  <p>Red-baseline skeleton: the seed predicate and the search/plant wiring land in the AI step; until
 *  then {@link #update()} inherits the base terminate-on-cycle.</p> */
public class BoardRobotPlanter extends RedstoneBoardRobot {

    private BlockPos blockFound;

    public BoardRobotPlanter(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotPlanterNBT.INSTANCE;
    }

    /** The seed predicate — {@code CropManager.isSeed} in the AI step. */
    public boolean matchesSeed(ItemStack stack) {
        // Red-baseline skeleton.
        return false;
    }

    @Override
    public void end() {
        releaseBlockFound();
    }

    protected BlockPos blockFound() {
        return blockFound;
    }

    private void releaseBlockFound() {
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
