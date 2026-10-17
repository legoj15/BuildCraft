/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.entity.EntityRobot;

/** The bomber's drop leg: arriving over the target releases exactly ONE primed TNT and keeps the rest of
 *  the cargo, as 7.1.x did (its transactor's {@code remove} took a single item). The port's first
 *  transcription extracted up to a whole stack and primed only one, silently destroying the other 63. */
public class BomberBoardTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    /** A robot holding five TNT arrives over the target: one primed TNT appears in the world and four
     *  stay aboard. The arrival is hand-fed ({@code delegateAIEnded} with the goto that just succeeded),
     *  exactly what {@code AIRobot.terminate()} does when the real flight lands, so no flight, ticking or
     *  station is needed — and the primed TNT is discarded before its fuse can matter. */
    public static void bomberDropsExactlyOneTnt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 4, 2);

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        robot.setInventoryStack(0, new ItemStack(Items.TNT, 5));

        AABB arena = new AABB(pos, pos).inflate(4.0, 4.0, 4.0);
        List<PrimedTnt> preexisting = new ArrayList<>(level.getEntitiesOfClass(PrimedTnt.class, arena));
        BoardRobotBomber board = new BoardRobotBomber(robot);

        AIRobotGotoBlock arrived = new AIRobotGotoBlock(robot, robot.blockPosition().getX(),
                robot.blockPosition().getY(), robot.blockPosition().getZ());
        board.delegateAIEnded(arrived);

        List<PrimedTnt> primed = new ArrayList<>(level.getEntitiesOfClass(PrimedTnt.class, arena));
        primed.removeAll(preexisting);
        // Discarded before any assertion can throw: a leaked primed TNT would blow up inside a sibling test.
        primed.forEach(PrimedTnt::discard);

        helper.assertTrue(primed.size() == 1,
                "arriving over the target primes exactly one TNT, got " + primed.size());

        int remaining = 0;
        for (int slot = 0; slot < robot.getInventorySize(); slot++) {
            ItemStack stack = robot.getInventoryStack(slot);
            if (stack.is(Items.TNT)) {
                remaining += stack.getCount();
            }
        }
        helper.assertTrue(remaining == 4,
                "one TNT left the cargo — the other four must stay aboard, got " + remaining);
        helper.succeed();
    }
}
