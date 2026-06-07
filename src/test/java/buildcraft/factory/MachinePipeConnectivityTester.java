/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.capabilities.Capabilities;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.BCLibConfig.PowerMode;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWood;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Verifies that item pipes can attach to BuildCraft machine inventories.
 *
 * <p>In the 1.21 capability-system port, a block entity's inventory is only visible to pipes if its
 * BE type is registered for {@code Capabilities.Item.BLOCK} in a {@code RegisterCapabilitiesEvent}
 * listener. Five machines that expose pipe-facing inventories via {@code ItemHandlerManager} were
 * never registered, so {@link PipeFlowItems#canConnect} found no handler and refused the
 * connection. These tests pin the fix: three exercise the Auto Workbench against the wood /
 * cobblestone / clay pipe behaviours, and one sweeps every machine that was missing a registration.
 *
 * <p>Two further tests cover Forge Energy: machines with an internal MJ battery expose
 * {@code Capabilities.Energy.BLOCK} only when the power-mode config enables MJ&harr;RF
 * autoconversion, so FE cables power them under {@code MJ_AUTOCONVERT_RF} / {@code DISPLAY_RF}
 * but not under {@code MJ_ONLY}.
 *
 * <p>The pipe is placed one block SOUTH of the machine, so the pipe's NORTH face points at it.
 */
public class MachinePipeConnectivityTester {

    private static final Direction TO_WORKBENCH = Direction.NORTH;

    // ---------- Wood pipe — extraction source ----------

    /** A wood pipe must pull crafted output from the workbench's result slot, never crafting
     *  ingredients from its materials inventory: it connects as a TILE, reports a non-zero power
     *  request, and on receiving power drains the result slot while leaving the insert-only
     *  materials inventory untouched. */
    public static void testWoodPipeExtractsFromAutoWorkbench(GameTestHelper helper) {
        BlockPos workbenchPos = new BlockPos(1, 2, 1);
        BlockPos pipePos = new BlockPos(1, 2, 2);

        helper.setBlock(workbenchPos, BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());
        //? if >=1.21.10 {
        TileAutoWorkbenchItems workbench = helper.getBlockEntity(workbenchPos, TileAutoWorkbenchItems.class);
        //?} else {
        /*TileAutoWorkbenchItems workbench = helper.getBlockEntity(workbenchPos);*/
        //?}
        // The result slot is extract-access — the natural place a wood pipe pulls crafted output from.
        workbench.invResult.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        // The materials inventory is insert-only: a wood pipe must not extract ingredients from it.
        // ItemHandlerManager lists materials before result in the combined handler, so a pipe that
        // *can* drain materials hits these slots first and never reaches the diamond.
        workbench.invMaterials.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));

        TilePipeHolder pipeTile = placeItemPipe(helper, pipePos, BCTransportItems.PIPE_WOOD_ITEM.get());
        Pipe pipe = pipeTile.getPipe();

        // One onTick() synchronously runs updateConnections() and then the directional behaviour's
        // auto-face pass, so the wood pipe both connects to and aims its extraction face at the
        // workbench without depending on arena BE-tick scheduling.
        pipe.markForUpdate();
        pipe.onTick();

        helper.assertTrue(
            pipe.isConnected(TO_WORKBENCH) && pipe.getConnectedType(TO_WORKBENCH) == ConnectedType.TILE,
            "Wood pipe should connect to the auto workbench as a TILE neighbour");

        PipeBehaviourWood wood = (PipeBehaviourWood) pipe.getBehaviour();
        helper.assertTrue(wood.getPowerRequested() > 0,
            "Wood pipe should recognise the workbench as an extractable source (non-zero power request)");

        wood.receivePower(512 * MjAPI.MJ, false);
        helper.assertTrue(workbench.invResult.getStackInSlot(0).isEmpty(),
            "Wood pipe should have pulled the diamond out of the workbench result slot");
        helper.assertTrue(countItem(workbench.invMaterials, Items.IRON_INGOT) == 1,
            "Wood pipe must not extract crafting ingredients from the materials inventory");

        helper.succeed();
    }

    /** A wood pipe must not drain the Assembly Table's resource inventory. The table holds its
     *  crafting materials in an insert-only inventory and ejects finished output itself (like a
     *  quarry — there is no result slot), so a wood pipe has nothing to pull from it. */
    public static void testWoodPipeSkipsAssemblyTableResources(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(1, 2, 1);
        BlockPos pipePos = new BlockPos(1, 2, 2);

        helper.setBlock(tablePos, BCSiliconBlocks.ASSEMBLY_TABLE.get());
        //? if >=1.21.10 {
        TileAssemblyTable table = helper.getBlockEntity(tablePos, TileAssemblyTable.class);
        //?} else {
        /*TileAssemblyTable table = helper.getBlockEntity(tablePos);*/
        //?}
        // The resource inventory is insert-only — a wood pipe must not pull these out.
        table.inv.setStackInSlot(0, new ItemStack(Items.REDSTONE));

        TilePipeHolder pipeTile = placeItemPipe(helper, pipePos, BCTransportItems.PIPE_WOOD_ITEM.get());
        Pipe pipe = pipeTile.getPipe();
        pipe.markForUpdate();
        pipe.onTick();

        helper.assertTrue(
            pipe.isConnected(TO_WORKBENCH) && pipe.getConnectedType(TO_WORKBENCH) == ConnectedType.TILE,
            "Wood pipe should connect to the assembly table as a TILE neighbour");

        PipeBehaviourWood wood = (PipeBehaviourWood) pipe.getBehaviour();
        wood.receivePower(512 * MjAPI.MJ, false);
        helper.assertTrue(countItem(table.inv, Items.REDSTONE) == 1,
            "Wood pipe must not extract resources from the assembly table's insert-only inventory");

        helper.succeed();
    }

    // ---------- Cobblestone pipe — plain connection ----------

    /** A plain transport pipe (cobblestone) just needs to register the workbench as a TILE
     *  neighbour — it does not extract, it is the "standard destination" baseline. */
    public static void testCobblestonePipeConnectsToAutoWorkbench(GameTestHelper helper) {
        BlockPos workbenchPos = new BlockPos(1, 2, 1);
        BlockPos pipePos = new BlockPos(1, 2, 2);

        helper.setBlock(workbenchPos, BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());
        TilePipeHolder pipeTile = placeItemPipe(helper, pipePos, BCTransportItems.PIPE_COBBLE_ITEM.get());
        Pipe pipe = pipeTile.getPipe();

        pipe.markForUpdate();
        pipe.onTick();

        helper.assertTrue(pipe.isConnected(TO_WORKBENCH),
            "Cobblestone pipe should connect to the auto workbench");
        helper.assertTrue(pipe.getConnectedType(TO_WORKBENCH) == ConnectedType.TILE,
            "Cobblestone pipe's connection to the auto workbench should be a TILE connection");

        helper.succeed();
    }

    // ---------- Clay pipe — inventory insertion ----------

    /** A clay pipe's purpose is to push its contents into an adjacent inventory. An item dropped
     *  into the pipe should travel through and land in the workbench's materials inventory. Item
     *  transit is multi-tick, so this polls until the diamond arrives. */
    public static void testClayPipeInsertsIntoAutoWorkbench(GameTestHelper helper) {
        BlockPos workbenchPos = new BlockPos(1, 2, 1);
        BlockPos pipePos = new BlockPos(1, 2, 2);

        helper.setBlock(workbenchPos, BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());
        //? if >=1.21.10 {
        TileAutoWorkbenchItems workbench = helper.getBlockEntity(workbenchPos, TileAutoWorkbenchItems.class);
        //?} else {
        /*TileAutoWorkbenchItems workbench = helper.getBlockEntity(workbenchPos);*/
        //?}

        TilePipeHolder pipeTile = placeItemPipe(helper, pipePos, BCTransportItems.PIPE_CLAY_ITEM.get());
        Pipe pipe = pipeTile.getPipe();
        pipe.markForUpdate();

        // Force an item into the pipe from an unconnected face; the workbench is the only TILE it
        // can route to, and the clay behaviour prioritises inventory delivery over passing it on.
        PipeFlowItems flow = (PipeFlowItems) pipe.getFlow();
        flow.insertItemsForce(new ItemStack(Items.DIAMOND), Direction.UP, null, 0.08);

        helper.succeedWhen(() -> {
            helper.assertTrue(pipe.isConnected(TO_WORKBENCH),
                "Clay pipe should connect to the auto workbench");
            helper.assertTrue(countItem(workbench.invMaterials, Items.DIAMOND) >= 1,
                "Clay pipe should have delivered the diamond into the workbench's materials inventory");
        });
    }

    // ---------- Architect Table — snapshot in/out slots ----------

    /** A wood pipe must pull a finished (used) snapshot out of the Architect's EXTRACT-access
     *  output slot. This is the regression that prompted the fix: after the 26.1 port the
     *  Architect's slots were plain ItemStack fields exposing no item handler, so pipes neither
     *  connected to nor extracted from it (1.12.2 exposed both slots via ItemHandlerManager). */
    public static void testWoodPipeExtractsFinishedBlueprintFromArchitect(GameTestHelper helper) {
        BlockPos architectPos = new BlockPos(1, 2, 1);
        BlockPos pipePos = new BlockPos(1, 2, 2);

        helper.setBlock(architectPos, BCBuildersBlocks.ARCHITECT.get());
        //? if >=1.21.10 {
        TileArchitectTable architect = helper.getBlockEntity(architectPos, TileArchitectTable.class);
        //?} else {
        /*TileArchitectTable architect = helper.getBlockEntity(architectPos);*/
        //?}
        // Drop a finished blueprint into the output slot a wood pipe pulls from. The box is invalid
        // (placed via setBlock, no land marks), so the Architect never scans or touches this slot.
        architect.invSnapshotOut.setStackInSlot(0, new ItemStack(BCBuildersItems.BLUEPRINT_USED.get()));

        TilePipeHolder pipeTile = placeItemPipe(helper, pipePos, BCTransportItems.PIPE_WOOD_ITEM.get());
        Pipe pipe = pipeTile.getPipe();
        pipe.markForUpdate();
        pipe.onTick();

        helper.assertTrue(
            pipe.isConnected(TO_WORKBENCH) && pipe.getConnectedType(TO_WORKBENCH) == ConnectedType.TILE,
            "Wood pipe should connect to the architect table as a TILE neighbour");

        PipeBehaviourWood wood = (PipeBehaviourWood) pipe.getBehaviour();
        helper.assertTrue(wood.getPowerRequested() > 0,
            "Wood pipe should recognise the architect's output slot as an extractable source");

        wood.receivePower(512 * MjAPI.MJ, false);
        helper.assertTrue(architect.invSnapshotOut.getStackInSlot(0).isEmpty(),
            "Wood pipe should have pulled the finished blueprint out of the architect output slot");

        helper.succeed();
    }

    /** A clay pipe must deliver a blank snapshot into the Architect's INSERT-access input slot.
     *  Item transit is multi-tick, so this polls until the blueprint arrives. */
    public static void testClayPipeInsertsBlankBlueprintIntoArchitect(GameTestHelper helper) {
        BlockPos architectPos = new BlockPos(1, 2, 1);
        BlockPos pipePos = new BlockPos(1, 2, 2);

        helper.setBlock(architectPos, BCBuildersBlocks.ARCHITECT.get());
        //? if >=1.21.10 {
        TileArchitectTable architect = helper.getBlockEntity(architectPos, TileArchitectTable.class);
        //?} else {
        /*TileArchitectTable architect = helper.getBlockEntity(architectPos);*/
        //?}

        TilePipeHolder pipeTile = placeItemPipe(helper, pipePos, BCTransportItems.PIPE_CLAY_ITEM.get());
        Pipe pipe = pipeTile.getPipe();
        pipe.markForUpdate();

        // The Architect is the only TILE the clay pipe can route to; force a blank blueprint in
        // from an unconnected face. The input slot's checker accepts any ItemSnapshot, and the
        // EXTRACT-wrapped output slot refuses insertion, so it lands in the input.
        PipeFlowItems flow = (PipeFlowItems) pipe.getFlow();
        flow.insertItemsForce(new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get()), Direction.UP, null, 0.08);

        helper.succeedWhen(() -> {
            helper.assertTrue(pipe.isConnected(TO_WORKBENCH),
                "Clay pipe should connect to the architect table");
            helper.assertTrue(
                architect.getSnapshotIn().getItem() == BCBuildersItems.BLUEPRINT_CLEAN.get(),
                "Clay pipe should have delivered the blank blueprint into the architect input slot");
        });
    }

    // ---------- Capability sweep — every machine fixed by this change ----------

    /** Regression net for the whole bug class: each machine whose RegisterCapabilitiesEvent
     *  registration was missing must now expose Capabilities.Item.BLOCK. The Architect Table
     *  joined this list after the same omission was found for its snapshot in/out slots. The
     *  Mining Well exposes an <em>empty</em> handler purely so item pipes render a connection to
     *  it — it has no internal buffer and pushes mined drops out itself — matching the Quarry and
     *  the role 1.12.2's AutomaticProvidingTransactor capability played. */
    public static void testItemMachinesExposeItemHandlerCapability(GameTestHelper helper) {
        assertExposesItemHandler(helper, new BlockPos(1, 2, 1),
            BCFactoryBlocks.AUTOWORKBENCH_ITEM.get(), "Auto Workbench");
        assertExposesItemHandler(helper, new BlockPos(2, 2, 1),
            BCSiliconBlocks.ASSEMBLY_TABLE.get(), "Assembly Table");
        assertExposesItemHandler(helper, new BlockPos(1, 2, 2),
            BCSiliconBlocks.INTEGRATION_TABLE.get(), "Integration Table");
        assertExposesItemHandler(helper, new BlockPos(2, 2, 2),
            BCSiliconBlocks.ADVANCED_CRAFTING_TABLE.get(), "Advanced Crafting Table");
        assertExposesItemHandler(helper, new BlockPos(1, 3, 1),
            BCBuildersBlocks.LIBRARY.get(), "Electronic Library");
        assertExposesItemHandler(helper, new BlockPos(2, 3, 1),
            BCBuildersBlocks.ARCHITECT.get(), "Architect Table");
        assertExposesItemHandler(helper, new BlockPos(1, 3, 2),
            BCFactoryBlocks.MINING_WELL.get(), "Mining Well");
        helper.succeed();
    }

    // ---------- Forge Energy — config-gated FE exposure on MJ-battery machines ----------

    /** Every machine with an internal MJ battery exposes Forge Energy when the power mode
     *  enables MJ-RF autoconversion, so FE cables can power it directly. */
    public static void testMjBatteryMachinesExposeFeWhenAutoconvertEnabled(GameTestHelper helper) {
        PowerMode previous = BCLibConfig.powerMode.get();
        try {
            BCLibConfig.powerMode.set(PowerMode.MJ_AUTOCONVERT_RF);
            checkMjBatteryMachineFe(helper, true);
            helper.succeed();
        } finally {
            BCLibConfig.powerMode.set(previous);
        }
    }

    /** Under PowerMode.MJ_ONLY autoconversion is off, so no machine exposes Forge Energy —
     *  FE cables must not connect. Covers the Quarry/Filler/Builder too, which previously
     *  exposed an FE surface regardless of the power mode. */
    public static void testMjBatteryMachinesHideFeUnderMjOnly(GameTestHelper helper) {
        PowerMode previous = BCLibConfig.powerMode.get();
        try {
            BCLibConfig.powerMode.set(PowerMode.MJ_ONLY);
            checkMjBatteryMachineFe(helper, false);
            helper.succeed();
        } finally {
            BCLibConfig.powerMode.set(previous);
        }
    }

    // ---------- Helpers ----------

    /** Places a pipe-holder block and attaches the given pipe item, the way an item-placed pipe
     *  would, so the resulting tile has a real Pipe + flow + behaviour to exercise. */
    private static TilePipeHolder placeItemPipe(GameTestHelper helper, BlockPos relPos, Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    private static void assertExposesItemHandler(GameTestHelper helper, BlockPos relPos, Block block,
            String name) {
        helper.setBlock(relPos, block);
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(relPos);
        //? if >=1.21.10 {
        var handler = level.getCapability(Capabilities.Item.BLOCK, absPos, Direction.NORTH);
        //?} else {
        /*var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, absPos, Direction.NORTH);*/
        //?}
        helper.assertTrue(handler != null,
            name + " must expose Capabilities.Item.BLOCK so item pipes can connect to it");
    }

    private static int countItem(ItemHandlerSimple inv, Item item) {
        int total = 0;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Places each of the eight MJ-battery machines and asserts whether it exposes a working
     *  Forge Energy capability, per the power mode set by the caller. */
    private static void checkMjBatteryMachineFe(GameTestHelper helper, boolean expectExposed) {
        assertEnergyCap(helper, new BlockPos(1, 2, 1), BCFactoryBlocks.MINING_WELL.get(), "Mining Well", expectExposed);
        assertEnergyCap(helper, new BlockPos(2, 2, 1), BCFactoryBlocks.PUMP.get(), "Pump", expectExposed);
        assertEnergyCap(helper, new BlockPos(1, 2, 2), BCFactoryBlocks.DISTILLER.get(), "Distiller", expectExposed);
        assertEnergyCap(helper, new BlockPos(2, 2, 2), BCFactoryBlocks.CHUTE.get(), "Chute", expectExposed);
        assertEnergyCap(helper, new BlockPos(1, 3, 1), BCSiliconBlocks.LASER.get(), "Laser", expectExposed);
        assertEnergyCap(helper, new BlockPos(2, 3, 1), BCBuildersBlocks.QUARRY.get(), "Quarry", expectExposed);
        assertEnergyCap(helper, new BlockPos(1, 3, 2), BCBuildersBlocks.FILLER.get(), "Filler", expectExposed);
        assertEnergyCap(helper, new BlockPos(2, 3, 2), BCBuildersBlocks.BUILDER.get(), "Builder", expectExposed);
    }

    private static void assertEnergyCap(GameTestHelper helper, BlockPos relPos, Block block,
            String name, boolean expectExposed) {
        helper.setBlock(relPos, block);
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(relPos);
        //? if >=1.21.10 {
        var energy = level.getCapability(Capabilities.Energy.BLOCK, absPos, Direction.NORTH);
        //?} else {
        /*var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, absPos, Direction.NORTH);*/
        //?}
        if (expectExposed) {
            helper.assertTrue(energy != null,
                name + " must expose Forge Energy when MJ-RF autoconversion is enabled");
            //? if >=1.21.10 {
            helper.assertTrue(energy.getCapacityAsLong() > 0,
                name + "'s Forge Energy handler should report a non-zero capacity");
            //?} else {
            /*helper.assertTrue(energy.getMaxEnergyStored() > 0,
                name + "'s Forge Energy handler should report a non-zero capacity");*/
            //?}
        } else {
            helper.assertTrue(energy == null,
                name + " must not expose Forge Energy under PowerMode.MJ_ONLY");
        }
    }
}
