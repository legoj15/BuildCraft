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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import buildcraft.api.mj.MjAPI;
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

    // ---------- armour discounts the damage debit ----------

    private static final long DAMAGE_PER_POINT = 260L * MjAPI.MJ;

    /** A fresh unadded robot wearing {@code worn} (put on through the real interact path, one click each)
     *  takes a 2-point hit, and the battery loses exactly {@code expectedPerPoint * 2}. */
    private static void assertDebit(GameTestHelper helper, String label, long expectedPerPoint,
            ItemStack... worn) {
        ServerLevel level = helper.getLevel();
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        try {
            for (ItemStack piece : worn) {
                player.setItemInHand(InteractionHand.MAIN_HAND, piece.copy());
                InteractionResult result = interact(robot, player);
                helper.assertTrue(result == InteractionResult.SUCCESS,
                        label + ": " + piece.getItem() + " must be wearable, got " + result);
            }
            helper.assertTrue(robot.getWearables().size() == worn.length,
                    label + ": every piece is aboard, got " + robot.getWearables().size());

            long stored = 5000L * MjAPI.MJ;
            robot.getBattery().setStored(stored);
            Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
            boolean hurt = EntityArenaUtil.hurt(level, robot, level.damageSources().playerAttack(attacker), 2.0F);
            attacker.discard();

            helper.assertTrue(hurt, label + ": the hit lands");
            long debit = stored - robot.getBattery().getStored();
            helper.assertTrue(debit == 2 * expectedPerPoint,
                    label + ": a 2-point hit must cost " + (2 * expectedPerPoint) + " but cost " + debit);
        } finally {
            player.discard();
        }
    }

    /** 7.1.x: each worn ARMOUR piece scaled the per-point debit by {@code 2 / (2 + protection)} and every
     *  other wearable took a flat 30% off. The port had only the flat 30% (its comment promised the armour
     *  hookup "after wearable acceptance", which shipped without it). Iron helmet = 2 protection (halves
     *  the debit), diamond = 3 (two fifths); a skull and a carved pumpkin are not armour (0.7 each, the
     *  pumpkin because it carries no armour attribute — 7.1.x's non-ItemArmor branch); and pieces compound
     *  in the order worn. */
    public static void armourScalesTheDamageDebit(GameTestHelper helper) {
        assertDebit(helper, "bare robot", DAMAGE_PER_POINT);
        assertDebit(helper, "iron helmet", DAMAGE_PER_POINT * 2 / 4, new ItemStack(Items.IRON_HELMET));
        assertDebit(helper, "diamond helmet", DAMAGE_PER_POINT * 2 / 5, new ItemStack(Items.DIAMOND_HELMET));
        assertDebit(helper, "skull", DAMAGE_PER_POINT * 7 / 10, new ItemStack(Items.SKELETON_SKULL));
        assertDebit(helper, "carved pumpkin", DAMAGE_PER_POINT * 7 / 10, new ItemStack(Items.CARVED_PUMPKIN));
        assertDebit(helper, "iron helmet then skull", DAMAGE_PER_POINT * 2 / 4 * 7 / 10,
                new ItemStack(Items.IRON_HELMET), new ItemStack(Items.SKELETON_SKULL));
        assertDebit(helper, "iron then diamond helmet", DAMAGE_PER_POINT * 2 / 4 * 2 / 5,
                new ItemStack(Items.IRON_HELMET), new ItemStack(Items.DIAMOND_HELMET));
        helper.succeed();
    }

    // ---------- acceptance reads the synced view, not the stale field ----------

    /** A robot whose own list is empty but whose synced wearable view reports {@code worn} helmets: exactly the
     *  client's situation, where the {@code wearables} field is never updated after spawn (its size stays 0) and
     *  {@link EntityRobot#getWearables()} - rebuilt from the synced accessors - is the truth. */
    private static EntityRobot robotWhoseSyncedViewHolds(ServerLevel level, int worn) {
        java.util.List<ItemStack> view = new java.util.ArrayList<>();
        for (int i = 0; i < worn; i++) {
            view.add(new ItemStack(Items.IRON_HELMET));
        }
        return new EntityRobot(BCRoboticsEntities.ROBOT.get(), level) {
            @Override
            public java.util.List<ItemStack> getWearables() {
                return view;
            }
        };
    }

    /** The click's client half predicts the server's answer: SUCCESS makes the client swing. The acceptance check
     *  counted the private {@code wearables} field, which the client never updates, so a client looking at a
     *  full robot (eight helmets in the synced slots) still predicted SUCCESS and swung at a robot the server
     *  refuses. It must count the synced view - checked here on the server with a robot whose view is full but
     *  whose field is empty, which only the synced view can tell apart from a bare robot. */
    public static void fullSyncedWearablesRefuseAnotherPiece(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        EntityRobot full = robotWhoseSyncedViewHolds(level, EntityRobot.MAX_WEARABLES);
        EntityRobot roomForOne = robotWhoseSyncedViewHolds(level, EntityRobot.MAX_WEARABLES - 1);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET, 2));
            InteractionResult result = interact(full, player);
            helper.assertTrue(result == InteractionResult.PASS,
                    "a robot whose synced view is full must refuse a ninth piece, got " + result);
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
                    "a refused click takes nothing from the hand");

            result = interact(roomForOne, player);
            helper.assertTrue(result == InteractionResult.SUCCESS,
                    "one slot short of full still takes a piece, got " + result);
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "the accepted piece left the hand");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    // ---------- creative players keep what they equip ----------

    /** The player-side half of the interact: {@code Player.interactOn} is what the server's packet handler
     *  calls, and it is where vanilla restores a creative player's stack when an entity interaction used
     *  an item up ({@code hasInfiniteMaterials}). The robot's own {@code interact} (what the tests above
     *  call) shrinks the stack unconditionally; going through the real entry point shows what a creative
     *  player actually experiences: the helmet goes on the robot and the stack in hand stays whole, while a
     *  survival player pays one. */
    public static void creativeEquipKeepsTheHeldItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        var creative = helper.makeMockPlayer(GameType.CREATIVE);
        var survival = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            // makeMockPlayer only overrides gameMode(); a real player's abilities follow the game type.
            GameType.CREATIVE.updatePlayerAbilities(creative.getAbilities());
            GameType.SURVIVAL.updatePlayerAbilities(survival.getAbilities());

            creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET, 3));
            InteractionResult result = interactOn(creative, robot);
            helper.assertTrue(result.consumesAction(), "the creative click equips the helmet, got " + result);
            helper.assertTrue(robot.getWearables().size() == 1, "the helmet is aboard");
            helper.assertTrue(creative.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 3,
                    "a creative player keeps the whole stack, got "
                            + creative.getItemInHand(InteractionHand.MAIN_HAND).getCount());

            survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET, 3));
            result = interactOn(survival, robot);
            helper.assertTrue(result.consumesAction(), "the survival click equips the helmet, got " + result);
            helper.assertTrue(robot.getWearables().size() == 2, "the second helmet is aboard");
            helper.assertTrue(survival.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
                    "a survival player pays one helmet, got "
                            + survival.getItemInHand(InteractionHand.MAIN_HAND).getCount());
        } finally {
            creative.discard();
            survival.discard();
        }
        helper.succeed();
    }

    /** {@code Player.interactOn} grew the hit-location argument at 26.1, alongside {@code Entity.interact}. */
    private static InteractionResult interactOn(net.minecraft.world.entity.player.Player player,
            EntityRobot robot) {
        //? if >=26.1 {
        return player.interactOn(robot, InteractionHand.MAIN_HAND, new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5));
        //?} else {
        /*return player.interactOn(robot, InteractionHand.MAIN_HAND);*/
        //?}
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
