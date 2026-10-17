/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

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
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.lib.misc.DrainRefusals;
import buildcraft.lib.misc.GameProfileUtil;
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
 *  fluid filter (7.1.x {@code updateFilter()}), refreshed on every search pass.
 *
 *  <p>The 7.1.x pump read its tank through {@code getTankInfo}; the modern equivalent is
 *  {@code robot.getFluidHandler()}. As in 7.1.x, a finished pump pass releases the block regardless of
 *  the pump AI's outcome (7.1.x never checked it either — the modern {@code AIRobotPumpBlock} reports
 *  success honestly now, but the board stays faithful). */
public class BoardRobotPump extends RedstoneBoardRobot {

    private static final int BUCKET = 1000;

    private BlockPos blockFound;

    /** Whether this board holds the registry reservation on {@link #blockFound} in this session. Not saved: the
     *  unload that saved a mid-job board freed it, so {@link #preempt} re-claims it after a reload. */
    private boolean blockFoundClaimed;

    /** The gate fluid filter of the robot's home station, refreshed on every search pass. Null means
     *  "no station, no filter" — pump anything. */
    private IFluidFilter fluidFilter;

    /** Blocks the pump AI was refused (by a protection mod or the block's own pickup), skipped by the search
     *  while they stay unchanged. Every entry was refused to {@link #refusalsOwner}: the AI asks as the home
     *  station's owner, so a refusal says nothing about anyone else. Transient: a reloaded board asks once more. */
    private final DrainRefusals refusals = new DrainRefusals();

    /** The UUID of the home-station owner every entry in {@link #refusals} was refused to (null: no owner, the
     *  generic "[BuildCraft]" identity). */
    @Nullable
    private UUID refusalsOwner;

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
                && BuildCraftAPI.getWorldProperty("fluidSource").get(level, pos)
                && !robot.getRegistry().isTaken(new ResourceIdBlock(pos))
                && matchesGateFilter(level.getBlockState(pos))
                && !refusalsForCurrentOwner().isRefused(level, pos);
    }

    /** {@link #refusals}, first emptied if the robot now works for someone other than the owner they were refused
     *  to (it was relinked to another player's station): that owner's refusals are not this one's, so the
     *  memory is keyed on the owner as well as the cell and its state. */
    private DrainRefusals refusalsForCurrentOwner() {
        GameProfile owner = RobotProtection.ownerOf(robot);
        UUID ownerId = owner == null ? null : GameProfileUtil.getId(owner);
        if (!Objects.equals(ownerId, refusalsOwner)) {
            refusals.clear();
            refusalsOwner = ownerId;
        }
        return refusals;
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
                blockFound = searchAndGoto.getBlockFound();
                blockFoundClaimed = searchAndGoto.holdsReservation();
                startDelegateAI(new AIRobotPumpBlock(robot, blockFound));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotPumpBlock pumpAi) {
            // A block that would not be given up (a claim mod said no, or its pickup refused) must not be
            // searched again while it is unchanged: the AI is one attempt and dies here, so the memory is the
            // board's. Without it the robot flew straight back to the same block on every pass.
            Level level = robot.level();
            if (pumpAi.refused() && level != null && pumpAi.blockToPump() != null) {
                refusalsForCurrentOwner().remember(level, pumpAi.blockToPump());
            }
            releaseBlockFound();
        } else if (ai instanceof AIRobotGotoStationAndUnloadFluids) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }

    /** Runs before the delegate on every cycle. A board reloaded mid-job remembers its block, but the unload freed
     *  the reservation: re-claim it before the resumed action AI touches the block, or - when another robot took
     *  it meanwhile - give up the block and the action rather than work it alongside that robot. With no resumed
     *  action there is nothing to re-claim it for, so it is simply forgotten. Not 7.1.x, which worked it
     *  unreserved. */
    @Override
    public void preempt(AIRobot ai) {
        if (blockFound != null && !blockFoundClaimed) {
            IRobotRegistry registry = robot.getRegistry();
            if (registry == null) {
                return;
            }
            if (ai != null && registry.reclaim(new ResourceIdBlock(blockFound), robot.getRobotId())) {
                blockFoundClaimed = true;
            } else {
                blockFound = null;
                abortDelegateAI();
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

    private void releaseBlockFound() {
        if (blockFound != null) {
            robot.getRegistry().release(new ResourceIdBlock(blockFound), robot.getRobotId());
            blockFound = null;
            blockFoundClaimed = false;
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
