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

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;

/** The pump: when its tank holds fluid it offloads it at the station
 *  ({@code AIRobotGotoStationAndUnloadFluids}); otherwise it searches for a
 *  {@code "fluidSource"} block and pumps it with {@code AIRobotPumpBlock}. Ported from 7.1.x
 *  {@code BoardRobotPump} (the 7.1.x gate fluid filter is Ph6, so the effective block filter is
 *  {@code fluidSource && !isTaken} — D1).
 *
 *  <p>Red-baseline skeleton: the tank check and the search/pump/unload wiring land in the AI step;
 *  until then {@link #update()} inherits the base terminate-on-cycle.</p> */
public class BoardRobotPump extends RedstoneBoardRobot {

    private BlockPos blockFound;

    public BoardRobotPump(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotPumpNBT.INSTANCE;
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
