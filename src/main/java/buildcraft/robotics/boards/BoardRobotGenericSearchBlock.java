/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.BlockClaims;
import buildcraft.lib.misc.ProtectionRefusals;
import buildcraft.robotics.JobClaim;
import buildcraft.robotics.RobotProtection;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.statements.ActionRobotFilter;

/** The search-board base: each cycle, search for the nearest block matching {@link #isExpectedBlock}
 *  (and not already taken by another robot), remember it as {@code blockFound}, and let the subclass act
 *  on it. Ported from 7.1.x {@code BoardRobotGenericSearchBlock}: the effective block filter is
 *  {@code isExpectedBlock && matchesGateFilter && !isTaken}, where the middle conjunct is the linked
 *  station's gate "Filter" action ({@link #updateFilter()} / {@link #matchesGateFilter}) — a station with
 *  no Filter action, or one whose Filter names no block, restricts nothing.
 *
 *  <p>A failed search sends the robot home to sleep ({@code AIRobotGotoSleep}) and the search starts again
 *  on the next cycle, exactly as 7.1.x. Subclasses act on {@link #blockFound()} in their own
 *  {@code update()} and only fall through to {@code super.update()} when nothing is found yet. */
public abstract class BoardRobotGenericSearchBlock extends RedstoneBoardRobot {

    /** Game ticks a refused block is left alone before the owner's claim is asked about it again (1 minute). */
    public static final int REFUSAL_RETRY_TICKS = 1200;

    /** The block found. Saved; a reloaded one is re-claimed whether or not an action resumed, because the break
     *  boards' {@code update()} goes back to breaking a remembered block. */
    private final JobClaim<BlockPos> blockFound = JobClaim.block(robot, JobClaim.OnReload.RECLAIM);

    /** The blocks the linked station's gate "Filter" action names, refreshed every {@link #update()}.
     *  Empty means "no restriction" (7.1.x: {@code blockFilter.size() == 0} passed everything). Replaced
     *  wholesale rather than mutated in place, so the search predicate can never observe a half-built
     *  set — 7.1.x's own comment warned that this predicate may be evaluated off the tick thread. */
    private volatile Set<Block> gateBlockFilter = Set.of();

    /** Blocks protection mods refused this robot, so a refused block is not chosen again (and not asked about
     *  again) every search. Transient: a reloaded board simply asks again. */
    private final ProtectionRefusals refusals = new ProtectionRefusals(REFUSAL_RETRY_TICKS, 256);

    public BoardRobotGenericSearchBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** The block predicate this board hunts for. Called with the live state of each candidate cell;
     *  must be pure-state (it is evaluated for every scanned cell, and the state is the only input a
     *  worldless property check gets). */
    public abstract boolean isExpectedBlock(BlockState state);

    /** The neighbour-aware seam the search actually calls. It defaults to the pure-state predicate, which
     *  is right for every board whose property is a function of the candidate state alone (logs, ores,
     *  dirt). A board whose property genuinely reads the surrounding blocks — the harvester, whose
     *  stacking crops are only ripe relative to the block below — overrides this; answering such a
     *  property from the state alone silently reports "no" forever. */
    public boolean isExpectedBlock(BlockGetter access, BlockPos pos) {
        return isExpectedBlock(access.getBlockState(pos));
    }

    @Override
    public void update() {
        updateFilter();

        Level level = robot.level();
        startDelegateAI(new AIRobotSearchAndGotoBlock(robot, false, pos -> isSearchCandidate(level, pos),
                pos -> approveSearchTarget(level, pos)));
    }

    /** The search predicate, evaluated for every block the scan visits, so it asks nobody anything: the
     *  neighbour-aware property (batch 1 — stacking crops are only ripe relative to the block below) AND the gate
     *  "Filter" conjunct (batch 2); then the reservation, and last a block protection mods already refused this
     *  robot (remembered by {@link #approveSearchTarget}; unchanged blocks only, for a bounded time). */
    public boolean isSearchCandidate(@Nullable Level level, BlockPos pos) {
        return level != null && isExpectedBlock(level, pos) && matchesGateFilter(level.getBlockState(pos))
                && !BlockClaims.isClaimed(robot.getRegistry(), pos)
                && !refusals.isRefused(pos, level.getBlockState(pos), level.getGameTime());
    }

