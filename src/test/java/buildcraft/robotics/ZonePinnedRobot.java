/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IZone;

import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.entity.EntityRobot;

/** A live board robot whose work zone AND load/unload zone are its test's own arena cell
 *  ({@link EntityArenaUtil#cellZone}). Without a zone a board's searches cover the whole dimension: a picker takes
 *  a sibling test's dropped items, a carrier or delivery robot flies to a sibling's station, a lumberjack chops a
 *  sibling's tree. A real robot gets its zones from gate actions on its linked station; a test that does not need
 *  that wiring uses this instead. Never for a test whose point is a gate-supplied zone. */
public class ZonePinnedRobot extends EntityRobot {

    private final IZone zone;

    public ZonePinnedRobot(GameTestHelper helper, RedstoneBoardRobotNBT board) {
        super(helper.getLevel(), board);
        this.zone = EntityArenaUtil.cellZone(helper);
    }

    /** A pinned robot at {@code relPos}'s block centre, added to the level (it starts ticking with the level). */
    public static ZonePinnedRobot spawn(GameTestHelper helper, BlockPos relPos, RedstoneBoardRobotNBT board) {
        ZonePinnedRobot robot = new ZonePinnedRobot(helper, board);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    @Override
    public IZone getZoneToWork() {
        return zone;
    }

    @Override
    public IZone getZoneToLoadUnload() {
        return zone;
    }
}
