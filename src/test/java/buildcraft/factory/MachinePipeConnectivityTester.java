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
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.BCLibConfig.PowerMode;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.silicon.BCSiliconBlocks;
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

    /** A wood pipe must see the workbench as a source it can pull from: it connects as a TILE,
     *  reports a non-zero power request (meaning it found extractable contents), and on receiving
     *  power actually drains the workbench's result slot. */
    public static void testWoodPipeExtractsFromAutoWorkbench(GameTestHelper helper) {
        BlockPos workbenchPos = new BlockPos(1, 2, 1);
        BlockPos pipePos = new BlockPos(1, 2, 2);

        helper.setBlock(workbenchPos, BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());
        TileAutoWorkbenchItems workbench = helper.getBlockEntity(workbenchPos, TileAutoWorkbenchItems.class);
        // The result slot is extract-access — the natural place a wood pipe pulls crafted output from.
        workbench.invResult.setStackInSlot(0, new ItemStack(Items.DIAMOND));

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
        TileAutoWorkbenchItems workbench = helper.getBlockEntity(workbenchPos, TileAutoWorkbenchItems.class);

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

    // ---------- Capability sweep — every machine fixed by this change ----------

    /** Regression net for the whole bug class: each machine whose RegisterCapabilitiesEvent
     *  registration was missing must now expose Capabilities.Item.BLOCK. */
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
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    private static void assertExposesItemHandler(GameTestHelper helper, BlockPos relPos, Block block,
            String name) {
        helper.setBlock(relPos, block);
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(relPos);
        var handler = level.getCapability(Capabilities.Item.BLOCK, absPos, Direction.NORTH);
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
        var energy = level.getCapability(Capabilities.Energy.BLOCK, absPos, Direction.NORTH);
        if (expectExposed) {
            helper.assertTrue(energy != null,
                name + " must expose Forge Energy when MJ-RF autoconversion is enabled");
            helper.assertTrue(energy.getCapacityAsLong() > 0,
                name + "'s Forge Energy handler should report a non-zero capacity");
        } else {
            helper.assertTrue(energy == null,
                name + " must not expose Forge Energy under PowerMode.MJ_ONLY");
        }
    }
}