    /** Asked once about the block the search has settled on (not about every candidate scanned, which would post a
     *  break event per block to every listener on every search): whether protection mods let this robot break it —
     *  every subclass breaks what it finds. A refusal is remembered so the next search skips the block instead of
     *  flying back to it (and asking) forever; it lasts while the block is unchanged, up to
     *  {@link #REFUSAL_RETRY_TICKS}, since lifting a claim fires no event. */
    public boolean approveSearchTarget(@Nullable Level level, BlockPos pos) {
        if (level == null) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel) || RobotProtection.canBreak(robot, serverLevel, pos)) {
            return true;
        }
        refusals.remember(pos, level.getBlockState(pos), level.getGameTime());
        return false;
    }

    /** The state-only form of the search predicate: the board's own block test AND the gate's Filter
     *  action. {@link #update()} uses the same two conjuncts, but answers the block test through the
     *  neighbour-aware {@link #isExpectedBlock(BlockGetter, BlockPos)} overload (stacking crops) and adds
     *  the registry-reservation check on top. */
    protected final boolean isSearchTarget(BlockState state) {
        return isExpectedBlock(state) && matchesGateFilter(state);
    }

    /** Re-reads the linked station's gate "Filter" actions (7.1.x {@code updateFilter}, called at the top
     *  of every {@code update()} so a gate re-parameterised while robots are running takes effect on the
     *  next cycle). Only BLOCK items contribute — 7.1.x collected {@code ItemBlock} parameters alone, so
     *  a Filter holding nothing placeable leaves the search unrestricted. An unlinked robot keeps an
     *  empty filter, i.e. no restriction. */
    public final void updateFilter() {
        DockingStation station = robot.getLinkedStation();
        if (station == null) {
            gateBlockFilter = Set.of();
            return;
        }

        Set<Block> blocks = new HashSet<>();
        for (ItemStack stack : ActionRobotFilter.getGateFilterStacks(station)) {
            if (stack.getItem() instanceof BlockItem blockItem) {
                blocks.add(blockItem.getBlock());
            }
        }
        gateBlockFilter = blocks.isEmpty() ? Set.of() : Set.copyOf(blocks);
    }

    /** Whether {@code state} is one of the blocks the gate's Filter action named. 7.1.x compared block +
     *  metadata; metadata is gone, and its 1.7.10 job — telling oak from birch — is now block identity,
     *  so identity is the faithful equivalent. */
    protected boolean matchesGateFilter(BlockState state) {
        Set<Block> filter = gateBlockFilter;
        return filter.isEmpty() || filter.contains(state.getBlock());
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAndGoto) {
            if (searchAndGoto.success()) {
                // Normally the search-and-goto took the reservation before it reported the block; if it did not
                // (a flight that came back from NBT unreserved), preempt re-claims or drops it on the next cycle.
                blockFound.adopt(searchAndGoto.getBlockFound(), searchAndGoto.holdsReservation());
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }

    /** Runs before the delegate on every cycle, so it sees a reloaded board before its resumed action AI (or
     *  {@code update()}) touches the remembered block: re-claimed, or - another robot took it meanwhile - given up
     *  with whatever this board was doing to it, to search again next cycle ({@link JobClaim#preempt}). Not 7.1.x:
     *  there the reloaded robot went on working the block unreserved while any other robot's search was free to take
     *  it too. */
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

    /** Releases the registry reservation on the found block (7.1.x did the same in {@code end} and after
     *  a finished action AI). Owner-checked: if this robot no longer holds the block (it lost it across an
     *  unload), the release leaves the current holder's claim alone. */
    protected void releaseBlockFound() {
        blockFound.release();
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);
        JobClaim.writeBlock(blockFound, nbt, "indexStored");
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);
        JobClaim.readBlock(blockFound, nbt, "indexStored");
    }
}
