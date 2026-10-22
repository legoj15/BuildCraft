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
import net.minecraft.world.level.Level;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.crops.CropManager;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.BlockClaims;
import buildcraft.robotics.JobClaim;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotPlant;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.lib.inventory.filter.AggregateFilter;
import buildcraft.robotics.path.IBlockFilter;
import buildcraft.robotics.statements.ActionRobotFilter;

/** The planter: keeps a seed equipped (fetching one from the station when none is held), searches for
 *  a spot that is not {@code "replaceable"} and can sustain the planted seed, then plants it with
 *  {@code AIRobotPlant}. Ported from 7.1.x {@code BoardRobotPlanter} (the 7.1.x gate filter is Ph6,
 *  so the effective block filter is {@code !replaceable && canSustainPlant && !isTaken} — D1).
 *
 *  <p>7.1.x searched with {@code maxDistanceToEnd = 1} (the path may end on any soft block within one of
 *  the target) — kept: the planter does not need to stand on the exact cell before the seed spot.
 *
 *  <p>A spot a protection mod would not let the robot plant (a claim mod refusing the right-click or placement) is
 *  not tried again and again: the failed planting is remembered per spot ({@link WorkRefusals}), so the next search
 *  picks somewhere else, the robot keeps the seed the planting did not use ({@link AIRobotPlant}), and after
 *  {@link WorkRefusals#GIVE_UP_AFTER} failed plantings in a row the planter sleeps instead of working through a
 *  whole claim. There is no side-effect-free probe for a placement, so unlike the farmer (and the generic search
 *  board) the search asks nobody first; the first attempt at each spot is the question. 7.1.x, which had no claim
 *  mods, would have picked the same spot every cycle. */
public class BoardRobotPlanter extends RedstoneBoardRobot {

    /** The spot being planted. Saved; a reloaded one is re-claimed only while the resumed planting works it. */
    private final JobClaim<BlockPos> blockFound = JobClaim.block(robot, JobClaim.OnReload.RECLAIM_WHILE_WORKING);

    /** Spots the seed was refused on, skipped by the search while they stay unchanged, and the run of failed
     *  plantings that sends the planter to sleep. Transient: a reloaded board simply asks again. */
    private final WorkRefusals refusals = new WorkRefusals(robot);

    public BoardRobotPlanter(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotPlanterNBT.INSTANCE;
    }

    /** The seed predicate: anything {@link CropManager#isSeed} recognises (class-based, so it works in
     *  the unit-test JVM where data tags do not resolve). */
    public boolean matchesSeed(ItemStack stack) {
        return CropManager.isSeed(stack);
    }

    /** What the planter is willing to fetch: its own seed predicate AND the linked station's gate
     *  "Filter" action (7.1.x: {@code new AggregateFilter(seedFilter,
     *  ActionRobotFilter.getGateFilter(robot.getLinkedStation()))}), so a station filtered on pumpkin
     *  seeds gets a pumpkin planter. An unset Filter — or no linked station — is pass-through. */
    IStackFilter seedFetchFilter() {
        return new AggregateFilter(this::matchesSeed,
                ActionRobotFilter.getGateFilter(robot.getLinkedStation()));
    }

    @Override
    public void update() {
        if (robot.getHeldItem().isEmpty()) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot, seedFetchFilter()));
        } else {
            Level level = robot.level();
            ItemStack seed = robot.getHeldItem();
            IBlockFilter blockFilter = pos -> level != null
                    && !BuildCraftAPI.getWorldProperty("replaceable").get(level, pos)
                    && CropManager.canSustainPlant(level, seed, pos)
                    && !BlockClaims.isClaimed(robot.getRegistry(), pos)
                    && !refusals.isRefused(level, pos);
            startDelegateAI(new AIRobotSearchAndGotoBlock(robot, true, blockFilter, 1));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAndGoto) {
            if (searchAndGoto.success()) {
                blockFound.adopt(searchAndGoto.getBlockFound(), searchAndGoto.holdsReservation());
                startDelegateAI(new AIRobotPlant(robot, blockFound.job()));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotPlant) {
            // A planting that failed (a claim mod refused the right-click or placement, or the soil changed under the
            // seed) must not be tried again on the same spot while it is unchanged: the AI is one attempt and dies
            // here, so the memory is the board's. Without it the robot flew back to the same spot on every pass.
            boolean giveUp = false;
            if (ai.success()) {
                refusals.succeeded();
            } else {
                giveUp = refusals.failed(robot.level(), blockFound.job());
            }
            releaseBlockFound();
            if (giveUp) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotFetchAndEquipItemStack) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }

    /** Runs before the delegate on every cycle: a remembered spot is re-claimed before the resumed planting touches
     *  it, or given up with the planting ({@link JobClaim#preempt}). */
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
