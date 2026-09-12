/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;

/** Ph9 wearable acceptance: 7.1.x's interact rules on the modern robot — a helmet-slot armour piece, a
 *  skull, or a robot overlay item is WORN (one per click, up to {@link EntityRobot#MAX_WEARABLES}),
 *  anything else is not; a sneaking wrench peels the last wearable back off; the worn list survives the
 *  NBT round trip. The Ph3-era plumbing (NBT key, spawn sync, drop path, damage reduction loop) shipped
 *  with the entity — this pins the acceptance that makes any of it reachable. */
public class RobotWearablesTester {

    public static void wearsEquipsAndPeelsWearables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 2, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        var pos = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(robot);

        // A mock player stands in for the interacting hand: interact only reads the held stack and
        // swings, which the anonymous Player satisfies (the GuiTester precedent).
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);

        try {
            // A diamond helmet is worn; exactly one leaves the hand.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HELMET, 3));
            InteractionResult result = interact(robot, player);
            helper.assertTrue(result == InteractionResult.SUCCESS,
                    "clicking with a helmet equips it, got " + result);
            helper.assertTrue(robot.getWearables().size() == 1,
                    "the helmet is aboard, got " + robot.getWearables().size());
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
                    "exactly one helmet left the player's hand");

            // A skull is worn too (7.1.x's ItemSkull family).
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SKELETON_SKULL, 1));
            result = interact(robot, player);
            helper.assertTrue(result == InteractionResult.SUCCESS,
                    "clicking with a skull equips it, got " + result);
            helper.assertTrue(robot.getWearables().size() == 2,
                    "the skull is worn beside the helmet");

            // Cobblestone is not wearable — the interaction passes through untouched.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 1));
            result = interact(robot, player);
            helper.assertTrue(result == InteractionResult.PASS,
                    "cobblestone is not worn — the interact passes, got " + result);
            helper.assertTrue(robot.getWearables().size() == 2,
                    "no third wearable appeared");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "the cobblestone never left the hand");

            // The NBT round trip keeps the worn pair (the Ph3 plumbing's key, now reachable).
            var tag = EntityArenaUtil.saveEntity(level, robot);
            EntityArenaUtil.loadEntity(level, robot, tag);
            helper.assertTrue(robot.getWearables().size() == 2,
                    "the wearables survive the save/load round trip, got " + robot.getWearables().size());
            helper.assertTrue(robot.getWearables().stream().anyMatch(s -> s.is(Items.DIAMOND_HELMET)),
                    "the helmet is still worn after the round trip");
            helper.assertTrue(robot.getWearables().stream().anyMatch(s -> s.is(Items.SKELETON_SKULL)),
                    "the skull is still worn after the round trip");

            // A sneaking wrench peels the LAST wearable (the skull, added second).
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(buildcraft.core.BCCoreItems.WRENCH.get()));
            player.setShiftKeyDown(true);
            interact(robot, player);
            helper.assertTrue(robot.getWearables().size() == 1,
                    "the wrench peels one wearable, leaving one, got " + robot.getWearables().size());
            helper.assertTrue(robot.getWearables().get(0).is(Items.DIAMOND_HELMET),
                    "the peel takes the LAST wearable (the skull), keeping the helmet");
            // The peeled skull drops into the world; keep it out of sibling arenas.
            for (var item : EntityArenaUtil.droppedItems(helper, robotRel, 3)) {
                item.discard();
            }
        } finally {
            player.discard();
            robot.discard();
        }
        helper.succeed();
    }

    /** The interact entry point fork: 26.1 grew the third hit-location argument. */
    private static InteractionResult interact(EntityRobot robot,
            net.minecraft.world.entity.player.Player player) {
        //? if >=26.1 {
        return robot.interact(player, InteractionHand.MAIN_HAND, new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5));
        //?} else {
        /*return robot.interact(player, InteractionHand.MAIN_HAND);*/
        //?}
    }
}
