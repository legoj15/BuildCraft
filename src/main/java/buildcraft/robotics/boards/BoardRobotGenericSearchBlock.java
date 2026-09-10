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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;
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

    private BlockPos blockFound;

    /** The blocks the linked station's gate "Filter" action names, refreshed every {@link #update()}.
     *  Empty means "no restriction" (7.1.x: {@code blockFilter.size() == 0} passed everything). Replaced
     *  wholesale rather than mutated in place, so the search predicate can never observe a half-built
     *  set — 7.1.x's own comment warned that this predicate may be evaluated off the tick thread. */
    private volatile Set<Block> gateBlockFilter = Set.of();

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
        startDelegateAI(new AIRobotSearchAndGotoBlock(robot, false,
                // Both audit fixes meet here: the neighbour-aware property (batch 1 — stacking crops are only
                // ripe relative to the block below) AND the gate "Filter" conjunct (batch 2).
                pos -> level != null && isExpectedBlock(level, pos) && matchesGateFilter(level.getBlockState(pos))
                        && !robot.getRegistry().isTaken(new ResourceIdBlock(pos))));
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
                blockFound = searchAndGoto.getBlockFound();
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
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

    /** Releases the registry reservation on the found block (7.1.x did the same in {@code end} and after
     *  a finished action AI). */
    protected void releaseBlockFound() {
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
            nbt.putIntArray("indexStored", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "indexStored", null);
        if (arr != null && arr.length == 3) {
            blockFound = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
