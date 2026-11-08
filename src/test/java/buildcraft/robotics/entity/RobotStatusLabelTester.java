/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.ZonePinnedRobot;
import buildcraft.robotics.boards.BoardRobotKnightNBT;

/**
 * The Robot Goggles status label on a LIVE robot, the half {@code RobotStatusTest} cannot reach: that the server tick
 * works the status out of the real AI chain and publishes it through the synced accessors the client reads, so a
 * knight with no sword in any station ends up reporting why it sleeps.
 */
public class RobotStatusLabelTester {

    /** Work budget of the poll, counted from the arena cell ticking; the manifest's {@code max_ticks} sits above
     *  {@code CELL_READY_TICKS} plus this. */
    private static final int STATUS_WORK_TICKS = 200;

    /** Well above the safety line, so the energy ladder leaves the knight to its board. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    /** A zone-pinned knight in an empty cell: its sword fetch finds no station, so the board sends it to sleep, and the
     *  synced status says "no tool". Read through {@code getStatus()}, the accessor the renderer's label uses. */
    public static void knightWithNoSwordReportsNoToolSleepReason(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + STATUS_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, STATUS_WORK_TICKS, () -> {
                    if (robot[0] == null) {
                        robot[0] = ZonePinnedRobot.spawn(helper, new BlockPos(3, 3, 3), BoardRobotKnightNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    return robot[0].getStatus().equals(new RobotStatus(RobotActivity.SLEEPING, RobotSleepReason.NO_TOOL));
                }, () -> robot[0] == null ? "the knight was never spawned" : "last status " + robot[0].getStatus()),
                () -> helper.succeed(),
                "the knight never reported sleeping for want of a tool",
                () -> EntityArenaUtil.discard(robot[0]));
    }
}
