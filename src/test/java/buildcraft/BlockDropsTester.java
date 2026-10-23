/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?}

import buildcraft.api.items.FluidItemDrops;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.factory.tile.TilePump;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.test.DropArenaUtil;
import buildcraft.lib.test.DropArenaUtil.Drops;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.tile.TileFilteredBuffer;

/**
 * End-to-end "right-tool / wrong-tool" drop tests for the BuildCraft block roster.
 * <p>
 * Each test below stands in for a category of blocks rather than asserting every block
 * one-by-one:
 * <ul>
 *   <li><b>Decorated Laser block</b> — no-inventory baseline that exercises just the
 *       loot table + {@code requiresCorrectToolForDrops} pair. The laser variant is the
 *       only decorated block in public builds; the other five are gated behind
 *       {@code -Dbuildcraft.dev=true} and would resolve to {@code null} in this test.</li>
 *   <li><b>Chute</b> — itemManager-backed real inventory, the path most machines use.</li>
 *   <li><b>Autoworkbench</b> — itemManager with a mix of real and PHANTOM slots; verifies
 *       template/filter items are <em>not</em> duplicated on break.</li>
 *   <li><b>Tank</b> — fluid-only contents that drop as fragile fluid-shard items.</li>
 *   <li><b>Stirling engine</b> — loose ItemStack fuel slot held outside ItemHandlerManager,
 *       reached via the {@code dropStack} helper. The two Stirling tests also break the FE engine (raw
 *       upgrade slots), the combustion engine (fluid tanks) and the MJ Dynamo (raw upgrade slots) beside it and
 *       require every one's contents to arrive <em>exactly once</em>: the engine blocks have no player-break drop
 *       of their own, a player break reaches the shared engine removal hook like an explosion does.</li>
 *   <li><b>Filtered buffer</b> — itemManager with a PHANTOM filter inventory; the filter
 *       template must <em>not</em> drop alongside the real stored items.</li>
 *   <li><b>Path marker</b> — hand-breakable (Material.CIRCUITS in 1.12.2); empty-hand
 *       break must still drop the block.</li>
 * </ul>
 * Each tool-gated block has two tests: one with a wooden pickaxe (drops block + contents)
 * and one with an empty hand (drops contents only, not the block itself).
 * <p>
 * Every test goes through {@link DropArenaUtil#awaitDrops}: the place-fill-break runs once the arena cell is
 * entity-ticking, and the assertions run after the expected drops have appeared and settled, never on a fixed tick.
 */
public class BlockDropsTester {

    // ---------- Decorated block (no inventory) ----------

