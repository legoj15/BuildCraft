/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.MachineBreakAttributionTester;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/**
 * A robot with no recorded deployer (these never had one: see {@code RobotDeployerProtectionTester} for one that
 * does) acts for the owner of its home station, so protection mods judge that player, not the generic
 * "[BuildCraft]" identity: the robot's planting, tool use and block breaking must run as the station owner's fake
 * player, the way the break AI's permission probe and the Stripes board already do. The observers are the events a
 * claim mod would cancel: the block-placement event for a planted seed, the right-click-block event a hoe's till
 * starts with (the one ask every node shares; 26.3 posts no tool-modification event for a till), the break-speed
 * event for a pickaxe swing at stone (the only player-attributed event the break itself posts - the destroy that
 * follows posts none).
 *
 * <p>A used tool is also in the fake player's hand while it is used: a claim or tool listener reads the player's
 * hand, not the item stack the event carries.
 */
public class RobotOwnerIdentityTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    private static GameProfile ownerProfile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    /** A robot with a home station owned by {@code owner}, not added to the level (cycled by hand). */
    private static EntityRobot robotOwnedBy(GameTestHelper helper, BlockPos relPos, GameProfile owner) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        robot.setMainStation(new DockingStation(helper.absolutePos(relPos), Direction.UP) {
            @Override
            public GameProfile getOwner() {
                return owner;
            }

            @Override
            public Iterable<StatementSlot> getActiveActions() {
                return Collections.emptyList();
            }
        });
        return robot;
    }

    private static void requireActedAs(GameTestHelper helper, List<Entity> actors, GameProfile owner, String what) {
        helper.assertTrue(!actors.isEmpty(), what + ": the event must have been posted");
        for (Entity actor : actors) {
            helper.assertTrue(actor instanceof FakePlayer, what + ": acts as a fake player, got " + actor);
            helper.assertTrue(GameProfileUtil.getId(owner).equals(actor.getUUID()),
                    what + ": acts as the station owner " + GameProfileUtil.getName(owner)
                            + ", got " + actor.getName().getString() + " (" + actor.getUUID() + ")");
        }
    }

    /** The seed is planted as the owner: the placement event names the owner's fake player. */
    public static void plantActsAsTheStationOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = ownerProfile("bc_robot_plant_owner");
        BlockPos soilRel = new BlockPos(2, 2, 2);
        BlockPos robotRel = new BlockPos(3, 2, 2);
        helper.setBlock(soilRel.below(), Blocks.FARMLAND);
        helper.setBlock(soilRel.above(), Blocks.GLOWSTONE);
        BlockPos planted = helper.absolutePos(soilRel);

        EntityRobot robot = robotOwnedBy(helper, robotRel, owner);
        robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS));

        List<Entity> placers = new ArrayList<>();
        Consumer<BlockEvent.EntityPlaceEvent> observer = event -> {
            if (event.getPos().equals(planted)) {
                placers.add(event.getEntity());
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, observer);
        try {
            AIRobotPlant planter = new AIRobotPlant(robot, helper.absolutePos(soilRel.below()));
            for (int cycles = 0; cycles < 80 && !level.getBlockState(planted).is(Blocks.WHEAT); cycles++) {
                planter.cycle();
            }
            helper.assertTrue(level.getBlockState(planted).is(Blocks.WHEAT), "precondition: the seed was planted");
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
        }
        requireActedAs(helper, placers, owner, "planting");
        helper.succeed();
    }

    /** A hoe tills dirt as the owner: the right-click-block event names the owner's fake player. */
    public static void useToolActsAsTheStationOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = ownerProfile("bc_robot_tool_owner");
        BlockPos dirtRel = new BlockPos(2, 1, 2);
        helper.setBlock(dirtRel, Blocks.DIRT);
        BlockPos dirt = helper.absolutePos(dirtRel);

        EntityRobot robot = robotOwnedBy(helper, dirtRel.above(), owner);
        robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));

        List<Entity> users = new ArrayList<>();
        Consumer<PlayerInteractEvent.RightClickBlock> observer = event -> {
            if (event.getPos().equals(dirt)) {
                Player player = event.getEntity();
                users.add(player);
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, observer);
        try {
            AIRobotUseToolOnBlock user = new AIRobotUseToolOnBlock(robot, dirt);
            for (int cycles = 0; cycles < 80 && !level.getBlockState(dirt).is(Blocks.FARMLAND); cycles++) {
                user.cycle();
            }
            helper.assertTrue(level.getBlockState(dirt).is(Blocks.FARMLAND), "precondition: the dirt was tilled");
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
        }
        requireActedAs(helper, users, owner, "tool use");
        helper.succeed();
    }

    /** The robot's swings at a block run as the owner: the break-speed event, posted once per cycle by vanilla's
     *  destroy-progress maths, names the owner's fake player, never the generic "[BuildCraft]" one. */
    public static void breakActsAsTheStationOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = ownerProfile("bc_robot_break_owner");
        BlockPos stoneRel = new BlockPos(2, 1, 2);
        helper.setBlock(stoneRel, Blocks.STONE);
        BlockPos stone = helper.absolutePos(stoneRel);

        EntityRobot robot = robotOwnedBy(helper, stoneRel.above(), owner);
        robot.setItemInUse(new ItemStack(Items.DIAMOND_PICKAXE));

        List<Entity> swingers = new ArrayList<>();
        Consumer<PlayerEvent.BreakSpeed> observer = event -> {
            if (event.getPosition().filter(stone::equals).isPresent()) {
                swingers.add(event.getEntity());
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerEvent.BreakSpeed.class, observer);
        try {
            AIRobotBreak breaker = new AIRobotBreak(robot, stone);
            for (int cycles = 0; cycles < 40 && !level.getBlockState(stone).isAir(); cycles++) {
                breaker.cycle();
            }
            helper.assertTrue(level.getBlockState(stone).isAir(), "precondition: the stone was broken");
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
            for (ItemEntity drop : EntityArenaUtil.droppedItems(helper, stoneRel, 0.75)) {
                drop.discard();
            }
        }
        requireActedAs(helper, swingers, owner, "breaking");
        helper.succeed();
    }

    /** A crop is harvested as the owner: the harvest's block-destroy game event names the owner's fake player (the
     *  crop API used to harvest with no player at all, so the destroy, and the crop's loot, had no breaker). */
    public static void harvestActsAsTheStationOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = ownerProfile("bc_robot_harvest_owner");
        BlockPos wheatRel = new BlockPos(2, 2, 2);
        BlockPos robotRel = new BlockPos(3, 2, 2);
        helper.setBlock(wheatRel.below(), Blocks.FARMLAND);
        helper.setBlock(wheatRel.above(), Blocks.GLOWSTONE);
        helper.setBlock(wheatRel, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        BlockPos wheat = helper.absolutePos(wheatRel);

        EntityRobot robot = robotOwnedBy(helper, robotRel, owner);
        try (MachineBreakAttributionTester.BreakerObserver observer =
                     new MachineBreakAttributionTester.BreakerObserver(wheat)) {
            AIRobotHarvest harvester = new AIRobotHarvest(robot, wheat);
            for (int cycles = 0; cycles < 40 && level.getBlockState(wheat).is(Blocks.WHEAT); cycles++) {
                harvester.cycle();
            }
            helper.assertTrue(!level.getBlockState(wheat).is(Blocks.WHEAT), "precondition: the wheat was harvested");
            observer.requireBrokenBy(helper, owner, "harvesting");
        } finally {
            // The harvest drops at the robot's feet this same tick.
            for (ItemEntity drop : EntityArenaUtil.droppedItems(helper, robotRel, 0.75)) {
                drop.discard();
            }
        }
        helper.succeed();
    }

    /** A hoe tills dirt with the hoe in the fake player's main hand: the right-click-block event's player holds the
     *  tool, and the robot gets the hoe back with the wear the use put on it. */
    public static void useToolHoldsTheToolInTheFakePlayersHand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = ownerProfile("bc_robot_tool_hand");
        BlockPos dirtRel = new BlockPos(2, 1, 2);
        helper.setBlock(dirtRel, Blocks.DIRT);
        BlockPos dirt = helper.absolutePos(dirtRel);

        EntityRobot robot = robotOwnedBy(helper, dirtRel.above(), owner);
        robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));

        List<ItemStack> hands = new ArrayList<>();
        Consumer<PlayerInteractEvent.RightClickBlock> observer = event -> {
            if (event.getPos().equals(dirt)) {
                hands.add(event.getEntity().getMainHandItem().copy());
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, observer);
        try {
            AIRobotUseToolOnBlock user = new AIRobotUseToolOnBlock(robot, dirt);
            for (int cycles = 0; cycles < 80 && !level.getBlockState(dirt).is(Blocks.FARMLAND); cycles++) {
                user.cycle();
            }
            helper.assertTrue(level.getBlockState(dirt).is(Blocks.FARMLAND), "precondition: the dirt was tilled");
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
        }
        helper.assertTrue(!hands.isEmpty(), "the right-click-block event must have been posted");
        for (ItemStack inHand : hands) {
            helper.assertTrue(inHand.is(Items.DIAMOND_HOE),
                    "a listener reading the player's hand must see the hoe in use, got " + inHand);
        }
        helper.assertTrue(robot.getHeldItem().is(Items.DIAMOND_HOE),
                "the robot gets the hoe back, got " + robot.getHeldItem());
        helper.assertTrue(robot.getHeldItem().getDamageValue() == 1,
                "...with the use's wear on it, got damage " + robot.getHeldItem().getDamageValue());
        helper.succeed();
    }

    /** A hoe on its last point of durability breaks in the fake player's hand: the robot's hand is empty afterwards,
     *  not left holding the spent hoe. */
    public static void useToolBreakingTheToolEmptiesTheRobotsHand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = ownerProfile("bc_robot_tool_breaks");
        BlockPos dirtRel = new BlockPos(2, 1, 2);
        helper.setBlock(dirtRel, Blocks.DIRT);
        BlockPos dirt = helper.absolutePos(dirtRel);

        EntityRobot robot = robotOwnedBy(helper, dirtRel.above(), owner);
        ItemStack hoe = new ItemStack(Items.WOODEN_HOE);
        hoe.setDamageValue(hoe.getMaxDamage() - 1);
        robot.setItemInUse(hoe);

        AIRobotUseToolOnBlock user = new AIRobotUseToolOnBlock(robot, dirt);
        for (int cycles = 0; cycles < 80 && !level.getBlockState(dirt).is(Blocks.FARMLAND); cycles++) {
            user.cycle();
        }
        helper.assertTrue(level.getBlockState(dirt).is(Blocks.FARMLAND), "precondition: the dirt was tilled");
        helper.assertTrue(robot.getHeldItem().isEmpty(),
                "the hoe broke with that use, so the robot's hand is empty - got " + robot.getHeldItem());
        helper.succeed();
    }
}
