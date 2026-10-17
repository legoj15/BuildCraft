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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/**
 * A robot acts for the owner of its home station, so protection mods judge that player, not the generic
 * "[BuildCraft]" identity: the robot's planting and tool use must run as the station owner's fake player, the way
 * the break AI's permission probe and the Stripes board already do. The observers are the events a claim mod would
 * cancel: the block-placement event for a planted seed, the tool-modification event for a hoe tilling dirt.
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

    /** A hoe tills dirt as the owner: the tool-modification event names the owner's fake player. */
    public static void useToolActsAsTheStationOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = ownerProfile("bc_robot_tool_owner");
        BlockPos dirtRel = new BlockPos(2, 1, 2);
        helper.setBlock(dirtRel, Blocks.DIRT);
        BlockPos dirt = helper.absolutePos(dirtRel);

        EntityRobot robot = robotOwnedBy(helper, dirtRel.above(), owner);
        robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));

        List<Entity> users = new ArrayList<>();
        Consumer<BlockEvent.BlockToolModificationEvent> observer = event -> {
            if (event.getPos().equals(dirt)) {
                Player player = event.getPlayer();
                users.add(player);
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.BlockToolModificationEvent.class, observer);
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
}
