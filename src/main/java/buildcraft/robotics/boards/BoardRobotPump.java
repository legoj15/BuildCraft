/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.BlockClaims;
import buildcraft.robotics.JobClaim;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.RefusalMemory;
import buildcraft.robotics.RobotProtection;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnloadFluids;
import buildcraft.robotics.ai.AIRobotPumpBlock;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.path.IBlockFilter;

/** The pump: when its tank holds fluid it offloads it at the station
 *  ({@code AIRobotGotoStationAndUnloadFluids}); otherwise it searches for a {@code "fluidSource"} block
 *  and pumps it with {@code AIRobotPumpBlock}. Ported from 7.1.x {@code BoardRobotPump}: the block filter
 *  is {@code fluidSource && !isTaken && matchesGateFilter}, the last term being the home station's gate
 *  fluid filter (7.1.x {@code updateFilter()}), refreshed on every search pass. (Not 7.1.x: the filter also
 *  refuses a miner tube, whose waterlogged water is the machine's own plumbing.)
 *
 *  <p>The 7.1.x pump read its tank through {@code getTankInfo}; the modern equivalent is
 *  {@code robot.getFluidHandler()}. As in 7.1.x, a finished pump pass releases the block regardless of
 *  the pump AI's outcome (7.1.x never checked it either — the modern {@code AIRobotPumpBlock} reports
 *  success honestly now, but the board stays faithful). */
public class BoardRobotPump extends RedstoneBoardRobot {

    private static final int BUCKET = 1000;

    /** The source being pumped. Saved; a reloaded one is re-claimed only while the resumed pump works it. */
    private final JobClaim<BlockPos> blockFound = JobClaim.block(robot, JobClaim.OnReload.RECLAIM_WHILE_WORKING);

    /** The gate fluid filter of the robot's home station, refreshed on every search pass. Null means
     *  "no station, no filter" — pump anything. */
    private IFluidFilter fluidFilter;

    /** Blocks the pump AI was refused (by a protection mod or the block's own pickup), skipped by the search
     *  while they stay unchanged. Keyed on the two players the AI asks for (see {@code RobotProtection}): the
     *  identity the robot acts as and its home station's owner. A refusal says nothing about any other pair.
     *  Policy {@link RefusalMemory#DRAIN}. Transient: a reloaded board asks once more. */
    private final RefusalMemory refusals = new RefusalMemory(RefusalMemory.DRAIN);

    public BoardRobotPump(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotPumpNBT.INSTANCE;
    }

    /** The mB the robot is currently carrying (0 when the tank is empty). */
    public int carriedFluid() {
        //? if >=1.21.10 {
        return robot.getFluidHandler().getAmountAsInt(0);
        //?} else {
        /*net.neoforged.neoforge.fluids.FluidStack carried = robot.getFluidHandler().getFluidInTank(0);
        return carried == null ? 0 : carried.getAmount();*/
        //?}
    }

    /** Refreshes {@link #fluidFilter} from the robot's LINKED (home) station — 7.1.x's
     *  {@code updateFilter()}, which read {@code ActionRobotFilter.getGateFluidFilter(robot
     *  .getLinkedStation())}. Here the read goes through the D1 seam
     *  {@link DockingStation#getRobotFluidFilter()}, which {@code DockingStationPipe} implements from the
     *  real gate actions. A stationless robot gets a null filter, exactly as 7.1.x normalised its
     *  pass-through filter to null. */
    void updateFilter() {
        DockingStation linked = robot.getLinkedStation();
        fluidFilter = linked == null ? null : linked.getRobotFluidFilter();
    }

    /** Whether the fluid standing in {@code state} passes the station's gate filter (7.1.x
     *  {@code matchesGateFilter}). No filter set passes everything; a block holding no fluid never passes
     *  a filter that IS set. */
    boolean matchesGateFilter(BlockState state) {
        if (fluidFilter == null) {
            return true;
        }
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) {
            return false;
        }
        // The search predicate only ever offers source blocks, so getType() is already the source fluid.
        return fluidFilter.matches(new FluidStack(fluidState.getType(), BUCKET));
    }

    /** The search predicate: a drainable fluid source nobody else has reserved that the station's gate filter
     *  lets through and that has not refused this board's robot (see {@link #refusals}). Asking protection mods
     *  is NOT part of it: this runs for every scanned cell, so the pump AI asks once, about the one block it was
     *  sent to, and the answer comes back through {@link #delegateAIEnded}. */
    public boolean isSearchCandidate(@Nullable Level level, BlockPos pos) {
        return level != null
                // A Mining Well / Pump tube drilled through water is waterlogged, a source like any other; it is the
                // machine's own plumbing, so the pond around it is pumped but the tube is left wet (as TilePump does).
                && !BlockUtil.isMinerTube(level.getBlockState(pos))
                && BuildCraftAPI.getWorldProperty("fluidSource").get(level, pos)
                && !BlockClaims.isClaimed(robot.getRegistry(), pos)
                && matchesGateFilter(level.getBlockState(pos))
                && !refusals.isRefused(level, pos, RobotProtection.identityOf(robot),
                        RobotProtection.ownerOf(robot));
    }

    @Override
    public void update() {
        if (carriedFluid() > 0) {
            startDelegateAI(new AIRobotGotoStationAndUnloadFluids(robot));
        } else {
            updateFilter();
            Level level = robot.level();
            IBlockFilter blockFilter = pos -> isSearchCandidate(level, pos);
            startDelegateAI(new AIRobotSearchAndGotoBlock(robot, false, blockFilter));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAndGoto) {
            if (searchAndGoto.success()) {
                blockFound.adopt(searchAndGoto.getBlockFound(), searchAndGoto.holdsReservation());
                startDelegateAI(new AIRobotPumpBlock(robot, blockFound.job()));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotPumpBlock pumpAi) {
            // A block that would not be given up (a claim mod said no, or its pickup refused) must not be
            // searched again while it is unchanged: the AI is one attempt and dies here, so the memory is the
            // board's. Without it the robot flew straight back to the same block on every pass.
            Level level = robot.level();
            if (pumpAi.refused() && level != null && pumpAi.blockToPump() != null) {
                refusals.remember(level, pumpAi.blockToPump(), RobotProtection.identityOf(robot),
                        RobotProtection.ownerOf(robot));
            }
            releaseBlockFound();
        } else if (ai instanceof AIRobotGotoStationAndUnloadFluids) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }

    /** Runs before the delegate on every cycle: a remembered source is re-claimed before the resumed pump touches
     *  it, or given up with the pump ({@link JobClaim#preempt}). */
    @Override
    public void preempt(AIRobot ai) {
        blockFound.preempt(this, ai);
    }

    @Override
    public void end() {
        releaseBlockFound();
    }

    protected BlockPos blockFound() {
        return blockFound.job();
    }

    private void releaseBlockFound() {
        blockFound.release();
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);
        JobClaim.writeBlock(blockFound, nbt, "blockFound");
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);
        JobClaim.readBlock(blockFound, nbt, "blockFound");
    }
}
