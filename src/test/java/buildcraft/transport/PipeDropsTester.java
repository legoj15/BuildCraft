/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.transport.EnumWirePart;

import buildcraft.core.BCCoreItems;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportPlugs;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.plug.PluggableBlocker;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Drop tests for pipes and the things attached to them.
 * <p>
 * Five scenarios:
 * <ol>
 *   <li><b>Pluggable click-break</b> — clicking on a pluggable's bounding box drops just that
 *       pluggable's pick stack; the pipe stays in place. Exercises
 *       {@link buildcraft.transport.block.BlockPipeHolder#onDestroyedByPlayer} with the player
 *       positioned to look directly at a plug.</li>
 *   <li><b>Wire click-break</b> — same idea for a wire part; only the wire item drops.</li>
 *   <li><b>Pickaxe full break</b> — pipe + cargo (items + fluid shards) + pluggables + wires
 *       all drop in one go.</li>
 *   <li><b>Hand full break</b> — cargo + pluggables + wires drop, but the pipe item itself
 *       does NOT (1.12.2 Material.IRON parity, restored via {@code requiresCorrectToolForDrops}
 *       and the {@code canHarvestBlock} gate in {@code dropPipeItems}).</li>
 *   <li><b>Fluid pipe break drops fragile shards</b> — a fluid pipe carrying water in a section
 *       buffer drops fragile fluid-container items when broken with a pickaxe.</li>
 * </ol>
 * <p>
 * The click-break tests position a real Player above the pipe with pitch 90° so
 * {@code player.pick(5.0, ...)} ray-traces straight down into the pluggable / wire AABB —
 * matching what happens in-game when the player aims at a plug and left-clicks. Without that
 * positioning the pick returns no hit on the pipe block and the full-break path runs instead
 * (which is what the pickaxe/hand tests deliberately exercise).
 */
public class PipeDropsTester {

    // ---------- Shared helpers ----------

    private static Player survivalPlayerWith(GameTestHelper helper, ItemStack heldItem) {
        Player p = helper.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, heldItem);
        return p;
    }

    /** Position the mock player two blocks above the pipe, at the given local x/z offset
     *  inside the pipe's block, looking straight down so {@code player.pick(5.0, 0.0f, false)}
     *  ray-traces through the column directly below the player's eye.
     *  <p>
     *  {@code localX} / {@code localZ} pick which part of the pipe's top surface the pick
     *  hits: pass {@code (0.5, 0.5)} for the center (which intersects the UP-facing plug
     *  AABB at {@code y=[0.75, 0.875]}), or pass a wire-corner offset like
     *  {@code (0.21875, 0.21875)} for the WEST_UP_NORTH wire AABB (centered ~3/16 from the
     *  corner with the {@code WIRE_HIT_INFLATE = 1/16} buffer applied).
     *  <p>
     *  {@code player.pick(..., 0.0f, ...)} interpolates eye position and view vector from the
     *  <em>previous</em> tick's fields; {@link Player#setOldPosAndRot()} copies the just-set
     *  current values back over the prev fields so partialTicks=0 returns them. */
    private static Player playerLookingDownAt(GameTestHelper helper, BlockPos absPipePos,
            double localX, double localZ, ItemStack heldItem) {
        Player p = survivalPlayerWith(helper, heldItem);
        double x = absPipePos.getX() + localX;
        double y = absPipePos.getY() + 3.0;
        double z = absPipePos.getZ() + localZ;
        p.setPos(x, y, z);
        p.setXRot(90f);
        p.setYRot(0f);
        p.setOldPosAndRot();
        return p;
    }

    /** Replays {@code ServerPlayerGameMode.destroyBlock} faithfully — drives the same
     *  playerWillDestroy → removeBlock → playerDestroy chain (with the canHarvestBlock gate on
     *  playerDestroy) that gameplay uses. */
    private static void breakAs(GameTestHelper helper, BlockPos relPos, Player player) {
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(absPos);
        BlockEntity be = level.getBlockEntity(absPos);
        boolean canHarvest = state.canHarvestBlock(level, absPos, player);
        state.getBlock().playerWillDestroy(level, absPos, state, player);
        level.removeBlock(absPos, false);
        if (canHarvest) {
            state.getBlock().playerDestroy(level, player, absPos, state, be, player.getMainHandItem());
        }
    }

    /** Place a wood-item pipe at the given relative position and return the tile.
     *  TilePipeHolder.onPlacedBy attaches the pipe definition the way an item-placed pipe
     *  would, so the resulting tile has a real Pipe + flow + behaviour to exercise. */
    private static TilePipeHolder placeItemPipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        return tile;
    }

    /** Same as {@link #placeItemPipe} but with a fluid-flow pipe so {@link PipeFlowFluids}
     *  is the flow instance. */
    private static TilePipeHolder placeFluidPipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_FLUID.get()));
        return tile;
    }

    private static void installBlockerOn(TilePipeHolder tile, Direction side) {
        PluggableBlocker plug = new PluggableBlocker(BCTransportPlugs.blocker, tile, side);
        tile.replacePluggable(side, plug);
    }

    // ---------- Pluggable click-break ----------

    public static void testPluggableBreakDropsItemAndKeepsPipe(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeItemPipe(helper, pipePos);
        installBlockerOn(tile, Direction.UP);

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pipePos);
        BlockState state = level.getBlockState(absPos);

        // Aim at the pipe's vertical center axis — the UP plug AABB spans
        // (0.25, 0.75, 0.25)–(0.75, 0.875, 0.75) which includes (0.5, ?, 0.5).
        Player player = playerLookingDownAt(helper, absPos, 0.5, 0.5, ItemStack.EMPTY);
        FluidState fluidState = level.getFluidState(absPos);

        // Click-break path: BlockPipeHolder.onDestroyedByPlayer should detect the plug hit,
        // pop the plug item, remove the plug from the tile, and return false to cancel the
        // pipe break.
        //? if >=1.21.10 {
        boolean removed = state.getBlock().onDestroyedByPlayer(state, level, absPos, player,
                player.getMainHandItem(), false, fluidState);
        //?} else {
        /*boolean removed = state.getBlock().onDestroyedByPlayer(state, level, absPos, player,
                false, fluidState);*/
        //?}

        helper.assertFalse(removed, "Pipe should not be destroyed when clicking on a plug");
        helper.assertBlockPresent(BCTransportBlocks.PIPE_HOLDER.get(), pipePos);
        helper.assertTrue(tile.getPluggable(Direction.UP) == null, "Plug should be removed from the tile");

        // Radius 4.0: under MC gravity a popResource'd item can fall ~2 blocks in 10 ticks,
        // putting it right at the boundary of a radius-2.0 search and making the assertion
        // flake on ~40% of runs. 4.0 gives generous margin while still being tight enough to
        // detect a real "no item dropped" regression.
        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCTransportItems.PLUG_BLOCKER.get(), pipePos, 4.0);
            helper.succeed();
        });
    }

    // ---------- Wire click-break ----------

    public static void testWireBreakDropsItemAndKeepsPipe(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeItemPipe(helper, pipePos);

        // WEST_UP_NORTH wire AABB (after the 1/16 hit inflation) covers roughly
        // (0.125, 0.6875, 0.125)–(0.3125, 0.875, 0.3125) — the corner where x and z are
        // both small and y is high. A pick from a player at local (0.21875, ?, 0.21875)
        // ray-tracing straight down passes through that box.
        DyeColor wireColor = DyeColor.RED;
        EnumWirePart wirePart = EnumWirePart.WEST_UP_NORTH;
        boolean added = tile.getWireManager().addPart(wirePart, wireColor);
        helper.assertTrue(added, "Wire should install on a fresh pipe");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pipePos);
        BlockState state = level.getBlockState(absPos);

        Player player = playerLookingDownAt(helper, absPos, 0.21875, 0.21875, ItemStack.EMPTY);
        FluidState fluidState = level.getFluidState(absPos);

        //? if >=1.21.10 {
        boolean removed = state.getBlock().onDestroyedByPlayer(state, level, absPos, player,
                player.getMainHandItem(), false, fluidState);
        //?} else {
        /*boolean removed = state.getBlock().onDestroyedByPlayer(state, level, absPos, player,
                false, fluidState);*/
        //?}

        helper.assertFalse(removed, "Pipe should not be destroyed when clicking on a wire");
        helper.assertBlockPresent(BCTransportBlocks.PIPE_HOLDER.get(), pipePos);
        helper.assertTrue(tile.getWireManager().getColorOfPart(wirePart) == null,
                "Wire should be removed from the tile");

        // Radius 4.0: same gravity-margin reasoning as the plug test above.
        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCTransportItems.WIRE_ITEMS.get(wireColor).get().asItem(),
                    pipePos, 4.0);
            helper.succeed();
        });
    }

    // ---------- Pickaxe full break drops pipe + cargo + plug + wire ----------

    public static void testPipePickaxeBreakDropsEverything(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeItemPipe(helper, pipePos);

        installBlockerOn(tile, Direction.DOWN);
        tile.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.BLUE);

        // Seed a traversing item — PipeFlowItems.addDrops returns every non-phantom
        // TravellingItem stack, so this should reappear as a drop on break.
        PipeFlowItems flow = (PipeFlowItems) tile.getPipe().getFlow();
        flow.insertItemsForce(new ItemStack(Items.EMERALD, 3), Direction.NORTH, null, 0.04);

        // The mock player from breakAs is NOT positioned at the pipe, so player.pick returns
        // no hit and the partial-break check in playerWillDestroy short-circuits — the full
        // dropPipeItems path runs.
        breakAs(helper, pipePos, survivalPlayerWith(helper, new ItemStack(Items.WOODEN_PICKAXE)));

        helper.assertBlockPresent(Blocks.AIR, pipePos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCTransportItems.PIPE_WOOD_ITEM.get(), pipePos, 4.0);
            helper.assertItemEntityPresent(BCTransportItems.PLUG_BLOCKER.get(), pipePos, 4.0);
            helper.assertItemEntityPresent(BCTransportItems.WIRE_ITEMS.get(DyeColor.BLUE).get().asItem(),
                    pipePos, 4.0);
            helper.assertItemEntityPresent(Items.EMERALD, pipePos, 4.0);
            helper.succeed();
        });
    }

    // ---------- Hand full break drops everything (pipes are intentionally hand-breakable) ----------

    public static void testPipeHandBreakDropsEverything(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeItemPipe(helper, pipePos);

        installBlockerOn(tile, Direction.DOWN);
        tile.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.GREEN);

        PipeFlowItems flow = (PipeFlowItems) tile.getPipe().getFlow();
        flow.insertItemsForce(new ItemStack(Items.LAPIS_LAZULI, 8), Direction.NORTH, null, 0.04);

        breakAs(helper, pipePos, survivalPlayerWith(helper, ItemStack.EMPTY));

        helper.assertBlockPresent(Blocks.AIR, pipePos);

        helper.runAfterDelay(10, () -> {
            // Pipes are hand-breakable by design — same drop set as a pickaxe break, the
            // pickaxe just speeds up the break action via the mineable/pickaxe tag. Anything
            // less would punish players for forgetting to swap to a pickaxe before tearing
            // down a network.
            helper.assertItemEntityPresent(BCTransportItems.PIPE_WOOD_ITEM.get(), pipePos, 4.0);
            helper.assertItemEntityPresent(BCTransportItems.PLUG_BLOCKER.get(), pipePos, 4.0);
            helper.assertItemEntityPresent(BCTransportItems.WIRE_ITEMS.get(DyeColor.GREEN).get().asItem(),
                    pipePos, 4.0);
            helper.assertItemEntityPresent(Items.LAPIS_LAZULI, pipePos, 4.0);
            helper.succeed();
        });
    }

    // ---------- Fluid pipe break drops fragile fluid shards ----------

    public static void testFluidPipeBreakDropsFluidShards(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeFluidPipe(helper, pipePos);

        // Seed a non-trivial amount so the fluid-shard converter has a stack to emit.
        PipeFlowFluids flow = (PipeFlowFluids) tile.getPipe().getFlow();
        int inserted = flow.insertFluidsForce(new FluidStack(Fluids.WATER, 1000), null, false);
        helper.assertTrue(inserted > 0, "Fluid pipe should accept water when empty");

        breakAs(helper, pipePos, survivalPlayerWith(helper, new ItemStack(Items.WOODEN_PICKAXE)));

        helper.assertBlockPresent(Blocks.AIR, pipePos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCTransportItems.PIPE_COBBLE_FLUID.get(), pipePos, 4.0);
            helper.assertItemEntityPresent(BCCoreItems.FRAGILE_FLUID_CONTAINER.get(), pipePos, 4.0);
            helper.succeed();
        });
    }
}
