/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.entity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import buildcraft.core.BCCoreItems;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsItems;

/**
 * Robot naming (7.2.x parity): a name tag renamed on an anvil names the robot it is used on, and the name rides the
 * robot item through a wrench pickup. The robot is a bare {@code Entity}, and vanilla's name tag only works on a
 * {@code LivingEntity} ({@code NameTagItem.interactLivingEntity}, which {@code Player.interactOn} reaches only for
 * living targets), so the rename has to be the robot's own interact branch. The data half - the custom name
 * surviving a save - is pinned by {@code ProgrammingTableTester#robotNameTagDataRoundTrip}; the placement half (an
 * anvil-named robot ITEM placing a named robot) by {@code ItemRobotPlacementTester}.
 */
public class RobotNameTagTester {

    private static final BlockPos ROBOT_REL = new BlockPos(2, 2, 2);

    private static EntityRobot spawnRobot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityArenaUtil.forceLoadEntityArena(helper, ROBOT_REL);
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(ROBOT_REL));
        robot.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(robot);
        return robot;
    }

    private static ItemStack nameTag(String name, int count) {
        ItemStack tag = new ItemStack(Items.NAME_TAG, count);
        if (name != null) {
            tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        return tag;
    }

    /** The interact entry point fork: 26.1 grew the third hit-location argument. */
    private static InteractionResult interact(EntityRobot robot, Player player) {
        //? if >=26.1 {
        return robot.interact(player, InteractionHand.MAIN_HAND, new Vec3(0.5, 0.5, 0.5));
        //?} else {
        /*return robot.interact(player, InteractionHand.MAIN_HAND);*/
        //?}
    }

    /** A named tag names the robot and is used up one at a time; a tag with no name does nothing (vanilla's
     *  {@code NameTagItem} passes on it too), and a second tag renames. */
    public static void nameTagNamesTheRobot(GameTestHelper helper) {
        EntityRobot robot = spawnRobot(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, nameTag(null, 1));
            InteractionResult result = interact(robot, player);
            helper.assertTrue(!result.consumesAction(), "a blank name tag has no name to give, got " + result);
            helper.assertFalse(robot.hasCustomName(), "...and the robot stays unnamed");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "...and the blank tag is not used up");

            player.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Robbie", 2));
            result = interact(robot, player);
            helper.assertTrue(result.consumesAction(), "a named tag names the robot, got " + result);
            helper.assertTrue(robot.hasCustomName() && "Robbie".equals(robot.getCustomName().getString()),
                    "the robot is called Robbie, got " + robot.getCustomName());
            helper.assertTrue("Robbie".equals(robot.getDisplayName().getString()),
                    "...and shows it, got " + robot.getDisplayName().getString());
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "one tag was used up, one left");

            player.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Wall-E", 1));
            interact(robot, player);
            helper.assertTrue("Wall-E".equals(robot.getCustomName().getString()),
                    "a second tag renames it, got " + robot.getCustomName());

            var tag = EntityArenaUtil.saveEntity(helper.getLevel(), robot);
            EntityArenaUtil.loadEntity(helper.getLevel(), robot, tag);
            helper.assertTrue(robot.hasCustomName() && "Wall-E".equals(robot.getCustomName().getString()),
                    "the name survives a save and load");
        } finally {
            player.discard();
            robot.discard();
        }
        helper.succeed();
    }

    /** A sneaking wrench turns a named robot back into its item, and that item carries the name: the robot drop is
     *  the first stack of {@code getDrops()} and the one that lands in the world. */
    public static void namedRobotKeepsItsNameThroughWrenchPickup(GameTestHelper helper) {
        EntityRobot robot = spawnRobot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        try {
            robot.setCustomName(Component.literal("Robbie"));

            Component named = robot.getDrops().get(0).get(DataComponents.CUSTOM_NAME);
            helper.assertTrue(named != null && "Robbie".equals(named.getString()),
                    "the robot's item drop carries its name, got " + named);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCCoreItems.WRENCH.get()));
            player.setShiftKeyDown(true);
            interact(robot, player);
            helper.assertTrue(robot.isRemoved(), "the wrench picked the robot up");

            List<ItemEntity> dropped = EntityArenaUtil.droppedItems(helper, ROBOT_REL, 3).stream()
                    .filter(item -> item.getItem().is(BCRoboticsItems.ROBOT.get())).toList();
            try {
                helper.assertTrue(dropped.size() == 1, "one robot item was dropped, found " + dropped.size());
                Component droppedName = dropped.get(0).getItem().get(DataComponents.CUSTOM_NAME);
                helper.assertTrue(droppedName != null && "Robbie".equals(droppedName.getString()),
                        "the dropped robot item is still called Robbie, got " + droppedName);
            } finally {
                dropped.forEach(ItemEntity::discard);
            }
        } finally {
            player.discard();
            if (!robot.isRemoved()) {
                robot.discard();
            }
        }
        helper.succeed();
    }
}
