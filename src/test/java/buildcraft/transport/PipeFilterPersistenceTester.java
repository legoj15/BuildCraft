/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.LevelChunk;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.transport.pipe.PipeBehaviour;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.net.NetEnumUtil;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.transport.container.ContainerDiamondPipe;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.container.ContainerEmzuliPipe;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Persistence pins for the Emzuli, Diamond-Wood (Emerald) and Diamond (sorting) pipes: every GUI edit of their saved
 * state must mark the chunk dirty, so the edit is written at the next save.
 *
 * <p>These pipes keep their filter inventory (and the Emzuli slot colours / Emerald filter mode) inside the
 * behaviour, which {@code TilePipeHolder.writeData} serialises live under {@code pipe -> beh}. What vanilla
 * gates is whether the chunk is written at all ({@code ChunkMap} skips a chunk unless {@code isUnsaved()}),
 * and the only things that raise that flag are {@code setChanged()}/{@code blockEntityChanged()}. The filter
 * handlers were built with no change callback, so an edit never raised it; a loaded pipe's per-tick
 * {@code blockEntityChanged} happens to mask that in play, which is why the contract has to be pinned at the
 * edit itself: each test clears the chunk's unsaved flag, drives the edit through the same path the menu uses
 * and asserts the flag is back before any tick can run. The class of bug first bit the Zone Planner rack (see
 * {@code ZonePlannerTransferTester}), whose machine has no such masking tick.
 */
public class PipeFilterPersistenceTester {

    /** Phantom filter slot of the Emzuli menu that the tests edit (slots 0-3 are the filters). */
    private static final int EMZULI_SLOT = 2;
    /** Phantom filter slot of the Emerald menu that the tests edit (slots 0-8 are the filters). */
    private static final int EMERALD_SLOT = 4;

    // --- Emzuli ---

    public static void emzuliFilterEditMarksChunkDirty(GameTestHelper helper) {
        TilePipeHolder tile = placePipe(helper, BCTransportItems.PIPE_EMZULI_ITEM.get());
        PipeBehaviourEmzuli behaviour = (PipeBehaviourEmzuli) tile.getPipe().getBehaviour();
        ContainerEmzuliPipe menu = new ContainerEmzuliPipe(0,
                helper.makeMockPlayer(GameType.CREATIVE).getInventory(), behaviour);
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());

        // A GUI click on a phantom slot ends in SlotPhantom.set.
        markChunkSaved(helper, chunk);
        menu.getSlot(EMZULI_SLOT).set(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(chunk.isUnsaved(), "an Emzuli filter edit must mark the chunk dirty");
        helper.assertTrue(behaviour.invFilters.getStackInSlot(EMZULI_SLOT).is(Items.IRON_INGOT),
                "the edit reached the filter inventory");
        helper.assertTrue(((PipeBehaviourEmzuli) reloadedBehaviour(tile)).invFilters
                .getStackInSlot(EMZULI_SLOT).is(Items.IRON_INGOT), "the filter survives the pipe's saved NBT");

        // The JEI ghost-ingredient drop reaches the same slot through its own packet.
        markChunkSaved(helper, chunk);
        ghostDrop(menu, EMZULI_SLOT + 1, "minecraft:gold_ingot");
        helper.assertTrue(chunk.isUnsaved(), "a JEI ghost drop onto an Emzuli filter must mark the chunk dirty");
        helper.assertTrue(behaviour.invFilters.getStackInSlot(EMZULI_SLOT + 1).is(Items.GOLD_INGOT),
                "the ghost drop reached the filter inventory");

        // Clearing a slot is an edit too: a stale filter would otherwise come back after a relog.
        markChunkSaved(helper, chunk);
        menu.getSlot(EMZULI_SLOT).set(ItemStack.EMPTY);
        helper.assertTrue(chunk.isUnsaved(), "clearing an Emzuli filter must mark the chunk dirty");
        helper.assertTrue(((PipeBehaviourEmzuli) reloadedBehaviour(tile)).invFilters
                .getStackInSlot(EMZULI_SLOT).isEmpty(), "the cleared slot stays cleared in the saved NBT");

        helper.succeed();
    }

    public static void emzuliColourEditMarksChunkDirty(GameTestHelper helper) {
        TilePipeHolder tile = placePipe(helper, BCTransportItems.PIPE_EMZULI_ITEM.get());
        PipeBehaviourEmzuli behaviour = (PipeBehaviourEmzuli) tile.getPipe().getBehaviour();
        ContainerEmzuliPipe menu = new ContainerEmzuliPipe(0,
                helper.makeMockPlayer(GameType.CREATIVE).getInventory(), behaviour);
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());

