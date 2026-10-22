/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.statements.IAction;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventActionActivate;

import buildcraft.core.BCCoreItems;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.net.NetEnumUtil;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.transport.container.ContainerDiamondPipe;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.container.ContainerEmzuliPipe;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDaizuli;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;
import buildcraft.transport.pipe.behaviour.PipeBehaviourLapis;
import buildcraft.transport.pipe.behaviour.PipeBehaviourLimiter;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Persistence pins for the pipes whose saved state a player edits: every GUI, wrench or gate edit of it must mark the
 * chunk dirty, so the edit is written at the next save. Covers the Emzuli, Diamond-Wood (Emerald) and Diamond
 * (sorting) filters, and the wrench / gate-action edits of the Daizuli, Lapis, Limiter (iron / diamond power) and
 * Stripes pipes plus the facing Wood, Iron and Daizuli share.
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

    // --- Wrench and gate edits (Daizuli, Lapis, Limiter, Stripes, and the facing Wood / Iron / Daizuli share) ---

    public static void daizuliEditsMarkChunkDirty(GameTestHelper helper) {
        TilePipeHolder tile = placePipe(helper, BCTransportItems.PIPE_DAIZULI_ITEM.get());
        PipeBehaviourDaizuli behaviour = (PipeBehaviourDaizuli) tile.getPipe().getBehaviour();
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());
        Player wrencher = wrenchHolder(helper);

        // A wrench on a side re-aims the pipe: PipeBehaviourDirectional.setCurrentDir, which Wood, Iron and
        // Emerald share (those two cannot be driven from a lone pipe: they only face a connection).
        markChunkSaved(helper, chunk);
        helper.assertTrue(behaviour.onPipeActivate(wrencher, hitOn(tile), 0f, 0f, 0f, EnumPipePart.NORTH),
                "the wrench click on a side was consumed");
        helper.assertTrue(behaviour.getCurrentDir() == Direction.NORTH, "the wrench re-aimed the pipe");
        helper.assertTrue(chunk.isUnsaved(), "a wrench re-aim must mark the chunk dirty");
        helper.assertTrue(((PipeBehaviourDaizuli) reloadedBehaviour(tile)).getCurrentDir() == Direction.NORTH,
                "the facing survives the pipe's saved NBT");

        // A wrench on the middle cycles the colour.
        markChunkSaved(helper, chunk);
        helper.assertTrue(behaviour.onPipeActivate(wrencher, hitOn(tile), 0f, 0f, 0f, EnumPipePart.CENTER),
                "the wrench click on the centre was consumed");
        DyeColor cycled = DyeColor.byId(DyeColor.WHITE.getId() + 1);
        helper.assertTrue(savedColour(behaviour) == cycled, "the wrench advanced the colour");
        helper.assertTrue(chunk.isUnsaved(), "a wrench colour cycle must mark the chunk dirty");
        helper.assertTrue(savedColour(reloadedBehaviour(tile)) == cycled, "the colour survives the pipe's saved NBT");

        // The gate's "paint pipe" action.
        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_PIPE_COLOUR[DyeColor.RED.ordinal()]);
        helper.assertTrue(savedColour(behaviour) == DyeColor.RED, "the gate action set the colour");
        helper.assertTrue(chunk.isUnsaved(), "a gate colour action must mark the chunk dirty");
        helper.assertTrue(savedColour(reloadedBehaviour(tile)) == DyeColor.RED, "the colour survives the saved NBT");

        // A gate fires its action every tick while its trigger holds: the same value again changes nothing, and
        // must not re-dirty (a setChanged also drops the pipe's cached collision shape).
        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_PIPE_COLOUR[DyeColor.RED.ordinal()]);
        helper.assertFalse(chunk.isUnsaved(), "repeating the colour the pipe already has must not dirty the chunk");

        // The gate's "face this way" action.
        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.EAST.ordinal()]);
        helper.assertTrue(behaviour.getCurrentDir() == Direction.EAST, "the gate action re-aimed the pipe");
        helper.assertTrue(chunk.isUnsaved(), "a gate direction action must mark the chunk dirty");
        helper.assertTrue(((PipeBehaviourDaizuli) reloadedBehaviour(tile)).getCurrentDir() == Direction.EAST,
                "the facing survives the saved NBT");

        helper.succeed();
    }

    public static void lapisEditsMarkChunkDirty(GameTestHelper helper) {
        TilePipeHolder tile = placePipe(helper, BCTransportItems.PIPE_LAPIS_ITEM.get());
        PipeBehaviourLapis behaviour = (PipeBehaviourLapis) tile.getPipe().getBehaviour();
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());
        Player wrencher = wrenchHolder(helper);

        markChunkSaved(helper, chunk);
        helper.assertTrue(behaviour.onPipeActivate(wrencher, hitOn(tile), 0f, 0f, 0f, EnumPipePart.CENTER),
                "the wrench click was consumed");
        DyeColor cycled = DyeColor.byId(DyeColor.WHITE.getId() + 1);
        helper.assertTrue(savedColour(behaviour) == cycled, "the wrench advanced the colour");
        helper.assertTrue(chunk.isUnsaved(), "a wrench colour cycle must mark the chunk dirty");
        helper.assertTrue(savedColour(reloadedBehaviour(tile)) == cycled, "the colour survives the pipe's saved NBT");

        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_PIPE_COLOUR[DyeColor.GREEN.ordinal()]);
        helper.assertTrue(savedColour(behaviour) == DyeColor.GREEN, "the gate action set the colour");
        helper.assertTrue(chunk.isUnsaved(), "a gate colour action must mark the chunk dirty");
        helper.assertTrue(savedColour(reloadedBehaviour(tile)) == DyeColor.GREEN, "the colour survives the saved NBT");

        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_PIPE_COLOUR[DyeColor.GREEN.ordinal()]);
        helper.assertFalse(chunk.isUnsaved(), "repeating the colour the pipe already has must not dirty the chunk");

        helper.succeed();
    }

    /** The iron power pipe: the limiter behaviour shared by the iron and diamond kinesis and RF pipes. */
    public static void limiterEditsMarkChunkDirty(GameTestHelper helper) {
        TilePipeHolder tile = placePipe(helper, BCTransportItems.PIPE_IRON_POWER.get());
        PipeBehaviourLimiter behaviour = (PipeBehaviourLimiter) tile.getPipe().getBehaviour();
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());
        Player wrencher = wrenchHolder(helper);

        markChunkSaved(helper, chunk);
        helper.assertTrue(behaviour.onPipeActivate(wrencher, hitOn(tile), 0f, 0f, 0f, EnumPipePart.CENTER),
                "the wrench click was consumed");
        helper.assertTrue(savedLimitShift(behaviour) == 1, "the wrench stepped the limit");
        helper.assertTrue(chunk.isUnsaved(), "a wrench limit step must mark the chunk dirty");
        helper.assertTrue(savedLimitShift(reloadedBehaviour(tile)) == 1, "the limit survives the pipe's saved NBT");

        // The gate's limit actions run from index 0 = fully blocked, so index (MAX_SHIFT - shift) sets that shift.
        int shift = 3;
        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_IRON_POWER_LIMIT[PipeBehaviourLimiter.MAX_SHIFT - shift]);
        helper.assertTrue(savedLimitShift(behaviour) == shift, "the gate action set the limit");
        helper.assertTrue(chunk.isUnsaved(), "a gate limit action must mark the chunk dirty");
        helper.assertTrue(savedLimitShift(reloadedBehaviour(tile)) == shift, "the limit survives the saved NBT");

        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_IRON_POWER_LIMIT[PipeBehaviourLimiter.MAX_SHIFT - shift]);
        helper.assertFalse(chunk.isUnsaved(), "repeating the limit the pipe already has must not dirty the chunk");

        helper.succeed();
    }

    public static void stripesDirectionEditMarksChunkDirty(GameTestHelper helper) {
        TilePipeHolder tile = placePipe(helper, BCTransportItems.PIPE_STRIPES_ITEM.get());
        PipeBehaviourStripes behaviour = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
        LevelChunk chunk = helper.getLevel().getChunkAt(tile.getBlockPos());

        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.EAST.ordinal()]);
        helper.assertTrue(behaviour.direction == Direction.EAST, "the gate action aimed the stripes pipe");
        helper.assertTrue(chunk.isUnsaved(), "a gate direction action must mark the chunk dirty");
        helper.assertTrue(((PipeBehaviourStripes) reloadedBehaviour(tile)).direction == Direction.EAST,
                "the direction survives the pipe's saved NBT");

        markChunkSaved(helper, chunk);
        fireAction(tile, BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.EAST.ordinal()]);
        helper.assertFalse(chunk.isUnsaved(), "repeating the direction the pipe already has must not dirty the chunk");

        helper.succeed();
    }

    // --- helpers ---

    /** A survival mock player with a BuildCraft wrench in the main hand. */
    private static Player wrenchHolder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCCoreItems.WRENCH.get()));
        return player;
    }

    private static BlockHitResult hitOn(TilePipeHolder tile) {
        BlockPos pos = tile.getBlockPos();
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /** Fires a gate action at the pipe the way {@code GateLogic} does. */
    private static void fireAction(TilePipeHolder tile, IAction action) {
        tile.fireEvent(new PipeEventActionActivate(tile, action, new IStatementParameter[0], EnumPipePart.CENTER));
    }

    /** The colour a Daizuli or Lapis behaviour would save. */
    private static DyeColor savedColour(PipeBehaviour behaviour) {
        return NBTUtilBC.readEnum(behaviour.writeToNbt().get("colour"), DyeColor.class);
    }

    /** The limit a Limiter behaviour would save. */
    private static int savedLimitShift(PipeBehaviour behaviour) {
        return NBTUtilBC.getInt(behaviour.writeToNbt(), "limitShift", -1);
    }

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
