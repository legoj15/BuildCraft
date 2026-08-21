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

/** The farmer: keeps a hoe equipped (fetching one from the station when none is held), searches for
 *  tilled-able {@code "dirt"} with air above it, then tills it with {@code AIRobotUseToolOnBlock}.
 *  Ported from 7.1.x {@code BoardRobotFarmer} (7.1.x's tool check was {@code ItemHoe}; the modern
 *  equivalent is {@code instanceof HoeItem}).
 *
 *  <p>Red-baseline skeleton: the hoe predicate and the search/till wiring land in the AI step; until
 *  then {@link #update()} inherits the base terminate-on-cycle.</p> */
public class BoardRobotFarmer extends RedstoneBoardRobot {

    private BlockPos blockFound;

    public BoardRobotFarmer(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotFarmerNBT.INSTANCE;
    }

    /** The hoe predicate — {@code instanceof HoeItem} in the AI step. */
    public boolean isExpectedHoe(ItemStack stack) {
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