        markChunkSaved(helper, chunk);
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        buffer.writeByte(DyeColor.PURPLE.getId());
        menu.paintWidgets.get(SlotIndex.TRIANGLE).handleWidgetDataServer(null, buffer);
        buffer.release();

        helper.assertTrue(behaviour.slotColours.get(SlotIndex.TRIANGLE) == DyeColor.PURPLE,
                "the paint widget set the slot colour");
        helper.assertTrue(chunk.isUnsaved(), "an Emzuli slot-colour edit must mark the chunk dirty");
        helper.assertTrue(((PipeBehaviourEmzuli) reloadedBehaviour(tile)).slotColours
                .get(SlotIndex.TRIANGLE) == DyeColor.PURPLE, "the colour survives the pipe's saved NBT");

        helper.succeed();
    }

    // --- Diamond-Wood (Emerald) ---

    public static void woodDiamondFilterEditMarksChunkDirty(GameTestHelper helper) {
        TilePipeHolder tile = placePipe(helper, BCTransportItems.PIPE_DIAMOND_WOOD_ITEM.get());
        PipeBehaviourWoodDiamond behaviour = (PipeBehaviourWoodDiamond) tile.getPipe().getBehaviour();
        ContainerDiamondWoodPipe menu = new ContainerDiamondWoodPipe(0,
                helper.makeMockPlayer(GameType.CREATIVE).getInventory(), behaviour);
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());

        markChunkSaved(helper, chunk);
        menu.getSlot(EMERALD_SLOT).set(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(chunk.isUnsaved(), "an Emerald pipe filter edit must mark the chunk dirty");
        helper.assertTrue(behaviour.filters.getStackInSlot(EMERALD_SLOT).is(Items.IRON_INGOT),
                "the edit reached the filter inventory");
        helper.assertTrue(((PipeBehaviourWoodDiamond) reloadedBehaviour(tile)).filters
                .getStackInSlot(EMERALD_SLOT).is(Items.IRON_INGOT), "the filter survives the pipe's saved NBT");

        markChunkSaved(helper, chunk);
        ghostDrop(menu, EMERALD_SLOT + 1, "minecraft:gold_ingot");
        helper.assertTrue(chunk.isUnsaved(), "a JEI ghost drop onto an Emerald filter must mark the chunk dirty");
        helper.assertTrue(behaviour.filters.getStackInSlot(EMERALD_SLOT + 1).is(Items.GOLD_INGOT),
                "the ghost drop reached the filter inventory");

        markChunkSaved(helper, chunk);
        menu.getSlot(EMERALD_SLOT).set(ItemStack.EMPTY);
        helper.assertTrue(chunk.isUnsaved(), "clearing an Emerald pipe filter must mark the chunk dirty");
        helper.assertTrue(((PipeBehaviourWoodDiamond) reloadedBehaviour(tile)).filters
                .getStackInSlot(EMERALD_SLOT).isEmpty(), "the cleared slot stays cleared in the saved NBT");

        helper.succeed();
    }

    public static void woodDiamondModeEditMarksChunkDirty(GameTestHelper helper) {
        TilePipeHolder tile = placePipe(helper, BCTransportItems.PIPE_DIAMOND_WOOD_ITEM.get());
        PipeBehaviourWoodDiamond behaviour = (PipeBehaviourWoodDiamond) tile.getPipe().getBehaviour();
        ContainerDiamondWoodPipe menu = new ContainerDiamondWoodPipe(0,
                helper.makeMockPlayer(GameType.CREATIVE).getInventory(), behaviour);
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());

        // Replay the client -> server filter-mode packet the mode button sends.
        markChunkSaved(helper, chunk);
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        NetEnumUtil.write(buffer, FilterMode.BLACK_LIST);
        menu.readMessage(ContainerDiamondWoodPipe.NET_FILTER_MODE, buffer, false, null);
        buffer.release();

        helper.assertTrue(behaviour.filterMode == FilterMode.BLACK_LIST, "the mode packet set the filter mode");
        helper.assertTrue(chunk.isUnsaved(), "an Emerald pipe filter-mode edit must mark the chunk dirty");
        helper.assertTrue(((PipeBehaviourWoodDiamond) reloadedBehaviour(tile)).filterMode == FilterMode.BLACK_LIST,
                "the filter mode survives the pipe's saved NBT");

        helper.succeed();
    }

    // --- Diamond (sorting) ---

    /** Phantom filter slot of the Diamond menu that the tests edit (6 rows of 9, one row per pipe side). */
    private static final int DIAMOND_SLOT = 13;

    public static void diamondItemFilterEditMarksChunkDirty(GameTestHelper helper) {
        diamondFilterEditMarksChunkDirty(helper, BCTransportItems.PIPE_DIAMOND_ITEM.get(), "Diamond item pipe");
    }

    public static void diamondFluidFilterEditMarksChunkDirty(GameTestHelper helper) {
        diamondFilterEditMarksChunkDirty(helper, BCTransportItems.PIPE_DIAMOND_FLUID.get(), "Diamond fluid pipe");
    }

    private static void diamondFilterEditMarksChunkDirty(GameTestHelper helper, Item pipeItem, String what) {
        TilePipeHolder tile = placePipe(helper, pipeItem);
        PipeBehaviourDiamond behaviour = (PipeBehaviourDiamond) tile.getPipe().getBehaviour();
        ContainerDiamondPipe menu = new ContainerDiamondPipe(0,
                helper.makeMockPlayer(GameType.CREATIVE).getInventory(), behaviour);
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());

        markChunkSaved(helper, chunk);
        menu.getSlot(DIAMOND_SLOT).set(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(chunk.isUnsaved(), "a " + what + " filter edit must mark the chunk dirty");
        helper.assertTrue(behaviour.filters.getStackInSlot(DIAMOND_SLOT).is(Items.IRON_INGOT),
                "the edit reached the filter inventory");
        helper.assertTrue(((PipeBehaviourDiamond) reloadedBehaviour(tile)).filters
                .getStackInSlot(DIAMOND_SLOT).is(Items.IRON_INGOT), "the filter survives the pipe's saved NBT");

        markChunkSaved(helper, chunk);
        ghostDrop(menu, DIAMOND_SLOT + 1, "minecraft:gold_ingot");
        helper.assertTrue(chunk.isUnsaved(), "a JEI ghost drop onto a " + what + " filter must mark the chunk dirty");
        helper.assertTrue(behaviour.filters.getStackInSlot(DIAMOND_SLOT + 1).is(Items.GOLD_INGOT),
                "the ghost drop reached the filter inventory");

        markChunkSaved(helper, chunk);
        menu.getSlot(DIAMOND_SLOT).set(ItemStack.EMPTY);
        helper.assertTrue(chunk.isUnsaved(), "clearing a " + what + " filter must mark the chunk dirty");
        helper.assertTrue(((PipeBehaviourDiamond) reloadedBehaviour(tile)).filters
                .getStackInSlot(DIAMOND_SLOT).isEmpty(), "the cleared slot stays cleared in the saved NBT");

        helper.succeed();
    }

    // --- helpers ---

    private static TilePipeHolder placePipe(GameTestHelper helper, Item pipeItem) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    /** Clears the chunk's unsaved flag and checks it took. No tick may run between this and the assertion that
     *  follows the edit: a ticking pipe re-dirties its chunk every tick, which would hide a missing callback. */
    private static void markChunkSaved(GameTestHelper helper, LevelChunk chunk) {
        //? if >=1.21.10 {
        chunk.tryMarkSaved();
        //?} else {
        /*chunk.setUnsaved(false);*/
        //?}
        helper.assertFalse(chunk.isUnsaved(), "baseline: the chunk flag is clean before the edit");
    }

    /** Replays the server-bound JEI ghost-ingredient packet for a phantom slot. */
    private static void ghostDrop(ContainerBC_Neptune menu, int slot, String itemId) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        buffer.writeShort(slot);
        buffer.writeUtf(itemId);
        menu.readMessage(ContainerBC_Neptune.NET_GHOST_SLOT_SET, buffer, false, null);
        buffer.release();
    }

    /** Rebuilds the pipe from its saved NBT, as a chunk reload does, and returns the fresh behaviour. */
    private static PipeBehaviour reloadedBehaviour(TilePipeHolder tile) {
        try {
            return new Pipe(tile, tile.getPipe().writeToNbt()).getBehaviour();
        } catch (InvalidInputDataException e) {
            throw new IllegalStateException("Pipe NBT round-trip failed to reconstruct the pipe", e);
        }
    }
}
