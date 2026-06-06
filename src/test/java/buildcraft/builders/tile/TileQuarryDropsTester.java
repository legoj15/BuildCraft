/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.BCBuildersBlocks;

/**
 * Pins drop-routing in {@link TileQuarry.TaskBreakBlock#finish} across both quarry phases:
 * <ul>
 *   <li>frame-volume pre-clearing, where the rig hasn't been built and
 *       {@link TileQuarry#drillPos} is null (set null at TileQuarry.java:559 immediately
 *       before queuing the {@code TaskBreakBlock});</li>
 *   <li>active mining, where the rig is descending and {@code drillPos} carries the
 *       drill's current world position (derived from the mining box).</li>
 * </ul>
 * Both phases must route the broken block's drops through
 * {@link buildcraft.lib.misc.InventoryUtil#addToBestAcceptor} to a neighbour of the
 * quarry. The frame-clearing pin guards against the historical {@code drillPos!=null}
 * guard that silently discarded everything broken before the rig existed; the mining
 * pin keeps the happy path explicitly covered.
 */
public class TileQuarryDropsTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testFrameClearingRoutesDropsToAdjacentChest(GameTestHelper helper) {
        try {
            runScenario(helper, /* drillPosSet = */ false);
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    public static void testMiningRoutesDropsToAdjacentChest(GameTestHelper helper) {
        try {
            runScenario(helper, /* drillPosSet = */ true);
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static void runScenario(GameTestHelper helper, boolean drillPosSet) {
        BlockPos quarryLocal = new BlockPos(2, 2, 2);
        BlockPos chestLocal = new BlockPos(3, 2, 2);
        BlockPos breakLocal = new BlockPos(2, 3, 2);

        helper.setBlock(quarryLocal, BCBuildersBlocks.QUARRY.get());
        helper.setBlock(chestLocal, Blocks.CHEST);
        helper.setBlock(breakLocal, Blocks.STONE);

        //? if >=1.21.10 {
        TileQuarry quarry = helper.getBlockEntity(quarryLocal, TileQuarry.class);
        //?} else {
        /*TileQuarry quarry = helper.getBlockEntity(quarryLocal);*/
        //?}
        assertTrue(quarry != null, "quarry block-entity must be present");

        BlockPos breakAbs = helper.absolutePos(breakLocal);
        // Replicate each phase's drillPos contract: pre-clearing zeroes it
        // (TileQuarry.java:559); mining derives it from miningBox (TileQuarry.java:593).
        // Only the null-ness matters for the routing branch under test.
        quarry.drillPos = drillPosSet ? Vec3.atLowerCornerOf(breakAbs) : null;

        TileQuarry.TaskBreakBlock task = quarry.new TaskBreakBlock(breakAbs);

        // Pumping `target` MJ into addPower drives Task.finish in one call:
        // power >= target trips the finish branch in Task.addPower (TileQuarry.java:1022).
        long target = task.getTarget();
        assertTrue(task.addPower(target),
                "addPower(target) must drive TaskBreakBlock past its threshold and into finish");

        assertTrue(helper.getLevel().getBlockState(breakAbs).isAir(),
                "stone target must be broken after finish (drillPosSet=" + drillPosSet + ")");

        // The chest is the only acceptor adjacent to the quarry: addToRandomInjectable
        // returns the stack unchanged (no pipes), addToRandomInventory finds the chest
        // via Capabilities.Item.BLOCK on the east face.
        ChestBlockEntity chest = (ChestBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(chestLocal));
        assertTrue(chest != null, "chest block-entity must be present");

        boolean foundCobble = false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack slot = chest.getItem(i);
            if (!slot.isEmpty() && slot.is(Items.COBBLESTONE)) {
                foundCobble = true;
                break;
            }
        }
        assertTrue(foundCobble,
                "chest adjacent to the quarry must receive the cobblestone drop "
                        + "(drillPosSet=" + drillPosSet + ")");

        helper.succeed();
    }
}
