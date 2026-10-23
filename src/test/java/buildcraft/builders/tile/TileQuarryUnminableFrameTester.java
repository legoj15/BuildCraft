/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.mj.MjAPI;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.RefusalMemory;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.WorkClaimMod;

/**
 * A cell inside the quarry's frame volume that the quarry may never mine must not stall the quarry.
 *
 * <p>The regression: {@code TileQuarry.check} queued every non-air, non-fluid cell of the frame volume
 * into {@code frameBreakBlockPoses}, and the power loop handled that set's head by starting a break only
 * when {@code canMine} said yes -- then called {@code check} (which re-added the same cell) and
 * {@code continue}d. Bedrock, a claim-protected block or a block logged with a thick fluid is never
 * mineable, so the head was never removed: the loop spun on it every tick and nothing after that branch
 * ran, so the rest of the frame was never placed and the drill never started.
 *
 * <p>Layout (local coordinates, one arena cell, quarry beside the frame box like a placed one):
 * <pre>
 *   quarry (1,2,2)   chest (0,2,2) catches the drops
 *   frame box  (2,2,1)..(5,4,4)      - edge cell (2,2,2) is the unminable one, touching the quarry so it is
 *                                      the nearest cell and therefore the head of the break queue
 *   mining box (3,*,2)..(4,3,3)      - floored with bedrock at y=1 so the drill has nothing to dig
 *   stone: edge cell (3,2,1) and interior cell (4,3,3) - ordinary clearing must still happen around the skip
 * </pre>
 * The tick loop drives {@code TileQuarry.tick()} directly with a full battery each tick (synchronous, no
 * dependence on when the arena's chunks start ticking) until the drill is seeded, which only happens once
 * both frame queues are empty.
 */
public class TileQuarryUnminableFrameTester {

    private static final BlockPos QUARRY = new BlockPos(1, 2, 2);
    private static final BlockPos CHEST = new BlockPos(0, 2, 2);
    private static final BlockPos FRAME_MIN = new BlockPos(2, 2, 1);
    private static final BlockPos FRAME_MAX = new BlockPos(5, 4, 4);
    /** Frame-edge cell next to the quarry: the head of the break queue, and the cell made unminable. */
    private static final BlockPos STUCK_EDGE = new BlockPos(2, 2, 2);
    /** Interior cell (not on the frame edge) that is also unminable in the bedrock scenario. */
    private static final BlockPos STUCK_INTERIOR = new BlockPos(3, 3, 2);
    private static final BlockPos STONE_EDGE = new BlockPos(3, 2, 1);
    private static final BlockPos STONE_INTERIOR = new BlockPos(4, 3, 3);

    /** Real ticks a quarry gets, after a claim is lifted, to re-ask about a parked frame cell: three of its windows
     *  (200 each). A literal, not computed from the window, so {@code GameTestPollBudgetTester} can read it; the
     *  initializer below fails loudly when the window outgrows it. */
    private static final int FRAME_RETRY_TICKS = 600;

