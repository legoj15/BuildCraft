/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileTank;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.tile.TileFilteredBuffer;

/**
 * End-to-end "right-tool / wrong-tool" drop tests for the BuildCraft block roster.
 * <p>
 * Each test below stands in for a category of blocks rather than asserting every block
 * one-by-one:
 * <ul>
 *   <li><b>Decorated block</b> — no-inventory baseline that exercises just the loot
 *       table + {@code requiresCorrectToolForDrops} pair.</li>
 *   <li><b>Chute</b> — itemManager-backed real inventory, the path most machines use.</li>
 *   <li><b>Autoworkbench</b> — itemManager with a mix of real and PHANTOM slots; verifies
 *       template/filter items are <em>not</em> duplicated on break.</li>
 *   <li><b>Tank</b> — fluid-only contents that drop as fragile fluid-shard items.</li>
 *   <li><b>Stirling engine</b> — loose ItemStack fuel slot held outside ItemHandlerManager,
 *       reached via the {@code dropStack} helper.</li>
 *   <li><b>Filtered buffer</b> — itemManager with a PHANTOM filter inventory; the filter
 *       template must <em>not</em> drop alongside the real stored items.</li>
 *   <li><b>Path marker</b> — hand-breakable (Material.CIRCUITS in 1.12.2); empty-hand
 *       break must still drop the block.</li>
 * </ul>
 * Each tool-gated block has two tests: one with a wooden pickaxe (drops block + contents)
 * and one with an empty hand (drops contents only, not the block itself).
 */
public class BlockDropsTester {

    // ---------- Shared helpers ----------

    /** Plays the full destroy chain the way ServerPlayerGameMode does:
     *  playerWillDestroy → removeBlock → playerDestroy (only if canHarvestBlock).
     *  {@code helper.destroyBlock} short-circuits this path and skips loot, so the tests
     *  would silently pass even if the tool gate were misconfigured. The {@code canHarvest}
     *  gate is what makes the loot-table-side block-self drop conditional on the tool —
     *  without it the loot table fires unconditionally and the empty-hand test fails. */
    private static void breakAs(GameTestHelper helper, BlockPos relativePos, Player player) {
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(relativePos);
        BlockState state = level.getBlockState(absPos);
        BlockEntity be = level.getBlockEntity(absPos);
        boolean canHarvest = state.canHarvestBlock(level, absPos, player);
        state.getBlock().playerWillDestroy(level, absPos, state, player);
        level.removeBlock(absPos, false);
        if (canHarvest) {
            state.getBlock().playerDestroy(level, player, absPos, state, be, player.getMainHandItem());
        }
    }

