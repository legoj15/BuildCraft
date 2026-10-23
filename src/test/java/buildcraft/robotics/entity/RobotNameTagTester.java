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

    /** {@code Player.interactOn} grew the hit-location argument at 26.1, alongside {@code Entity.interact}. */
    private static InteractionResult interactOn(Player player, EntityRobot robot) {
        //? if >=26.1 {
        return player.interactOn(robot, InteractionHand.MAIN_HAND, new Vec3(0.5, 0.5, 0.5));
        //?} else {
        /*return player.interactOn(robot, InteractionHand.MAIN_HAND);*/
        //?}
    }

    /**
     * A creative player keeps the name tag, as with every named mob. Vanilla's {@code NameTagItem} itself shrinks the
     * stack unconditionally; what spares a creative player is {@code Player.interactOn}, which restores the held
     * stack's count after ANY entity's {@code interact} consumed the action ({@code hasInfiniteMaterials}) - the
     * robot's own {@code interact} (what the tests above call) is reached through exactly that, so the rename needs no
     * creative check of its own. This pins the real entry point's outcome: the robot is named either way, the creative
     * player's stack is whole and the survival player's is one short.
     */
    public static void creativeNameTagIsNotUsedUp(GameTestHelper helper) {
        EntityRobot robot = spawnRobot(helper);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            // makeMockPlayer only overrides gameMode(); a real player's abilities follow the game type.
            GameType.CREATIVE.updatePlayerAbilities(creative.getAbilities());
            GameType.SURVIVAL.updatePlayerAbilities(survival.getAbilities());

            creative.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Robbie", 3));
            InteractionResult result = interactOn(creative, robot);
            helper.assertTrue(result.consumesAction(), "the creative click names the robot, got " + result);
            helper.assertTrue(robot.hasCustomName() && "Robbie".equals(robot.getCustomName().getString()),
                    "the robot is called Robbie, got " + robot.getCustomName());
            helper.assertTrue(creative.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 3,
                    "a creative player keeps the whole stack of tags, got "
                            + creative.getItemInHand(InteractionHand.MAIN_HAND).getCount());

            survival.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Wall-E", 3));
            result = interactOn(survival, robot);
            helper.assertTrue(result.consumesAction(), "the survival click renames the robot, got " + result);
            helper.assertTrue("Wall-E".equals(robot.getCustomName().getString()),
                    "the robot is now Wall-E, got " + robot.getCustomName());
            helper.assertTrue(survival.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
                    "a survival player pays one tag, got "
                            + survival.getItemInHand(InteractionHand.MAIN_HAND).getCount());
        } finally {
            creative.discard();
            survival.discard();
            robot.discard();
        }
        helper.succeed();
    }

    /** Ticks the cell gets to become entity-ticking: the item the pickup drops is invisible to a scan until its
     *  chunk is, and the 6x7 cell crosses a chunk border in about 57% of runs. The item then gets
     *  {@link #PICKUP_WORK_TICKS} to show up. */
    private static final int PICKUP_READY_TICKS = 300;
    private static final int PICKUP_WORK_TICKS = 20;

    /** The robot items lying in this test's own cell. */
    private static List<ItemEntity> droppedRobotItems(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper),
                item -> item.getItem().is(BCRoboticsItems.ROBOT.get()));
    }

    /** A sneaking wrench turns a named robot back into its item, and that item carries the name: the robot drop is
     *  the first stack of {@code getDrops()} and the one that lands in the world.
     *
     *  <p>The pickup waits for the cell to tick and then polls for the item rather than scanning in the tick of the
     *  drop: an entity added to a chunk that is not entity-ticking yet is invisible to {@code getEntitiesOfClass}. */
    public static void namedRobotKeepsItsNameThroughWrenchPickup(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        EntityRobot[] robot = { null };
        boolean[] pickedUp = { false };
        EntityArenaUtil.tickUntil(helper, PICKUP_READY_TICKS + PICKUP_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, PICKUP_READY_TICKS, PICKUP_WORK_TICKS, () -> {
                    if (!pickedUp[0]) {
                        pickedUp[0] = true;
                        robot[0] = spawnRobot(helper);
                        robot[0].setCustomName(Component.literal("Robbie"));

                        Component named = robot[0].getDrops().get(0).get(DataComponents.CUSTOM_NAME);
                        helper.assertTrue(named != null && "Robbie".equals(named.getString()),
                                "the robot's item drop carries its name, got " + named);

                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCCoreItems.WRENCH.get()));
                        player.setShiftKeyDown(true);
                        interact(robot[0], player);
                        helper.assertTrue(robot[0].isRemoved(), "the wrench picked the robot up");
                        return false;
                    }
                    return !droppedRobotItems(helper).isEmpty();
                }, () -> pickedUp[0] ? "the picked-up robot's item never showed up in the cell" : null),
                () -> {
                    List<ItemEntity> dropped = droppedRobotItems(helper);
                    helper.assertTrue(dropped.size() == 1, "one robot item was dropped, found " + dropped.size());
                    Component droppedName = dropped.get(0).getItem().get(DataComponents.CUSTOM_NAME);
                    helper.assertTrue(droppedName != null && "Robbie".equals(droppedName.getString()),
                            "the dropped robot item is still called Robbie, got " + droppedName);
                    helper.succeed();
                },
                "the picked-up robot's item never showed up in the cell",
                () -> {
                    droppedRobotItems(helper).forEach(ItemEntity::discard);
                    EntityArenaUtil.discard(robot[0]);
                    player.discard();
                });
    }
}
