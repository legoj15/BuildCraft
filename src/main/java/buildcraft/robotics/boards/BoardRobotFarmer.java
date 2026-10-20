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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.BlockClaims;
import buildcraft.robotics.JobClaim;
import buildcraft.robotics.RobotProtection;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.AIRobotUseToolOnBlock;

/** The farmer: keeps a hoe equipped (fetching one from the station when none is held), searches for
 *  {@code "dirt"} with air above it, then tills it with {@code AIRobotUseToolOnBlock}. Ported from
 *  7.1.x {@code BoardRobotFarmer} (7.1.x's tool check was {@code ItemHoe}; the modern equivalent is
 *  {@link RobotToolPredicates#isHoe}).
 *
 *  <p>One divergence from 7.1.x: the fetch filter additionally refuses a WORN hoe
 *  ({@code damage >= maxDamage}) — 7.1.x would fetch a broken hoe and then spin forever, since a broken
 *  tool's failed till keeps the (damageable) item in the hand. Same guard the break-board base carries.
 *
 *  <p>A second: dirt a protection mod would not let the robot work is not tilled again and again. The search asks
 *  once about the block it settles on (as the generic search board does for its breaks), and a till that fails anyway
 *  - a claim mod cancelling the right-click the till starts with (or, below 26.3, the hoe's tool-modification event),
 *  which no break probe can see -
 *  is remembered per block ({@link WorkRefusals}), so the next search flies to different dirt. After
 *  {@link WorkRefusals#GIVE_UP_AFTER} failed tills in a row the farmer sleeps instead of working through a whole
 *  claim. 7.1.x, which had no claim mods, picked the same nearest dirt every cycle. */
public class BoardRobotFarmer extends RedstoneBoardRobot {

    /** The block being tilled. Saved; a reloaded one is re-claimed only while the resumed till works it. */
    private final JobClaim<BlockPos> blockFound = JobClaim.block(robot, JobClaim.OnReload.RECLAIM_WHILE_WORKING);

    /** Dirt the hoe was refused on (or protection mods would not let the robot break), skipped by the search while
     *  it stays unchanged, and the run of failed tills that sends the farmer to sleep. Transient: a reloaded board
     *  simply asks again. */
    private final WorkRefusals refusals = new WorkRefusals(robot);

    public BoardRobotFarmer(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotFarmerNBT.INSTANCE;
    }

    /** The hoe predicate: vanilla hoes by identity plus anything the {@code c:tools/hoe} tag admits. */
    public boolean isExpectedHoe(ItemStack stack) {
        return RobotToolPredicates.isHoe(stack);
    }

    @Override
    public void update() {
        if (robot.getHeldItem().isEmpty()) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot,
                    stack -> !stack.isEmpty() && stack.getDamageValue() < stack.getMaxDamage()
                            && isExpectedHoe(stack)));
        } else {
            Level level = robot.level();
            startDelegateAI(new AIRobotSearchAndGotoBlock(robot, false, pos -> isSearchCandidate(level, pos),
                    pos -> approveSearchTarget(level, pos)));
        }
    }

    /** The search predicate, evaluated for every block the scan visits: tillable dirt with air above it that no other
     *  robot has reserved and that has not refused this farmer (see {@link #refusals}). Asking protection mods is
     *  not part of it - that runs once, about the block the search settles on ({@link #approveSearchTarget}). */
    public boolean isSearchCandidate(@Nullable Level level, BlockPos pos) {
        return level != null
                && BuildCraftAPI.getWorldProperty("dirt").get(level, pos)
                && level.getBlockState(pos.above()).isAir()
                && !BlockClaims.isClaimed(robot.getRegistry(), pos)
                && !refusals.isRefused(level, pos);
    }

    /** Asked once about the block the search has settled on (not about every candidate scanned, which would post a
     *  break event per block to every listener on every search): whether protection mods let this robot work it.
     *  There is no probe for the till's own asks (the right-click, the tool modification) without tilling, so the
     *  question asked is the one
     *  every robot is asked - may it break the block - which a claim answers alike for both; a till refused anyway
     *  is caught when it fails ({@link #delegateAIEnded}). A refusal is remembered so the next search skips the
     *  block instead of flying back to it. */
    public boolean approveSearchTarget(@Nullable Level level, BlockPos pos) {
        if (level == null) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel) || RobotProtection.canBreak(robot, serverLevel, pos)) {
            return true;
        }
        refusals.remember(level, pos);
        return false;
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAndGoto) {
            if (searchAndGoto.success()) {
                blockFound.adopt(searchAndGoto.getBlockFound(), searchAndGoto.holdsReservation());
                startDelegateAI(new AIRobotUseToolOnBlock(robot, blockFound.job()));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotFetchAndEquipItemStack) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotUseToolOnBlock) {
            // A till that failed (a claim mod said no, or the dirt changed under the hoe) must not be tried again on
            // the same block while it is unchanged: the AI is one attempt and dies here, so the memory is the
            // board's. Without it the robot flew back to the same nearest dirt on every pass.
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
        }
    }

    /** Runs before the delegate on every cycle: a remembered block is re-claimed before the resumed action touches
     *  it, or given up with the action ({@link JobClaim#preempt}). */
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
