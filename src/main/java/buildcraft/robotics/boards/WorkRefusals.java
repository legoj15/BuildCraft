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

/**
 * What a farming board (the farmer, the planter) remembers about work that did not take: the blocks a protection mod
 * would not let its robot till or plant, so the search does not fly back to them every cycle, and how many attempts in
 * a row failed, so a robot facing a wall of refusals goes to sleep instead of trying them all.
 *
 * <p>The blocks are an owner-keyed {@link RefusalMemory} with the robot-search policy
 * ({@link RefusalMemory#ROBOT_SEARCH}) - the memory the generic search board keeps for its breaks: a refusal holds
 * while the block is unchanged, up to the policy's window, and says nothing about anyone but the home station's owner,
 * whom the robot asks as. Transient: a reloaded board simply asks again.
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
    private int failedInARow;

    WorkRefusals(IRobotAccess robot) {
        this.robot = robot;
    }

    /** Whether {@code pos} refused this robot and is unchanged, so the search should leave it alone. A block in an
     *  unloaded chunk is not read, and so not refused. */
    boolean isRefused(@Nullable Level level, BlockPos pos) {
        return level != null && refused.isRefused(level, pos, RobotProtection.ownerOf(robot));
    }

    /** Records that {@code pos} refused this robot, without counting an attempt: a block refused while the search
     *  settles on it was never worked. */
    void remember(@Nullable Level level, BlockPos pos) {
        if (level != null) {
            refused.remember(level, pos, RobotProtection.ownerOf(robot));
        }
    }

    /**
     * An attempt at {@code pos} failed: remembers the block (when the level and the block are known) and counts the
     * failure.
     *
     * @return true when this was the {@link #GIVE_UP_AFTER}th failure in a row - the board should stop for a while;
     *         the count starts again from zero
     */
    boolean failed(@Nullable Level level, @Nullable BlockPos pos) {
        if (pos != null) {
            remember(level, pos);
        }
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
