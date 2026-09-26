/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The Stripes board's use cycle ({@link AIRobotStripesHandler}) with a stack of blocks: ONE block goes down on the
 * reserved cell itself — not on a neighbour of it — and the rest of the stack stays in the robot's hand for the next
 * cell. Before the fix the whole remainder was deleted when the hand was cleared, and the block landed one cell
 * north of the reserved one (the handlers target {@code pos.relative(direction)}, the pipe-relative convention).
 */
public class RobotStripesTester {

    public static void stripesPlacesOnReservedCellAndKeepsRemainder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = new BlockPos(2, 1, 3);
        BlockPos target = helper.absolutePos(targetRel);

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(at.x, at.y, at.z);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);
        robot.setItemInUse(new ItemStack(Items.COBBLESTONE, 5));

        AIRobotStripesHandler handler = new AIRobotStripesHandler(robot, target);
        handler.start();
        // The use fires on the 61st cycle; stop there (a terminated AI cycled again would fire once more).
        for (int i = 0; i < 61; i++) {
            handler.cycle();
        }

        helper.assertTrue(level.getBlockState(target).is(Blocks.COBBLESTONE),
                "the block goes down on the reserved cell itself");
        helper.assertTrue(level.getBlockState(target.north()).isAir(), "nothing is placed beside the reserved cell");
        ItemStack held = robot.getHeldItem();
        helper.assertTrue(held.is(Items.COBBLESTONE) && held.getCount() == 4,
                "the robot keeps the 4 unplaced cobblestone in hand, holds " + held);

        boolean dropped = false;
        for (Entity e : level.getEntities((Entity) null, new AABB(target).inflate(3), e -> e instanceof ItemEntity)) {
            dropped = true;
            e.discard();
        }
        helper.assertTrue(!dropped, "nothing is dropped on the ground");
        helper.succeed();
    }
}
