/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.entity.EntityRobot;

/**
 * Robots for game tests that drive one by hand (its AIs cycled, its board's predicates called), never added to the
 * level: it never ticks, so nothing but the test moves it. A robot that must tick is spawned instead
 * ({@link ZonePinnedRobot#spawn}). The cell's chunks are the caller's to force-load where the code under test reads
 * past the arena's own chunk ({@code EntityArenaUtil.forceLoadCell}).
 */
public final class RobotArenaUtil {

    /** Above {@link IRobotAccess#SAFETY_POWER}, so the {@code AIRobotMain} ladder never preempts with a recharge. */
    public static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    private RobotArenaUtil() {}

    /** A plain robot with no board at {@code relPos}'s block centre, charged with {@link #SEEDED_CHARGE}. */
    public static EntityRobot unaddedRobot(GameTestHelper helper, BlockPos relPos) {
        return unadded(new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel()), helper, relPos);
    }

    /** {@code robot} (a {@link ZonePinnedRobot}, say) moved to {@code relPos}'s block centre and charged with
     *  {@link #SEEDED_CHARGE}. */
    public static <R extends EntityRobot> R unadded(R robot, GameTestHelper helper, BlockPos relPos) {
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        return robot;
    }
}
