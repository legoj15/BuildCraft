/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.RefusalMemory;
import buildcraft.robotics.RobotProtection;
import buildcraft.robotics.entity.RobotSleepReason;

/**
 * What a farming board (the farmer, the planter; the stripes board for its refused uses) remembers about work that did
 * not take: the blocks a protection mod would not let its robot till, plant or use an item on, so the search does not
 * fly back to them every cycle, and how many attempts in a row failed, so a robot facing a wall of refusals goes to
 * sleep instead of trying them all.
 *
 * <p>The blocks are a {@link RefusalMemory} with the robot-search policy ({@link RefusalMemory#ROBOT_SEARCH}) - the
 * memory the generic search board keeps for its breaks: a refusal holds while the block is unchanged, up to the
 * policy's window. It is keyed on the two players whose leave the robot needs (see {@code RobotProtection}): the
 * identity it acts as and its home station's owner. A refusal says nothing about any other pair, so a robot relinked to
 * another player's station asks afresh. Transient: a reloaded board simply asks again.
 *
 * <p>The blocks are kept in two memories of that policy: those a protection mod refused ({@link #remember},
 * {@link #refused}) and those the work failed on for any other cause ({@link #failed}), so a search that comes up empty
 * can say why ({@link #emptySearchReason}): the search skips both kinds, and a board whose every candidate is skipped
 * would otherwise report "no work" for a zone full of it.
 *
 * <p>Neither is saved or shared between robots; a board owns one.
 */
final class WorkRefusals {

    /** Consecutive failed attempts after which the board gives up for a while (it sleeps, then starts counting
     *  again). The refused blocks are skipped by the search in the meantime, so a robot that gives up has already
     *  tried this many distinct blocks. */
    static final int GIVE_UP_AFTER = 3;

    private final IRobotAccess robot;
    private final RefusalMemory refused = new RefusalMemory(RefusalMemory.ROBOT_SEARCH);
    private final RefusalMemory failedOn = new RefusalMemory(RefusalMemory.ROBOT_SEARCH);
    private int failedInARow;
    /** Whether the search under way skipped a block for each kind of memory ({@link #startSearch}). */
    private boolean skippedRefused;
    private boolean skippedFailed;

    WorkRefusals(IRobotAccess robot) {
        this.robot = robot;
    }

    /** Whether {@code pos} refused this robot, or the work on it failed, and it is unchanged, so the search should
     *  leave it alone. A block in an unloaded chunk is not read, and so not refused. Call it as the search predicate's
     *  last conjunct: a skip is noted for {@link #emptySearchReason}, which should count only blocks the search would
     *  otherwise have taken. */
    boolean isRefused(@Nullable Level level, BlockPos pos) {
        if (level == null) {
            return false;
        }
        if (refused.isRefused(level, pos, RobotProtection.identityOf(robot), RobotProtection.refusalKeyOf(robot))) {
            skippedRefused = true;
            return true;
        }
        if (failedOn.isRefused(level, pos, RobotProtection.identityOf(robot), RobotProtection.refusalKeyOf(robot))) {
            skippedFailed = true;
            return true;
        }
        return false;
    }

    /** Records that a protection mod refused {@code pos} to this robot, without counting an attempt: a block refused
     *  while the search settles on it was never worked. Noted for {@link #emptySearchReason} as a skip, since the
     *  search goes on without it. */
    void remember(@Nullable Level level, BlockPos pos) {
        if (level != null) {
            refused.remember(level, pos, RobotProtection.identityOf(robot), RobotProtection.refusalKeyOf(robot));
            skippedRefused = true;
        }
    }

    /** A new search starts: forgets what the last one skipped. */
    void startSearch() {
        skippedRefused = false;
        skippedFailed = false;
    }

    /**
     * Why the search since {@link #startSearch} found nothing: {@link RobotSleepReason#NO_WORK} when it skipped no
     * block either, so there truly is nothing to do; {@link RobotSleepReason#PROTECTED} when the only blocks it
     * skipped had refused this robot; {@link RobotSleepReason#WORK_FAILING} when some were blocks the work failed on.
     * A search that gives up before it has seen the whole zone (the random scanners) may miss some, so this is what
     * the search saw, not a census.
     */
    RobotSleepReason emptySearchReason() {
        if (skippedFailed) {
            return RobotSleepReason.WORK_FAILING;
        }
        return skippedRefused ? RobotSleepReason.PROTECTED : RobotSleepReason.NO_WORK;
    }

    /**
     * An attempt at {@code pos} failed for a cause the board cannot name (a change under the robot, a block it cannot
     * break, a refusal it could not tell apart): remembers the block among the failures (when the level and the
     * block are known) and counts the failure.
     *
     * @return true when this was the {@link #GIVE_UP_AFTER}th failure in a row - the board should stop for a while;
     *         the count starts again from zero
     */
    boolean failed(@Nullable Level level, @Nullable BlockPos pos) {
        if (pos != null && level != null) {
            failedOn.remember(level, pos, RobotProtection.identityOf(robot), RobotProtection.refusalKeyOf(robot));
        }
        return countFailure();
    }

    /** {@link #failed} for an attempt a protection mod refused: remembered among the refusals, counted the same. */
    boolean refused(@Nullable Level level, BlockPos pos) {
        if (level != null) {
            refused.remember(level, pos, RobotProtection.identityOf(robot), RobotProtection.refusalKeyOf(robot));
        }
        return countFailure();
    }

    private boolean countFailure() {
        if (++failedInARow >= GIVE_UP_AFTER) {
            failedInARow = 0;
            return true;
        }
        return false;
    }

    /** An attempt worked: the failures in a row are not in a row any more. */
    void succeeded() {
        failedInARow = 0;
    }
}