    public static void testDecoratedPickaxeDropsSelf(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCCoreBlocks.DECORATED_LASER.get());
            DropArenaUtil.breakBlock(helper, pos, new ItemStack(Items.WOODEN_PICKAXE));
        }, Drops.near(pos, 2.0).present(BCCoreBlocks.DECORATED_LASER.get().asItem()));
    }

    public static void testDecoratedHandBreakDropsNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCCoreBlocks.DECORATED_LASER.get());
            DropArenaUtil.breakBlock(helper, pos, ItemStack.EMPTY);
        }, Drops.near(pos, 2.0).absent(BCCoreBlocks.DECORATED_LASER.get().asItem()));
    }

    // ---------- Chute (itemManager, real inventory) ----------

    public static void testChutePickaxeDropsContentsAndSelf(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCFactoryBlocks.CHUTE.get());

            //? if >=1.21.10 {
            TileChute chute = helper.getBlockEntity(pos, TileChute.class);
            //?} else {
            /*TileChute chute = helper.getBlockEntity(pos);*/
            //?}
            chute.inv.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
            chute.inv.setStackInSlot(3, new ItemStack(Items.GOLD_INGOT, 11));

            DropArenaUtil.breakBlock(helper, pos, new ItemStack(Items.WOODEN_PICKAXE));
        }, Drops.near(pos, 2.0).present(BCFactoryBlocks.CHUTE.get().asItem(), Items.DIAMOND, Items.GOLD_INGOT));
    }

    public static void testChuteHandBreakDropsContentsOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCFactoryBlocks.CHUTE.get());

            //? if >=1.21.10 {
            TileChute chute = helper.getBlockEntity(pos, TileChute.class);
            //?} else {
            /*TileChute chute = helper.getBlockEntity(pos);*/
            //?}
            chute.inv.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));

            DropArenaUtil.breakBlock(helper, pos, ItemStack.EMPTY);
        }, Drops.near(pos, 2.0)
                // Contents drop unconditionally — losing the player's items to an accidental
                // hand-break would feel like a regression even though the block itself shouldn't.
                .present(Items.DIAMOND)
                // Block itself must NOT drop without a pickaxe.
                .absent(BCFactoryBlocks.CHUTE.get().asItem()));
    }

    // ---------- Autoworkbench: PHANTOM slots must NOT drop ----------

    public static void testAutoWorkbenchSkipsPhantomSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());

            //? if >=1.21.10 {
            TileAutoWorkbenchItems workbench = helper.getBlockEntity(pos, TileAutoWorkbenchItems.class);
            //?} else {
            /*TileAutoWorkbenchItems workbench = helper.getBlockEntity(pos);*/
            //?}

            // PHANTOM template slots — these never held real items in 1.12.2 and must not
            // duplicate on break. Use rare items so the assertion below can detect a leak
            // unambiguously.
            workbench.invBlueprint.setStackInSlot(0, new ItemStack(Items.NETHER_STAR, 1));
            workbench.invMaterialFilter.setStackInSlot(0, new ItemStack(Items.DRAGON_EGG, 1));

            // REAL inventories — must drop. Use distinct items so the positive case is
            // clearly attributable to the right slot.
            workbench.invMaterials.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 6));
            workbench.invResult.setStackInSlot(0, new ItemStack(Items.CRAFTING_TABLE, 1));

            DropArenaUtil.breakBlock(helper, pos, new ItemStack(Items.WOODEN_PICKAXE));
        }, Drops.near(pos, 2.0)
                // Real inventories + block itself drop.
                .present(BCFactoryBlocks.AUTOWORKBENCH_ITEM.get().asItem(), Items.OAK_LOG, Items.CRAFTING_TABLE)
                // PHANTOM slots stay phantom.
                .absent(Items.NETHER_STAR, Items.DRAGON_EGG));
    }

    // ---------- Tank (fluid contents drop as shards) ----------

    public static void testTankPickaxeDropsFluidShardAndSelf(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            placeFilledTank(helper, pos);
            DropArenaUtil.breakBlock(helper, pos, new ItemStack(Items.WOODEN_PICKAXE));
        }, Drops.near(pos, 2.0)
                .present(BCFactoryBlocks.TANK.get().asItem(), BCCoreItems.FRAGILE_FLUID_CONTAINER.get()));
    }

    public static void testTankHandBreakDropsFluidShardOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            placeFilledTank(helper, pos);
            DropArenaUtil.breakBlock(helper, pos, ItemStack.EMPTY);
        }, Drops.near(pos, 2.0)
                .present(BCCoreItems.FRAGILE_FLUID_CONTAINER.get())
                .absent(BCFactoryBlocks.TANK.get().asItem()));
    }

    /** A tank holding a bucket of water. */
    private static void placeFilledTank(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BCFactoryBlocks.TANK.get());

        //? if >=1.21.10 {
        TileTank tank = helper.getBlockEntity(pos, TileTank.class);
        //?} else {
        /*TileTank tank = helper.getBlockEntity(pos);*/
        //?}
        fillTank(tank, new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
    }

    private static void fillTank(TileTank tank, FluidStack stack) {
        fillBcTank(tank.tank, stack);
    }

    private static void fillBcTank(BCFluidTank tank, FluidStack stack) {
        //? if >=1.21.10 {
        try (Transaction tx = Transaction.open(null)) {
            tank.insert(0, FluidResource.of(stack), stack.getAmount(), tx);
            tx.commit();
        }
        //?} else {
        /*// 1.21.1 has no Transfer API; BCFluidTank exposes a version-neutral fill(slot, stack, simulate).
        tank.fill(0, stack, false);*/
        //?}
    }

    // ---------- Non-player removal (explosion / piston / mod tool) spills contents ----------
    // A bare level.removeBlock stands in for an explosion / piston / mod setBlock. It routes through
    // TileBC_Neptune.preRemoveSideEffects (>=1.21.10) or the block's onRemove (1.21.1) → the central
    // dropContentsOnRemoval, with no playerWillDestroy involved. The block item itself is gone (it
    // only returns via the loot table on a tool harvest), but the stored contents must still spill —
    // matching 1.12.2's universal breakBlock→onRemove→addDrops. It is NOT a stand-in for a replace-mode
    // /setblock, /fill or /clone: those wipe the old tile first (Clearable) or skip the side effects
    // entirely, so they spill nothing (CommandReplaceTester).

    public static void testChuteNonPlayerRemovalDropsContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCFactoryBlocks.CHUTE.get());

            //? if >=1.21.10 {
            TileChute chute = helper.getBlockEntity(pos, TileChute.class);
            //?} else {
            /*TileChute chute = helper.getBlockEntity(pos);*/
            //?}
            chute.inv.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));

            DropArenaUtil.removeBlock(helper, pos);
        }, Drops.near(pos, 2.0)
                .present(Items.DIAMOND)
                // The chute block itself is destroyed (no tool harvest → loot table doesn't fire).
                .absent(BCFactoryBlocks.CHUTE.get().asItem()));
    }

    public static void testPumpNonPlayerRemovalDropsFluid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCFactoryBlocks.PUMP.get());

            //? if >=1.21.10 {
            TilePump pump = helper.getBlockEntity(pos, TilePump.class);
            //?} else {
            /*TilePump pump = helper.getBlockEntity(pos);*/
            //?}
            // Pump spills its tank via the central getDropTanks() override.
            fillBcTank(pump.getTank(), new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));

            DropArenaUtil.removeBlock(helper, pos);
        }, Drops.near(pos, 2.0)
                .present(BCCoreItems.FRAGILE_FLUID_CONTAINER.get())
                .absent(BCFactoryBlocks.PUMP.get().asItem()));
    }

    public static void testFloodGateNonPlayerRemovalDropsFluid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCFactoryBlocks.FLOOD_GATE.get());

            //? if >=1.21.10 {
            TileFloodGate gate = helper.getBlockEntity(pos, TileFloodGate.class);
            //?} else {
            /*TileFloodGate gate = helper.getBlockEntity(pos);*/
            //?}
            // Flood Gate is a standalone BlockEntity; it carries its own preRemoveSideEffects / onRemove.
            fillBcTank(gate.getTank(), new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));

            DropArenaUtil.removeBlock(helper, pos);
        }, Drops.near(pos, 2.0)
                .present(BCCoreItems.FRAGILE_FLUID_CONTAINER.get())
                .absent(BCFactoryBlocks.FLOOD_GATE.get().asItem()));
    }

    // ---------- Distiller: the bucket / bottle slots drop too ----------

    public static void testDistillerHandBreakDropsContainerSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCFactoryBlocks.DISTILLER.get());

            //? if >=1.21.10 {
            TileDistiller_BC8 distiller = helper.getBlockEntity(pos, TileDistiller_BC8.class);
            //?} else {
            /*TileDistiller_BC8 distiller = helper.getBlockEntity(pos);*/
            //?}
            // The three container slots hold the buckets / bottles the player parked there; only the fluids
            // used to drop, so breaking the machine silently destroyed them.
            distiller.containerSlots.setStackInSlot(0, new ItemStack(Items.BUCKET));
            distiller.containerSlots.setStackInSlot(2, new ItemStack(Items.GLASS_BOTTLE));
            distiller.getTankIn().setFluidStack(0, new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));

            DropArenaUtil.breakBlock(helper, pos, ItemStack.EMPTY);
        }, Drops.near(pos, 2.0)
                .present(Items.BUCKET, Items.GLASS_BOTTLE, BCCoreItems.FRAGILE_FLUID_CONTAINER.get()));
    }

    public static void testDistillerNonPlayerRemovalDropsContainerSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCFactoryBlocks.DISTILLER.get());

            //? if >=1.21.10 {
            TileDistiller_BC8 distiller = helper.getBlockEntity(pos, TileDistiller_BC8.class);
            //?} else {
            /*TileDistiller_BC8 distiller = helper.getBlockEntity(pos);*/
            //?}
            distiller.containerSlots.setStackInSlot(1, new ItemStack(Items.BUCKET));
            distiller.getTankIn().setFluidStack(0, new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));

            DropArenaUtil.removeBlock(helper, pos);
        }, Drops.near(pos, 2.0)
                .present(Items.BUCKET, BCCoreItems.FRAGILE_FLUID_CONTAINER.get())
                .absent(BCFactoryBlocks.DISTILLER.get().asItem()));
    }

    // ---------- Stirling engine (raw fuel slot) ----------
    // Both tests go through breakEnginesAndCheck, which also breaks the other engines that hold contents.

    public static void testStirlingEngineHandBreakDropsFuel(GameTestHelper helper) {
        breakEnginesAndCheck(helper, ItemStack.EMPTY);
    }

    public static void testStirlingEnginePickaxeDropsFuelAndSelf(GameTestHelper helper) {
        breakEnginesAndCheck(helper, new ItemStack(Items.WOODEN_PICKAXE));
    }

    /** Breaks the Stirling (fuel slot) and the {@link #CONTENT_ENGINES} as a survival player holding {@code tool}.
     *  The Stirling drops its fuel either way and its own block only when the tool can harvest it; every other
     *  engine's contents must arrive exactly once. The shards of the combustion engine are the reason the drops are
     *  asserted a few ticks after they appear and not on a fixed tick (see {@link DropArenaUtil}). */
    private static void breakEnginesAndCheck(GameTestHelper helper, ItemStack tool) {
        BlockPos pos = new BlockPos(1, 2, 1);
        Item engineItem = BCEnergyBlocks.ENGINE_STONE.get().asItem();
        Drops drops = Drops.near(pos, 2.0).present(Items.COAL);
        if (tool.isEmpty()) {
            drops.absent(engineItem);
        } else {
            drops.present(engineItem);
        }
        for (ExpectedDrop drop : expectedContentDrops()) {
            drops.alsoNear(drop.pos(), 1.0).exactly(drop.item(), drop.count());
        }
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCEnergyBlocks.ENGINE_STONE.get());
            tile(helper, pos, TileEngineStone_BC8.class).setFuelStack(new ItemStack(Items.COAL, 17));
            placeContentEngines(helper);

            DropArenaUtil.breakBlock(helper, pos, tool.copy());
            breakContentEngines(helper, tool);
        }, drops);
    }

    // The other engines that hold contents. They share the Stirling's cell, three blocks apart (the cell is 6 wide
    // in x and 7 deep in z): a popped item drifts up to about a block over the few ticks the drops are watched, so two
    // blocks would let one engine's gear land in the next engine's radius-1 box and be counted there.
    private static final BlockPos FE_ENGINE_POS = new BlockPos(4, 2, 1);
    private static final BlockPos COMBUSTION_ENGINE_POS = new BlockPos(1, 2, 4);
    private static final BlockPos DYNAMO_POS = new BlockPos(4, 2, 4);
    private static final BlockPos[] CONTENT_ENGINES = { FE_ENGINE_POS, COMBUSTION_ENGINE_POS, DYNAMO_POS };

    private static FluidStack water() {
        return new FluidStack(Fluids.WATER, 1000);
    }

    /** An FE engine and an MJ Dynamo with gear upgrades, and a combustion engine with water in two tanks. */
    private static void placeContentEngines(GameTestHelper helper) {
        helper.setBlock(FE_ENGINE_POS, BCEnergyBlocks.ENGINE_FE.get());
        TileEngineFE fe = tile(helper, FE_ENGINE_POS, TileEngineFE.class);
        fe.upgrades.setStackInSlot(0, new ItemStack(BCCoreItems.GEAR_IRON.get()));
        fe.upgrades.setStackInSlot(3, new ItemStack(BCCoreItems.GEAR_GOLD.get()));

        helper.setBlock(COMBUSTION_ENGINE_POS, BCEnergyBlocks.ENGINE_IRON.get());
        TileEngineIron_BC8 combustion = tile(helper, COMBUSTION_ENGINE_POS, TileEngineIron_BC8.class);
        combustion.tankFuel.setFluidStack(0, water());
        combustion.tankCoolant.setFluidStack(0, water());

        helper.setBlock(DYNAMO_POS, BCEnergyBlocks.DYNAMO_MJ.get());
        tile(helper, DYNAMO_POS, TileDynamoMJ.class).upgrades.setStackInSlot(1, new ItemStack(BCCoreItems.GEAR_IRON.get()));
    }

    private static void breakContentEngines(GameTestHelper helper, ItemStack tool) {
        for (BlockPos pos : CONTENT_ENGINES) {
            DropArenaUtil.breakBlock(helper, pos, tool.copy());
        }
    }

    /** What one engine must drop: {@code count} of {@code item} within a block of {@code pos}. Every engine's
     *  contents arrive once, not zero times (a hook the break never reached) and not twice (a player-break drop on
     *  top of the removal hook). */
    private record ExpectedDrop(BlockPos pos, Item item, int count) {}

    private static List<ExpectedDrop> expectedContentDrops() {
        // The combustion engine spills the shards one drop of its two tanks yields.
        NonNullList<ItemStack> oneSpill = NonNullList.create();
        FluidItemDrops.addFluidDrops(oneSpill, water(), water());
        int expectedShards = oneSpill.stream().mapToInt(ItemStack::getCount).sum();
        if (expectedShards <= 0) {
            throw new IllegalStateException("two tanks of water should yield fluid shards, so the shard count "
                    + "below would pass vacuously");
        }
        return List.of(
                new ExpectedDrop(FE_ENGINE_POS, BCCoreItems.GEAR_IRON.get(), 1),
                new ExpectedDrop(FE_ENGINE_POS, BCCoreItems.GEAR_GOLD.get(), 1),
                new ExpectedDrop(DYNAMO_POS, BCCoreItems.GEAR_IRON.get(), 1),
                new ExpectedDrop(COMBUSTION_ENGINE_POS, BCCoreItems.FRAGILE_FLUID_CONTAINER.get(), expectedShards));
    }

    private static <T extends BlockEntity> T tile(GameTestHelper helper, BlockPos rel, Class<T> type) {
        BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        if (!type.isInstance(be)) {
            throw new IllegalStateException("expected a " + type.getSimpleName() + " at " + rel + ", found " + be);
        }
        return type.cast(be);
    }

    // ---------- Filtered Buffer: PHANTOM filter must NOT drop ----------

    public static void testFilteredBufferSkipsFilterSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCTransportBlocks.FILTERED_BUFFER.get());

            //? if >=1.21.10 {
            TileFilteredBuffer buffer = helper.getBlockEntity(pos, TileFilteredBuffer.class);
            //?} else {
            /*TileFilteredBuffer buffer = helper.getBlockEntity(pos);*/
            //?}

            // PHANTOM filter slots — these record what the buffer accepts, they were never
            // consumed. Use a distinctive item so a leak is visible.
            buffer.invFilter.setStackInSlot(0, new ItemStack(Items.NETHER_STAR, 1));
            buffer.invFilter.setStackInSlot(4, new ItemStack(Items.DRAGON_EGG, 1));

            // Real stored items.
            buffer.invMain.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
            buffer.invMain.setStackInSlot(4, new ItemStack(Items.IRON_INGOT, 12));

            DropArenaUtil.breakBlock(helper, pos, new ItemStack(Items.WOODEN_PICKAXE));
        }, Drops.near(pos, 2.0)
                .present(BCTransportBlocks.FILTERED_BUFFER.get().asItem(), Items.DIAMOND, Items.IRON_INGOT)
                .absent(Items.NETHER_STAR, Items.DRAGON_EGG));
    }

    // ---------- Marker: hand-breakable (no pickaxe gate) ----------

    public static void testMarkerHandBreakDropsSelf(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos markerPos = new BlockPos(1, 2, 1);
        // Markers were Material.CIRCUITS in 1.12.2 — hand-break returns the marker item without needing a tool. (The
        // dropped item would outlive the test and could satisfy a later test's "a path marker dropped" check by
        // proximity; awaitDrops discards the cell's items on every exit.)
        DropArenaUtil.awaitDrops(helper, () -> {
            // Marker needs a solid floor for canSurvive to hold.
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(markerPos, BCCoreBlocks.MARKER_PATH.get());

            DropArenaUtil.breakBlock(helper, markerPos, ItemStack.EMPTY);
        }, Drops.near(markerPos, 2.0).present(BCCoreBlocks.MARKER_PATH.get().asItem()));
    }

    // ---------- More non-player removal coverage for the second machine batch ----------
    // The remaining converted blocks (Builder, Laser Table, Distiller, Heat Exchange, Engine Iron)
    // reuse mechanisms already exercised above: opt-in itemManager (Chute), opt-in + getDropTanks
    // (Pump), and standalone fluid (Flood Gate). These two add the genuinely new wrinkles: the
    // Architect's snapshot slots must be reachable by addDrops, and the Tank is the canonical
    // standalone fluid block.

    public static void testArchitectNonPlayerRemovalDropsContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(pos, BCBuildersBlocks.ARCHITECT.get());

            //? if >=1.21.10 {
            TileArchitectTable architect = helper.getBlockEntity(pos, TileArchitectTable.class);
            //?} else {
            /*TileArchitectTable architect = helper.getBlockEntity(pos);*/
            //?}
            // The snapshot in-slot is a real itemManager handler; addDrops must capture it on a
            // non-player removal (this is the assumption behind wiring Architect via the opt-in path).
            architect.invSnapshotIn.setStackInSlot(0, new ItemStack(Items.NETHER_STAR, 1));

            DropArenaUtil.removeBlock(helper, pos);
        }, Drops.near(pos, 2.0)
                .present(Items.NETHER_STAR)
                .absent(BCBuildersBlocks.ARCHITECT.get().asItem()));
    }

    public static void testTankNonPlayerRemovalDropsFluid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        DropArenaUtil.awaitDrops(helper, () -> {
            placeFilledTank(helper, pos);
            DropArenaUtil.removeBlock(helper, pos);
        }, Drops.near(pos, 2.0)
                .present(BCCoreItems.FRAGILE_FLUID_CONTAINER.get())
                .absent(BCFactoryBlocks.TANK.get().asItem()));
    }
}
