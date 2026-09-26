/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.ai.AIRobotSleep;

/**
 * The charging latch on a LIVE robot — the half of the rules {@code RobotChargingLatchTest} cannot reach: that the
 * receiver feeds the latch the amount the battery ACCEPTED (not the amount offered), that the latch then drains one
 * per entity tick, and that while it is up a sleeping robot's synched sleep flag stays off (the renderer shows a
 * charging robot awake — 7.1.x: {@code isActive() && ticksCharging == 0}).
 */
public class RobotChargingLatchTester {

    private static final long THRESHOLD = RobotChargeReceiver.CHARGE_DETECT_THRESHOLD;
    private static final long BIG_OFFER = 100L * MjAPI.MJ;

    public static void chargingLatchFollowsAcceptedPowerAndDrains(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(3, 3, 3);
        EntityArenaUtil.forceLoadEntityArena(helper, relPos);
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(robot);

        RobotChargeReceiver receiver = robot.getChargeReceiver();
        long[] fullAt = { -1 };
        boolean[] sawSleepWhileCharging = { false };
        boolean[] armed = { false };

        EntityArenaUtil.tickUntil(helper, 200, () -> {
            if (!armed[0]) {
                if (robot.getRobotId() == EntityRobotBase.NULL_ROBOT_ID) {
                    return false; // not ticking yet
                }
                armed[0] = true;
                // All synchronous, between two entity ticks, so the latch cannot decay under the assertions.
                int start = robot.getTicksCharging();

                robot.getBattery().setStored(EntityRobotBase.MAX_POWER);
                receiver.receivePower(BIG_OFFER, false);
                helper.assertTrue(robot.getTicksCharging() == start,
                        "a FULL robot rejects the whole offer — a rejected transfer must not read as charging");

                robot.getBattery().setStored(EntityRobotBase.MAX_POWER - THRESHOLD);
                receiver.receivePower(BIG_OFFER, false);
                helper.assertTrue(robot.getTicksCharging() == start,
                        "only " + THRESHOLD + " µMJ fit: a big OFFER that lands at the threshold is not charging");

                robot.getBattery().setStored(EntityRobotBase.MAX_POWER - THRESHOLD - 1);
                receiver.receivePower(BIG_OFFER, false);
                helper.assertTrue(robot.getTicksCharging() == start + 5,
                        "one µMJ over the threshold accepted is a noticed delivery (+5): got "
                                + robot.getTicksCharging());

                // Asleep, and charged to the latch ceiling.
                robot.mainAI = new AIRobotSleep(robot);
                robot.getBattery().setStored(0);
                for (int i = 0; i < 6; i++) {
                    receiver.receivePower(BIG_OFFER, false);
                }
                helper.assertTrue(robot.getTicksCharging() == 30, "six deliveries cap the latch at 30");
                fullAt[0] = helper.getLevel().getGameTime();
                return false;
            }
            int latch = robot.getTicksCharging();
            if (latch > 0 && latch < 30 && robot.isSleepingClient()) {
                sawSleepWhileCharging[0] = true;
            }
            return latch == 0;
        }, () -> {
            long drained = helper.getLevel().getGameTime() - fullAt[0];
            helper.assertTrue(drained >= 30,
                    "the latch drains one per entity tick, so 30 cannot be gone in fewer than 30 ticks: " + drained);
            helper.assertFalse(sawSleepWhileCharging[0],
                    "a sleeping robot must not show as asleep while its charging latch is up");
            helper.assertTrue(robot.isSleepingClient(),
                    "once the latch is down the sleeping robot shows as asleep");
            robot.discard();
            helper.succeed();
        }, "the robot never ticked, or its charging latch never drained to zero");
    }
}
