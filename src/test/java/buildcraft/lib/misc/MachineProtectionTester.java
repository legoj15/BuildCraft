/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.mj.MjAPI;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.block.ILocalBlockUpdateSubscriber;
import buildcraft.lib.block.LocalBlockUpdateNotifier;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.RobotProtection;
import buildcraft.robotics.ai.AIRobotBreak;
import buildcraft.robotics.ai.AIRobotHarvest;
import buildcraft.robotics.boards.BoardRobotLumberjack;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Machine protection: every BuildCraft machine that breaks blocks must first ask protection mods through
 * {@link BlockUtil#canMachineBreak} (a cancellable break event posted by the machine's fake player), and that probe
 * must not itself look like a block breaking to BuildCraft's own listeners.
 * <p>
 * A "protection mod" here is a break listener that cancels every break at one arena position — exactly how claim
 * mods refuse. Each test registers it, unregisters it in a {@code finally}, and filters on its own position, so
 * concurrent tests in other arena cells are never refused.
 */
public class MachineProtectionTester {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static void requireProtectionChecksOn() {
        check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
    }

    /** Records every break event at {@code pos}, cancelling them all when {@code refuse} is set. */
    private static final class ProtectionMod implements AutoCloseable {
        final List<Player> askedBy = new ArrayList<>();
        private final Object token;

        ProtectionMod(BlockPos pos, boolean refuse) {
            token = BreakEventCompat.onBreak(event -> {
                if (event.getPos().equals(pos)) {
                    askedBy.add(BreakEventCompat.playerOf(event));
                    if (refuse) {
                        BreakEventCompat.cancel(event);
                    }
                }
            });
        }

        @Override
        public void close() {
            BreakEventCompat.removeListener(token);
        }
    }

    /**
     * The permission probe posts a real break event, but nothing breaks — so a nearby laser (any
     * {@link ILocalBlockUpdateSubscriber}) must not be told a block changed. Before the fix every per-block check in a
     * quarry or builder scan made subscribers in range rescan. A genuine break event still reaches them.
     */
    public static void probeIsNotABlockUpdate(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, Blocks.STONE);
        BlockPos pos = helper.absolutePos(rel);
        BlockState stone = level.getBlockState(pos);

        List<BlockPos> updates = new ArrayList<>();
        ILocalBlockUpdateSubscriber laser = new ILocalBlockUpdateSubscriber() {
            @Override
            public BlockPos getSubscriberPos() {
                return pos;
            }

            @Override
            public int getUpdateRange() {
                return 1;
            }

            @Override
            public void setLevelUpdated(Level world, BlockPos eventPos, BlockState oldState, BlockState newState,
                    int flags) {
                updates.add(eventPos);
            }
        };
        LocalBlockUpdateNotifier notifier = LocalBlockUpdateNotifier.instance(level);
        notifier.registerSubscriberForUpdateNotifications(laser);
        try (ProtectionMod observer = new ProtectionMod(pos, false)) {
            check(BlockUtil.canMachineBreak(level, pos, new GameProfile(UUID.randomUUID(), "bc_protection_test")),
                    "an unprotected block may be broken");
            check(!observer.askedBy.isEmpty(), "precondition: the probe was actually posted");
            check(updates.isEmpty(), "the permission probe must not reach block-update subscribers, got " + updates);

            try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level)) {
                LocalBlockUpdateNotifier.onBlockBroken(BreakEventCompat.create(level, pos, stone, lease.player()));
            }
            check(updates.contains(pos), "a genuine break event still reaches subscribers");
        } finally {
            notifier.removeSubscriberFromUpdateNotifications(laser);
        }
        helper.succeed();
    }

    /**
     * A stripes pipe asks before it breaks the block in front of it, as the owner (the "[BuildCraft]" player for an
     * unowned pipe): refused, the block stays however much power arrives; allowed, it breaks.
     */
    public static void stripesPipeAsksBeforeBreaking(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos pipeRel = new BlockPos(2, 1, 2);
        BlockPos targetRel = pipeRel.above();
        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipeRel);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRIPES_ITEM.get()));
        if (!(tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes stripes)) {
            helper.fail("the stripes pipe item did not produce a stripes behaviour");
            return;
        }
        // An unconnected stripes pipe keeps a non-null direction across ticks (it only re-derives it when null).
        stripes.direction = Direction.UP;
        helper.setBlock(targetRel, Blocks.STONE);
        BlockPos target = helper.absolutePos(targetRel);

        try (ProtectionMod claim = new ProtectionMod(target, true)) {
            for (int i = 0; i < 60; i++) {
                stripes.receivePower(64 * MjAPI.MJ, false);
                stripes.onTick();
            }
            check(level.getBlockState(target).is(Blocks.STONE), "a refused stripes pipe must not break the block");
            check(!claim.askedBy.isEmpty(), "the stripes pipe must ask before breaking");
            Player asker = claim.askedBy.get(0);
            check(asker instanceof FakePlayer, "the stripes pipe asks as a fake player");
            check(GameProfileUtil.getId(FakePlayerUtil.BUILDCRAFT_PROFILE).equals(asker.getUUID()),
                    "an unowned stripes pipe asks as the [BuildCraft] identity");
        }

        for (int i = 0; i < 60 && !level.getBlockState(target).isAir(); i++) {
            stripes.receivePower(64 * MjAPI.MJ, false);
            stripes.onTick();
        }
        check(level.getBlockState(target).isAir(), "once allowed, the stripes pipe breaks the block");

        // The cobblestone drop travels down the pipe; empty it so removing the arena's pipe drops nothing.
        for (TravellingItem item : ((PipeFlowItems) tile.getPipe().getFlow()).getAllItemsForRender()) {
            item.getStack().setCount(0);
        }
        helper.succeed();
    }

    private static EntityRobot unaddedRobot(GameTestHelper helper, BlockPos relPos) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);
        return robot;
    }

    /**
     * Robots ask before breaking (the break board AI and the harvester), and their boards skip refused blocks
     * instead of flying back to the same one forever. A robot with no home station asks as "[BuildCraft]".
     */
    public static void robotsAskBeforeBreaking(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos stoneRel = new BlockPos(1, 1, 1);
        BlockPos logRel = new BlockPos(3, 1, 1);
        BlockPos wheatRel = new BlockPos(2, 2, 4);
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(logRel, Blocks.OAK_LOG);
        helper.setBlock(wheatRel.below(), Blocks.FARMLAND);
        helper.setBlock(wheatRel.above(), Blocks.GLOWSTONE);
        helper.setBlock(wheatRel, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        BlockPos stone = helper.absolutePos(stoneRel);
        BlockPos log = helper.absolutePos(logRel);
        BlockPos wheat = helper.absolutePos(wheatRel);

        EntityRobot robot = unaddedRobot(helper, new BlockPos(2, 2, 2));
        check(RobotProtection.ownerOf(robot) == null, "a robot without a home station has no owner");
        robot.setItemInUse(new ItemStack(Items.DIAMOND_PICKAXE));
        BoardRobotLumberjack lumberjack = new BoardRobotLumberjack(robot);

        try (ProtectionMod stoneClaim = new ProtectionMod(stone, true);
                ProtectionMod logClaim = new ProtectionMod(log, true);
                ProtectionMod wheatClaim = new ProtectionMod(wheat, true)) {
            AIRobotBreak breaker = new AIRobotBreak(robot, stone);
            for (int i = 0; i < 20; i++) {
                breaker.cycle();
            }
            check(level.getBlockState(stone).is(Blocks.STONE), "a refused robot must not break the block");
            check(!breaker.success(), "a refused break reports failure");
            check(!stoneClaim.askedBy.isEmpty(), "the robot must ask before breaking");
            check(GameProfileUtil.getId(FakePlayerUtil.BUILDCRAFT_PROFILE).equals(stoneClaim.askedBy.get(0).getUUID()),
                    "an unowned robot asks as the [BuildCraft] identity");

            AIRobotHarvest harvester = new AIRobotHarvest(robot, wheat);
            for (int i = 0; i < 40; i++) {
                harvester.cycle();
            }
            check(level.getBlockState(wheat).is(Blocks.WHEAT), "a refused robot must not harvest the crop");
            check(!wheatClaim.askedBy.isEmpty(), "the harvester must ask before harvesting");

            check(!lumberjack.isSearchCandidate(level, log), "a board skips a block it may not break");
        }
        check(lumberjack.isSearchCandidate(level, log), "an unprotected log is a lumberjack target");

        AIRobotBreak breaker = new AIRobotBreak(robot, stone);
        for (int i = 0; i < 20 && !level.getBlockState(stone).isAir(); i++) {
            breaker.cycle();
        }
        check(level.getBlockState(stone).isAir(), "once allowed, the robot breaks the block");

        // The break drops at the block's centre this same tick; a tight box keeps the cleanup inside this cell
        // (inflate(2) around rel (1,1,1) reached into the neighbouring cell and could eat its items).
        for (Entity e : level.getEntities((Entity) null, new AABB(stone).inflate(0.75),
                e -> e instanceof ItemEntity item && item.getItem().is(Items.COBBLESTONE))) {
            e.discard();
        }
        helper.succeed();
    }
}