    private static Player survivalPlayerWith(GameTestHelper helper, ItemStack heldItem) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, heldItem);
        return player;
    }

    // ---------- Decorated block (no inventory) ----------

    public static void testDecoratedPickaxeDropsSelf(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCCoreBlocks.DECORATED_DESTROY.get());

        breakAs(helper, pos, survivalPlayerWith(helper, new ItemStack(Items.WOODEN_PICKAXE)));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCCoreBlocks.DECORATED_DESTROY.get().asItem(), pos, 2.0);
            helper.succeed();
        });
    }

    public static void testDecoratedHandBreakDropsNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCCoreBlocks.DECORATED_DESTROY.get());

        breakAs(helper, pos, survivalPlayerWith(helper, ItemStack.EMPTY));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityNotPresent(BCCoreBlocks.DECORATED_DESTROY.get().asItem(), pos, 2.0);
            helper.succeed();
        });
    }

    // ---------- Chute (itemManager, real inventory) ----------

    public static void testChutePickaxeDropsContentsAndSelf(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCFactoryBlocks.CHUTE.get());

        TileChute chute = helper.getBlockEntity(pos, TileChute.class);
        chute.inv.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
        chute.inv.setStackInSlot(3, new ItemStack(Items.GOLD_INGOT, 11));

        breakAs(helper, pos, survivalPlayerWith(helper, new ItemStack(Items.WOODEN_PICKAXE)));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCFactoryBlocks.CHUTE.get().asItem(), pos, 2.0);
            helper.assertItemEntityPresent(Items.DIAMOND, pos, 2.0);
            helper.assertItemEntityPresent(Items.GOLD_INGOT, pos, 2.0);
            helper.succeed();
        });
    }

    public static void testChuteHandBreakDropsContentsOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCFactoryBlocks.CHUTE.get());

        TileChute chute = helper.getBlockEntity(pos, TileChute.class);
        chute.inv.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));

        breakAs(helper, pos, survivalPlayerWith(helper, ItemStack.EMPTY));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            // Contents drop unconditionally — losing the player's items to an accidental
            // hand-break would feel like a regression even though the block itself shouldn't.
            helper.assertItemEntityPresent(Items.DIAMOND, pos, 2.0);
            // Block itself must NOT drop without a pickaxe.
            helper.assertItemEntityNotPresent(BCFactoryBlocks.CHUTE.get().asItem(), pos, 2.0);
            helper.succeed();
        });
    }

    // ---------- Autoworkbench: PHANTOM slots must NOT drop ----------

    public static void testAutoWorkbenchSkipsPhantomSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());

        TileAutoWorkbenchItems workbench = helper.getBlockEntity(pos, TileAutoWorkbenchItems.class);

        // PHANTOM template slots — these never held real items in 1.12.2 and must not
        // duplicate on break. Use rare items so the assertion below can detect a leak
        // unambiguously.
        workbench.invBlueprint.setStackInSlot(0, new ItemStack(Items.NETHER_STAR, 1));
        workbench.invMaterialFilter.setStackInSlot(0, new ItemStack(Items.DRAGON_EGG, 1));

        // REAL inventories — must drop. Use distinct items so the positive case is
        // clearly attributable to the right slot.
        workbench.invMaterials.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 6));
        workbench.invResult.setStackInSlot(0, new ItemStack(Items.CRAFTING_TABLE, 1));

        breakAs(helper, pos, survivalPlayerWith(helper, new ItemStack(Items.WOODEN_PICKAXE)));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            // Real inventories + block itself drop.
            helper.assertItemEntityPresent(BCFactoryBlocks.AUTOWORKBENCH_ITEM.get().asItem(), pos, 2.0);
            helper.assertItemEntityPresent(Items.OAK_LOG, pos, 2.0);
            helper.assertItemEntityPresent(Items.CRAFTING_TABLE, pos, 2.0);
            // PHANTOM slots stay phantom.
            helper.assertItemEntityNotPresent(Items.NETHER_STAR, pos, 2.0);
            helper.assertItemEntityNotPresent(Items.DRAGON_EGG, pos, 2.0);
            helper.succeed();
        });
    }

    // ---------- Tank (fluid contents drop as shards) ----------

    public static void testTankPickaxeDropsFluidShardAndSelf(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCFactoryBlocks.TANK.get());

        TileTank tank = helper.getBlockEntity(pos, TileTank.class);
        fillTank(tank, new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));

        breakAs(helper, pos, survivalPlayerWith(helper, new ItemStack(Items.WOODEN_PICKAXE)));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCFactoryBlocks.TANK.get().asItem(), pos, 2.0);
            helper.assertItemEntityPresent(BCCoreItems.FRAGILE_FLUID_CONTAINER.get(), pos, 2.0);
            helper.succeed();
        });
    }

    public static void testTankHandBreakDropsFluidShardOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCFactoryBlocks.TANK.get());

        TileTank tank = helper.getBlockEntity(pos, TileTank.class);
        fillTank(tank, new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));

        breakAs(helper, pos, survivalPlayerWith(helper, ItemStack.EMPTY));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCCoreItems.FRAGILE_FLUID_CONTAINER.get(), pos, 2.0);
            helper.assertItemEntityNotPresent(BCFactoryBlocks.TANK.get().asItem(), pos, 2.0);
            helper.succeed();
        });
    }

    private static void fillTank(TileTank tank, FluidStack stack) {
        try (Transaction tx = Transaction.open(null)) {
            tank.tank.insert(0, FluidResource.of(stack), stack.getAmount(), tx);
            tx.commit();
        }
    }

    // ---------- Stirling engine (raw fuel slot) ----------

    public static void testStirlingEngineHandBreakDropsFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCEnergyBlocks.ENGINE_STONE.get());

        TileEngineStone_BC8 engine = helper.getBlockEntity(pos, TileEngineStone_BC8.class);
        engine.setFuelStack(new ItemStack(Items.COAL, 17));

        breakAs(helper, pos, survivalPlayerWith(helper, ItemStack.EMPTY));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(Items.COAL, pos, 2.0);
            helper.assertItemEntityNotPresent(BCEnergyBlocks.ENGINE_STONE.get().asItem(), pos, 2.0);
            helper.succeed();
        });
    }

    public static void testStirlingEnginePickaxeDropsFuelAndSelf(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCEnergyBlocks.ENGINE_STONE.get());

        TileEngineStone_BC8 engine = helper.getBlockEntity(pos, TileEngineStone_BC8.class);
        engine.setFuelStack(new ItemStack(Items.COAL, 17));

        breakAs(helper, pos, survivalPlayerWith(helper, new ItemStack(Items.WOODEN_PICKAXE)));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(Items.COAL, pos, 2.0);
            helper.assertItemEntityPresent(BCEnergyBlocks.ENGINE_STONE.get().asItem(), pos, 2.0);
            helper.succeed();
        });
    }

    // ---------- Filtered Buffer: PHANTOM filter must NOT drop ----------

    public static void testFilteredBufferSkipsFilterSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCTransportBlocks.FILTERED_BUFFER.get());

        TileFilteredBuffer buffer = helper.getBlockEntity(pos, TileFilteredBuffer.class);

        // PHANTOM filter slots — these record what the buffer accepts, they were never
        // consumed. Use a distinctive item so a leak is visible.
        buffer.invFilter.setStackInSlot(0, new ItemStack(Items.NETHER_STAR, 1));
        buffer.invFilter.setStackInSlot(4, new ItemStack(Items.DRAGON_EGG, 1));

        // Real stored items.
        buffer.invMain.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
        buffer.invMain.setStackInSlot(4, new ItemStack(Items.IRON_INGOT, 12));

        breakAs(helper, pos, survivalPlayerWith(helper, new ItemStack(Items.WOODEN_PICKAXE)));
        helper.assertBlockPresent(Blocks.AIR, pos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCTransportBlocks.FILTERED_BUFFER.get().asItem(), pos, 2.0);
            helper.assertItemEntityPresent(Items.DIAMOND, pos, 2.0);
            helper.assertItemEntityPresent(Items.IRON_INGOT, pos, 2.0);
            helper.assertItemEntityNotPresent(Items.NETHER_STAR, pos, 2.0);
            helper.assertItemEntityNotPresent(Items.DRAGON_EGG, pos, 2.0);
            helper.succeed();
        });
    }

    // ---------- Marker: hand-breakable (no pickaxe gate) ----------

    public static void testMarkerHandBreakDropsSelf(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos markerPos = new BlockPos(1, 2, 1);
        // Marker needs a solid floor for canSurvive to hold.
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(markerPos, BCCoreBlocks.MARKER_PATH.get());

        breakAs(helper, markerPos, survivalPlayerWith(helper, ItemStack.EMPTY));
        helper.assertBlockPresent(Blocks.AIR, markerPos);

        helper.runAfterDelay(10, () -> {
            // Markers were Material.CIRCUITS in 1.12.2 — hand-break returns the marker
            // item without needing a tool.
            helper.assertItemEntityPresent(BCCoreBlocks.MARKER_PATH.get().asItem(), markerPos, 2.0);
            helper.succeed();
        });
    }
}
