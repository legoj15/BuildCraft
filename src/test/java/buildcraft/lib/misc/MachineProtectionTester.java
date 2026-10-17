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
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.block.ILocalBlockUpdateSubscriber;
import buildcraft.lib.block.LocalBlockUpdateNotifier;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.RobotProtection;
import buildcraft.robotics.ai.AIRobotBreak;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotHarvest;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
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
    public static final class ProtectionMod implements AutoCloseable {
        public final List<Player> askedBy = new ArrayList<>();
        private final Object token;

        public ProtectionMod(BlockPos pos, boolean refuse) {
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
        BlockPos targetRel = new BlockPos(2, 2, 2);
        TilePipeHolder tile = placeStripesPipeFacingUp(helper, new BlockPos(2, 1, 2));
        PipeBehaviourStripes stripes = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
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

        // The claim is lifted without the block changing - nothing fires an event for that - so the pipe notices
        // only once its retry window has passed.
        for (int i = 0; i < PipeBehaviourStripes.REFUSAL_RETRY_TICKS + 60 && !level.getBlockState(target).isAir(); i++) {
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

    /** A stripes pipe at {@code pipeRel}, facing up (an unconnected one keeps a non-null direction across ticks). */
    private static TilePipeHolder placeStripesPipeFacingUp(GameTestHelper helper, BlockPos pipeRel) {
        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipeRel);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRIPES_ITEM.get()));
        if (!(tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes stripes)) {
            throw new IllegalStateException("the stripes pipe item did not produce a stripes behaviour");
        }
        stripes.direction = Direction.UP;
        return tile;
    }

    /**
     * A stripes pipe whose block was refused backs off: it asks once, then neither spends power on the block nor asks
     * again until the block turns into something else (which is a new question, asked at once). Before the fix the
     * refusal reset the progress but kept the direction, so the pipe re-accumulated the whole break cost and re-asked
     * once per cycle for as long as the claim stood.
     */
    public static void stripesPipeBacksOffFromARefusedBlock(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos targetRel = new BlockPos(2, 2, 2);
        TilePipeHolder tile = placeStripesPipeFacingUp(helper, new BlockPos(2, 1, 2));
        PipeBehaviourStripes stripes = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
        helper.setBlock(targetRel, Blocks.STONE);
        BlockPos target = helper.absolutePos(targetRel);
        // The pipe's battery holds 256 MJ: that much arrives while it fills, and anything beyond it was spent.
        long batteryCapacity = 256 * MjAPI.MJ;

        try (ProtectionMod claim = new ProtectionMod(target, true)) {
            long accepted = 0;
            for (int i = 0; i < 60; i++) {
                accepted += 64 * MjAPI.MJ - stripes.receivePower(64 * MjAPI.MJ, false);
                stripes.onTick();
            }
            check(level.getBlockState(target).is(Blocks.STONE), "a refused stripes pipe must not break the block");
            check(accepted <= batteryCapacity, "a refused stripes pipe spends no power on the block: it took "
                    + accepted / MjAPI.MJ + " MJ, a full battery is " + batteryCapacity / MjAPI.MJ);
            check(claim.askedBy.size() == 1, "a refused stripes pipe asks once and then backs off, asked "
                    + claim.askedBy.size() + " times");

            // The block changes under the same claim: a new block is a new question, asked at once.
            helper.setBlock(targetRel, Blocks.DIRT);
            stripes.receivePower(64 * MjAPI.MJ, false);
            stripes.onTick();
            check(claim.askedBy.size() == 2, "a changed block is asked about at once, asked "
                    + claim.askedBy.size() + " times");
            check(level.getBlockState(target).is(Blocks.DIRT), "and is still refused");
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

            // Candidacy is cheap and asks nobody; only the block the search settles on is put to the protection mods.
            check(lumberjack.isSearchCandidate(level, log), "scanning a claimed log asks nothing, it is a candidate");
            check(logClaim.askedBy.isEmpty(), "scanning a block must not post a break event for it");
            check(!lumberjack.approveSearchTarget(level, log), "a board refuses a chosen block it may not break");
            check(logClaim.askedBy.size() == 1, "the chosen block is asked about exactly once");
            check(!lumberjack.isSearchCandidate(level, log), "a refused block is remembered and no longer a candidate");
            check(logClaim.askedBy.size() == 1, "and remembering it asked nobody again");
        }
        // The claim is lifted without the log changing - nothing fires an event for that - so the refusal holds until
        // its retry window passes; a log that turns into another block is a new question.
        check(!lumberjack.isSearchCandidate(level, log), "a refusal holds while the block is unchanged");
        level.setBlock(log, Blocks.BIRCH_LOG.defaultBlockState(), 3);
        check(lumberjack.isSearchCandidate(level, log), "an unprotected log is a lumberjack target");
        check(lumberjack.approveSearchTarget(level, log), "and a chosen unprotected log is approved");

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

    /** Registers {@code robot} with the live registry (a reservation by an unregistered robot reads as not taken). */
    private static IRobotRegistry register(EntityRobot robot) {
        IRobotRegistry registry = robot.getRegistry();
        check(registry != null, "the dimension's robot registry must exist");
        robot.setUniqueRobotId(registry.getNextRobotId());
        registry.registerRobot(robot);
        return registry;
    }

    /**
     * Cycles {@code ai} until the registry shows {@code expect} reserved (the search settled), up to a bound. A search
     * with few candidates scans a large cube, ten thousand cells per cycle, hence the generous bound.
     */
    private static void cycleUntilTaken(AIRobot ai, IRobotRegistry registry, BlockPos expect) {
        int cycles = 0;
        while (cycles < 600 && !registry.isTaken(new ResourceIdBlock(expect))) {
            ai.cycle();
            cycles++;
        }
        check(registry.isTaken(new ResourceIdBlock(expect)), "the search settled on " + expect);
    }

    /**
     * A robot board's search asks protection mods about the block it settles on, not about every block it scans: with
     * six logs in reach a lumberjack used to post a break event for each of the five path finders' candidates on
     * every search - spam for every break listener when a work zone sits inside a claim.
     */
    public static void robotSearchAsksOnlyAboutTheChosenTarget(GameTestHelper helper) {
        requireProtectionChecksOn();
        for (BlockPos rel : new BlockPos[] {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1),
                new BlockPos(1, 1, 5), new BlockPos(3, 1, 5), new BlockPos(5, 1, 5)}) {
            helper.setBlock(rel, Blocks.OAK_LOG);
        }
        EntityRobot robot = unaddedRobot(helper, new BlockPos(3, 3, 3));
        robot.setItemInUse(new ItemStack(Items.DIAMOND_AXE));
        IRobotRegistry registry = register(robot);
        BoardRobotLumberjack lumberjack = new BoardRobotLumberjack(robot);

        // Every break event posted while this synchronous loop runs comes from this robot (tests do not interleave),
        // wherever the search ends up - logs in a neighbouring arena are legal targets for it too.
        List<BlockPos> probed = new ArrayList<>();
        Object listener = BreakEventCompat.onBreak(event -> probed.add(event.getPos()));
        try {
            // Settled once the board has handed the found log to the goto leg.
            for (int i = 0; i < 600 && !(lumberjack.getActiveAI() instanceof AIRobotGotoBlock); i++) {
                lumberjack.cycle();
            }
            check(lumberjack.getActiveAI() instanceof AIRobotGotoBlock, "the search settled on a log");
        } finally {
            BreakEventCompat.removeListener(listener);
            lumberjack.abortDelegateAI();
            registry.releaseResources(robot);
            registry.killRobot(robot);
        }
        check(probed.size() == 1, "protection mods are asked about the chosen log only, not about every log the search"
                + " scanned: asked about " + probed);
        helper.succeed();
    }

    /**
     * A log a protection mod refuses is skipped for the next one, and the refusal is remembered: a second search asks
     * about the refused log no more, instead of flying back to it (or probing it) on every search. The searches are
     * built exactly as the board builds its own (the board's candidate filter and its chosen-target approver) and
     * share one board, as consecutive searches of one robot do.
     */
    public static void robotSearchSkipsRefusedTargetsAndRemembersThem(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos refusedRel = new BlockPos(1, 1, 1);
        BlockPos allowedRel = new BlockPos(5, 1, 5);
        helper.setBlock(refusedRel, Blocks.OAK_LOG);
        helper.setBlock(allowedRel, Blocks.OAK_LOG);
        BlockPos refused = helper.absolutePos(refusedRel);
        BlockPos allowed = helper.absolutePos(allowedRel);

        EntityRobot robot = unaddedRobot(helper, new BlockPos(1, 3, 1));
        robot.setItemInUse(new ItemStack(Items.DIAMOND_AXE));
        IRobotRegistry registry = register(robot);
        BoardRobotLumberjack lumberjack = new BoardRobotLumberjack(robot);

        AIRobot search = null;
        try (ProtectionMod claim = new ProtectionMod(refused, true);
                ProtectionMod open = new ProtectionMod(allowed, false)) {
            for (int round = 1; round <= 2; round++) {
                // Pinned to this test's two logs: logs in a neighbouring arena are legal targets for the board too.
                search = new AIRobotSearchAndGotoBlock(robot, false,
                        pos -> (pos.equals(refused) || pos.equals(allowed)) && lumberjack.isSearchCandidate(level, pos),
                        pos -> lumberjack.approveSearchTarget(level, pos));
                cycleUntilTaken(search, registry, allowed);
                check(!registry.isTaken(new ResourceIdBlock(refused)), "the refused log is never reserved");
                check(claim.askedBy.size() == 1, "the refused log is asked about once and then remembered, asked "
                        + claim.askedBy.size() + " times by search " + round);
                check(open.askedBy.size() == round, "the allowed log is asked about on each search, asked "
                        + open.askedBy.size() + " times by search " + round);
                search.abort();
                registry.release(new ResourceIdBlock(allowed));
            }
        } finally {
            if (search != null) {
                search.abort();
            }
            registry.releaseResources(robot);
            registry.killRobot(robot);
        }
        helper.succeed();
    }
}
