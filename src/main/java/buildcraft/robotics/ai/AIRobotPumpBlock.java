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
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.fluid.FluidResource;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.BlockUtil;

/** Pumps a bucket of fluid out of the source block at {@code blockToPump} (through the in-tree
 *  {@code BlockUtil.drainBlock}) into the robot's tank. Ported from 7.1.x {@code AIRobotPumpBlock}
 *  (7.1.x pumped through the robot's fluid transactor; the modern equivalent is
 *  {@link IRobotAccess#getFluidHandler()}).
 *
 *  <p>7.1.x's pump simulated the fill and then drained the block WITHOUT actually filling the robot —
 *  and never set its {@code pumped} counter, so its {@code success()} was permanently false. The pump
 *  board ignored the success (it just released the block and re-searched), so the bug was inert; this
 *  port tanks the bucket for real and reports it, per the design doc. One bucket per pass: a partially
 *  full tank (room &lt; 1000) fails the pass, and the board's tank check routes the robot to unload first. */
public class AIRobotPumpBlock extends AIRobot {

    private static final int BUCKET = 1000;

    private BlockPos blockToPump;
    private long waited = 0;
    private int pumped = 0;

    public AIRobotPumpBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotPumpBlock(IRobotAccess iRobot, BlockPos iBlockToPump) {
        super(iRobot);

        blockToPump = iBlockToPump;
    }

    @Override
    public void start() {
        if (blockToPump != null) {
            robot.aimItemAt(blockToPump);
        }
    }

    @Override
    public void update() {
        if (waited < 40) {
            waited++;
            return;
        }

        if (blockToPump != null && robot.level() instanceof ServerLevel serverLevel) {
            FluidStack preview = BlockUtil.drainBlock(serverLevel, blockToPump, false);
            if (preview != null) {
                //? if >=1.21.10 {
                FluidResource resource = FluidResource.of(preview);
                int room = robot.getFluidHandler().getCapacityAsInt(0, resource)
                        - robot.getFluidHandler().getAmountAsInt(0);
                if (room >= BUCKET) {
                    robot.getFluidHandler().insert(0, resource, BUCKET, null);
                    BlockUtil.drainBlock(serverLevel, blockToPump, true);
                    pumped = BUCKET;
                }
                //?} else {
                /*int room = robot.getFluidHandler().fill(preview, IFluidHandler.FluidAction.SIMULATE);
                if (room >= BUCKET) {
                    robot.getFluidHandler().fill(preview, IFluidHandler.FluidAction.EXECUTE);
                    BlockUtil.drainBlock(serverLevel, blockToPump, true);
                    pumped = BUCKET;
                }*/
                //?}
            }
        }
        terminate();
    }

    @Override
    public boolean success() {
        return pumped > 0;
    }

    @Override
    public long getPowerCost() {
        // Ph5 cost table: 5 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 500_000;
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (blockToPump != null) {
            int[] arr = {blockToPump.getX(), blockToPump.getY(), blockToPump.getZ()};
            nbt.putIntArray("blockToPump", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "blockToPump", null);
        if (arr != null && arr.length == 3) {
            blockToPump = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