    static {
        if (FRAME_RETRY_TICKS < RefusalMemory.QUARRY_FRAME.windowTicks() * 3) {
            throw new IllegalStateException("FRAME_RETRY_TICKS (" + FRAME_RETRY_TICKS + ") must cover three refusal "
                    + "windows (" + RefusalMemory.QUARRY_FRAME.windowTicks() + " each): raise it, and the manifests' "
                    + "max_ticks above it");
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** Bedrock on the frame line (and one in the interior): the frame must finish around it and digging start. */
    public static void testFrameCompletesAroundBedrock(GameTestHelper helper) {
        try {
            Scenario s = new Scenario(helper);
            try {
                helper.setBlock(STUCK_EDGE, Blocks.BEDROCK);
                helper.setBlock(STUCK_INTERIOR, Blocks.BEDROCK);
                s.runToDiggingAndVerify(helper, Blocks.BEDROCK);
                assertTrue(helper.getLevel().getBlockState(s.abs(STUCK_INTERIOR)).is(Blocks.BEDROCK),
                        "the interior bedrock must be left alone");
            } finally {
                s.cleanUp(helper);
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** A claim-protected block on the frame line: the quarry must leave it and go on, not spin asking forever. */
    public static void testFrameCompletesAroundClaimProtectedBlock(GameTestHelper helper) {
        try {
            assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                    "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            Scenario s = new Scenario(helper);
            try {
                helper.setBlock(STUCK_EDGE, Blocks.STONE);
                try (ClaimProtectionMod claim =
                             new ClaimProtectionMod(s.abs(STUCK_EDGE), true)) {
                    s.runToDiggingAndVerify(helper, Blocks.STONE);
                    assertTrue(!claim.askedBy.isEmpty(),
                            "liveness: the quarry must have asked the claim about the cell");
                }
            } finally {
                s.cleanUp(helper);
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * A claim that is lifted after the quarry parked its cell: lifting fires no event, so the quarry must ask again
     * on its own within a bounded time, and the frame gap it left must then fill. The quarry has finished and gone
     * idle by the time the claim lifts, which is the case a re-scan tied to active work could never reach.
     *
     * <p>The retry window runs on game time, so this waits real ticks for it (each one also driving the quarry by
     * hand with a full battery), gated on the gap filling rather than on any fixed tick.
     */
    public static void testFrameCompletesAfterClaimIsLifted(GameTestHelper helper) {
        Scenario s;
        try {
            assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                    "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            s = new Scenario(helper);
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
            return;
        }
        BlockPos stuckAbs;
        try {
            helper.setBlock(STUCK_EDGE, Blocks.STONE);
            try (ClaimProtectionMod claim = new ClaimProtectionMod(s.abs(STUCK_EDGE), true)) {
                s.runToDiggingAndVerify(helper, Blocks.STONE);
                assertTrue(!claim.askedBy.isEmpty(), "liveness: the quarry must have asked the claim about the cell");
            }
            stuckAbs = s.abs(STUCK_EDGE);
        } catch (Throwable t) {
            s.cleanUp(helper);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
            return;
        }
        // The claim is lifted: nothing tells the quarry. It must find out by asking again once the window ends.
        int[] polls = {0};
        EntityArenaUtil.tickUntil(helper, FRAME_RETRY_TICKS, () -> {
            polls[0]++;
            s.quarry.getBattery().setStored(s.quarry.getBattery().getCapacity());
            s.quarry.tick();
            boolean filled = helper.getLevel().getBlockState(stuckAbs).is(BCBuildersBlocks.FRAME.get());
            if (!filled && polls[0] >= FRAME_RETRY_TICKS) {
                s.cleanUp(helper); // the wait is about to fail the test: leave nothing behind
            }
            return filled;
        }, () -> {
            try {
                for (int i = 0; i < FRAME_RETRY_TICKS && s.quarry.hasPendingWork(); i++) {
                    s.quarry.getBattery().setStored(s.quarry.getBattery().getCapacity());
                    s.quarry.tick();
                }
                assertTrue(!s.quarry.hasPendingWork() && !s.quarry.isActive(),
                        "the quarry must settle idle again once the gap is filled");
                s.cleanUp(helper);
                helper.succeed();
            } catch (Throwable t) {
                s.cleanUp(helper);
                helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
            }
        }, "the frame gap was never filled after the claim was lifted");
    }

    /**
     * A parked cell that stops being a block the quarry has to break: the claim still stands (the quarry may not mine
     * what is there), but the block the quarry was refused is gone and the cell is bare. The quarry has finished and
     * gone idle, so its retry is the only probe; it used to re-queue a parked cell only when {@code canMine} was true,
     * and a cell with nothing left to break (air under a standing claim, or bare fluid) is never mineable, so its
     * frame gap stayed until the chunk reloaded. The retry must now check the cell and place the frame.
     *
     * <p>The cell turns to air rather than water: both are "nothing the frame must break", and water would flow
     * through the open frame box for the whole 200-tick window.
     *
     * <p>Everything but building the quarry waits for the first poll: a placed quarry runs {@code onLoad} on its first
     * level tick, which rescans the whole frame box and would repair the gap by itself, hiding the retry. The poll's
     * first call is that tick, so the parking and the wait that follow are the quarry's own.
     */
    public static void testFrameFillsGapWhenParkedCellBecomesBare(GameTestHelper helper) {
        Scenario s;
        try {
            assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                    "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            s = new Scenario(helper);
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
            return;
        }
        BlockPos stuckAbs = s.abs(STUCK_EDGE);
        ClaimProtectionMod[] claim = {null};
        boolean[] parked = {false};
        EntityArenaUtil.tickUntil(helper, FRAME_RETRY_TICKS + 5, () -> {
            if (!parked[0]) {
                parked[0] = true;
                helper.setBlock(STUCK_EDGE, Blocks.STONE);
                claim[0] = new ClaimProtectionMod(stuckAbs, true);
                s.runToDiggingAndVerify(helper, Blocks.STONE);
                assertTrue(!claim[0].askedBy.isEmpty(), "liveness: the quarry must have asked the claim about the cell");
                // The refused block goes away; the claim over the cell stays, so canMine keeps saying no.
                helper.setBlock(STUCK_EDGE, Blocks.AIR);
                return false;
            }
            s.quarry.getBattery().setStored(s.quarry.getBattery().getCapacity());
            s.quarry.tick();
            return helper.getLevel().getBlockState(stuckAbs).is(BCBuildersBlocks.FRAME.get());
        }, helper::succeed, "the frame gap was never filled after the parked cell turned to air under a standing claim",
                () -> {
                    if (claim[0] != null) {
                        claim[0].close();
                    }
                    s.cleanUp(helper);
                });
    }

    /**
     * A frame-line cell a claim will not let the quarry place into: the frame block is placed by a bare
     * {@code setBlockAndUpdate} that no placement event sees, so the quarry must ask first. A refused cell is a gap in
     * the frame, parked like a refused break: the rest of the frame is built, the drill starts, and the quarry settles
     * idle instead of spinning on the cell or paying for a frame block it cannot place. The claim refuses placement
     * only; the cell is bare air, so nothing is ever asked about breaking it.
     */
    public static void testFrameSkipsCellWherePlacementIsRefused(GameTestHelper helper) {
        try {
            assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                    "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            Scenario s = new Scenario(helper);
            try {
                BlockPos stuckAbs = s.abs(STUCK_EDGE);
                try (WorkClaimMod claim = new WorkClaimMod(stuckAbs::equals, stuckAbs::equals)) {
                    s.runToDiggingAndVerify(helper, Blocks.AIR);
                    assertTrue(claim.placeAsks(stuckAbs) >= 1,
                            "liveness: the quarry must have asked the claim before placing the frame block");
                }
            } finally {
                s.cleanUp(helper);
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The claim over that frame-line cell is lifted after the quarry parked it and went idle. Lifting fires no event,
     * so the quarry must ask for the placement again on its own once the cell's window ends, and the gap must fill.
     */
    public static void testFrameFillsGapAfterPlacementClaimIsLifted(GameTestHelper helper) {
        fillsGapAfterPlacementIsAllowed(helper, false);
    }

    /**
     * The same, but only the claim's placement rule is lifted: its break rule still stands over the cell (a claim that
     * lets its players build and not break). The cell is air throughout, so the retry cannot tell the lift by the cell
     * changing, and {@code canMine} on it stays false: the window ending is all the retry has, and it must ask the
     * placement again rather than read the cell, whose window {@code takeExpired} has just restarted, as still
     * refused. (A bare fluid cell takes this same path, since {@code canMine} never accepts a fluid.)
     */
    public static void testFrameFillsGapAfterPlacementLiftedUnderBreakClaim(GameTestHelper helper) {
        fillsGapAfterPlacementIsAllowed(helper, true);
    }

    /**
     * Everything but building the quarry waits for the first poll, for the reason
     * {@link #testFrameFillsGapWhenParkedCellBecomesBare} gives: the quarry's first level tick rescans the frame box
     * and would fill the gap by itself, hiding the retry.
     *
     * @param breakClaimStays whether a claim refusing breaks (and only breaks) stays over the cell all the way
     */
    private static void fillsGapAfterPlacementIsAllowed(GameTestHelper helper, boolean breakClaimStays) {
        Scenario s;
        try {
            assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                    "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            s = new Scenario(helper);
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
            return;
        }
        BlockPos stuckAbs = s.abs(STUCK_EDGE);
        WorkClaimMod[] placeClaim = {null};
        ClaimProtectionMod[] breakClaim = {null};
        boolean[] parked = {false};
        EntityArenaUtil.tickUntil(helper, FRAME_RETRY_TICKS + 5, () -> {
            if (!parked[0]) {
                parked[0] = true;
                placeClaim[0] = new WorkClaimMod(stuckAbs::equals, stuckAbs::equals);
                if (breakClaimStays) {
                    breakClaim[0] = new ClaimProtectionMod(stuckAbs, true);
                }
                s.runToDiggingAndVerify(helper, Blocks.AIR);
                assertTrue(placeClaim[0].placeAsks(stuckAbs) >= 1,
                        "liveness: the quarry must have asked the claim before placing the frame block");
                // The placement is allowed again: nothing tells the quarry. It must find out by asking once the
                // window ends.
                placeClaim[0].close();
                placeClaim[0] = null;
                return false;
            }
            s.quarry.getBattery().setStored(s.quarry.getBattery().getCapacity());
            s.quarry.tick();
            return helper.getLevel().getBlockState(stuckAbs).is(BCBuildersBlocks.FRAME.get());
        }, () -> {
            try {
                for (int i = 0; i < FRAME_RETRY_TICKS && s.quarry.hasPendingWork(); i++) {
                    s.quarry.getBattery().setStored(s.quarry.getBattery().getCapacity());
                    s.quarry.tick();
                }
                assertTrue(!s.quarry.hasPendingWork() && !s.quarry.isActive(),
                        "the quarry must settle idle again once the gap is filled");
                helper.succeed();
            } catch (Throwable t) {
                helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
            }
        }, "the frame gap was never filled after the placement claim was lifted", () -> {
            if (placeClaim[0] != null) {
                placeClaim[0].close();
            }
            if (breakClaim[0] != null) {
                breakClaim[0].close();
            }
            s.cleanUp(helper);
        });
    }

    /**
     * A claim that appears over a frame-line cell while the quarry is partway through making its frame block: the ask
     * made before the task started was answered yes, so the one made as the block goes in is the only one that can
     * stop it. The power spent is given back, the cell stays bare, and the quarry carries on around it.
     *
     * <p>The quarry is fed a trickle (the frame block costs 24 MJ) so the task is in flight across several ticks, and
     * the cell is whichever one the quarry started on. The stone the scenario puts in the frame box is cleared first:
     * a trickle would take far too long to break it, and none of this is about breaking.
     */
    public static void testFrameCellClaimedMidTaskIsNotFramed(GameTestHelper helper) {
        try {
            assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                    "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            Scenario s = new Scenario(helper);
            try {
                helper.setBlock(STONE_EDGE, Blocks.AIR);
                helper.setBlock(STONE_INTERIOR, Blocks.AIR);
                for (int i = 0; i < 400 && !(s.quarry.currentTask instanceof TileQuarry.TaskAddFrame); i++) {
                    s.quarry.getBattery().setStored(MjAPI.MJ);
                    s.quarry.tick();
                }
                assertTrue(s.quarry.currentTask instanceof TileQuarry.TaskAddFrame,
                        "the quarry never started a frame block: currentTask is " + s.quarry.currentTask);
                TileQuarry.TaskAddFrame task = (TileQuarry.TaskAddFrame) s.quarry.currentTask;
                BlockPos claimedAbs = task.framePos;

                try (WorkClaimMod claim = new WorkClaimMod(claimedAbs::equals, claimedAbs::equals)) {
                    for (int i = 0; i < 400 && s.quarry.currentTask == task; i++) {
                        s.quarry.getBattery().setStored(MjAPI.MJ);
                        s.quarry.tick();
                    }
                    assertTrue(s.quarry.currentTask != task, "the frame task never finished");
                    BlockState cell = helper.getLevel().getBlockState(claimedAbs);
                    assertTrue(!cell.is(BCBuildersBlocks.FRAME.get()),
                            "the quarry placed a frame block in a cell a claim refused while it was being made");
                    assertTrue(claim.placeAsks(claimedAbs) >= 1,
                            "the quarry must ask the claim again as the frame block goes in");

                    // Around the refused cell the frame still completes and the quarry settles.
                    s.runToDigging();
                    s.verifyFrameAround(helper, claimedAbs, cell.getBlock());
                    s.assertSettlesIdle();
                }
            } finally {
                s.cleanUp(helper);
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static final class Scenario {
        final TileQuarry quarry;
        final BlockPos quarryAbs;

        Scenario(GameTestHelper helper) {
            helper.setBlock(QUARRY, BCBuildersBlocks.QUARRY.get());
            helper.setBlock(CHEST, Blocks.CHEST);
            helper.setBlock(STONE_EDGE, Blocks.STONE);
            helper.setBlock(STONE_INTERIOR, Blocks.STONE);
            // Bedrock floor under the mining columns: nothing for the drill to mine or descend past.
            for (int x = 3; x <= 4; x++) {
                for (int z = 2; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.BEDROCK);
                }
            }

            //? if >=1.21.10 {
            quarry = helper.getBlockEntity(QUARRY, TileQuarry.class);
            //?} else {
            /*quarry = helper.getBlockEntity(QUARRY);*/
            //?}
            assertTrue(quarry != null, "quarry block-entity must be present");
            quarryAbs = helper.absolutePos(QUARRY);

            BlockPos min = helper.absolutePos(FRAME_MIN);
            BlockPos max = helper.absolutePos(FRAME_MAX);
            quarry.frameBox.reset();
            quarry.frameBox.setMin(min);
            quarry.frameBox.setMax(max);
            quarry.miningBox.reset();
            // Same shape TileQuarry.onPlacedBy builds; tick() re-floors Y from the configured depth.
            quarry.miningBox.setMin(new BlockPos(min.getX() + 1, quarryAbs.getY() - 1, min.getZ() + 1));
            quarry.miningBox.setMax(new BlockPos(max.getX() - 1, max.getY() - 1, max.getZ() - 1));
            quarry.updatePoses();
        }

        BlockPos abs(BlockPos local) {
            return quarry.getBlockPos().offset(local.subtract(QUARRY));
        }

        /**
         * Ticks the quarry until its drill is seeded (the digging phase, reached only when both frame queues
         * are empty) and verifies the frame around the one unminable cell.
         *
         * @param stuckBlock the block the unminable edge cell must still hold afterwards
         */
        void runToDiggingAndVerify(GameTestHelper helper, Block stuckBlock) {
            runToDigging();
            verifyFrameAround(helper, abs(STUCK_EDGE), stuckBlock);
            assertInteriorClearedAndRouted(helper);
            assertSettlesIdle();
        }

        /** Ticks the quarry with a full battery until its drill is seeded, or fails if it never is. */
        void runToDigging() {
            for (int i = 0; i < 4000 && quarry.drillPos == null; i++) {
                quarry.getBattery().setStored(quarry.getBattery().getCapacity());
                quarry.tick();
            }
            assertTrue(quarry.drillPos != null,
                    "the quarry never reached the digging phase: it is stuck on the unminable frame cell"
                            + " (hasPendingWork=" + quarry.hasPendingWork() + ")");
        }

        /** Every frame-line cell but {@code stuckAbs} holds a frame block; that one still holds {@code stuckBlock}. */
        void verifyFrameAround(GameTestHelper helper, BlockPos stuckAbs, Block stuckBlock) {
            BlockPos min = quarry.frameBox.min();
            BlockPos max = quarry.frameBox.max();
            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                BlockState state = helper.getLevel().getBlockState(p);
                if (!quarry.frameBox.isOnEdge(p)) {
                    continue;
                }
                if (p.equals(stuckAbs)) {
                    assertTrue(state.is(stuckBlock),
                            "the frame-line cell the quarry may not touch must be left alone, got " + state);
                } else {
                    assertTrue(state.is(BCBuildersBlocks.FRAME.get()),
                            "every other frame-line cell must hold a frame block, but " + p.subtract(quarryAbs)
                                    + " (relative to the quarry) holds " + state);
                }
            }
        }

        private void assertInteriorClearedAndRouted(GameTestHelper helper) {
            assertTrue(helper.getLevel().getBlockState(abs(STONE_INTERIOR)).isAir(),
                    "ordinary interior clearing must still happen around the skipped cell");

            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(abs(CHEST));
            assertTrue(chest != null, "chest block-entity must be present");
            boolean foundCobble = false;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(Items.COBBLESTONE)) {
                    foundCobble = true;
                    break;
                }
            }
            assertTrue(foundCobble, "the cleared stone must have been routed into the chest beside the quarry");
        }

        /** One more pass: with the frame done and nothing mineable below, the quarry must settle idle
         *  (no pending work, inactive) instead of staying busy on the skipped cell forever. */
        void assertSettlesIdle() {
            quarry.getBattery().setStored(quarry.getBattery().getCapacity());
            quarry.tick();
            assertTrue(!quarry.hasPendingWork(),
                    "a quarry with a finished frame and nothing to mine must have no pending work");
            assertTrue(!quarry.isActive(), "a finished quarry must go inactive so it stops scanning and loading chunks");
        }

        /** Drops the rig entities and the force-load tickets so nothing leaks into neighbouring arenas. */
        void cleanUp(GameTestHelper helper) {
            quarry.currentTask = null;
            quarry.drillPos = null;
            quarry.updateRigs();
            helper.setBlock(QUARRY, Blocks.AIR);
        }
    }
}
