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

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.MachineProtectionTester;

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
                try (MachineProtectionTester.ProtectionMod claim =
                             new MachineProtectionTester.ProtectionMod(s.abs(STUCK_EDGE), true)) {
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
            for (int i = 0; i < 4000 && quarry.drillPos == null; i++) {
                quarry.getBattery().setStored(quarry.getBattery().getCapacity());
                quarry.tick();
            }
            assertTrue(quarry.drillPos != null,
                    "the quarry never reached the digging phase: it is stuck on the unminable frame cell"
                            + " (hasPendingWork=" + quarry.hasPendingWork() + ")");

            BlockPos stuckAbs = abs(STUCK_EDGE);
            BlockPos min = quarry.frameBox.min();
            BlockPos max = quarry.frameBox.max();
            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                BlockState state = helper.getLevel().getBlockState(p);
                if (!quarry.frameBox.isOnEdge(p)) {
                    continue;
                }
                if (p.equals(stuckAbs)) {
                    assertTrue(state.is(stuckBlock),
                            "the unminable frame-line cell must be left untouched, got " + state);
                } else {
                    assertTrue(state.is(BCBuildersBlocks.FRAME.get()),
                            "every other frame-line cell must hold a frame block, but " + p.subtract(quarryAbs)
                                    + " (relative to the quarry) holds " + state);
                }
            }
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

            // One more pass: with the frame done and nothing mineable below, the quarry must settle idle
            // (no pending work, inactive) instead of staying busy on the skipped cell forever.
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
