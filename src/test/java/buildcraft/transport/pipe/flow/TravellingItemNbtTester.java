/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.api.core.InvalidInputDataException;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Regression guard for the pipe-cargo data-loss bug: in-transit ItemStacks used to round-trip
 * through a lossy id+count helper, so an enchanted/named/damaged item travelling through a pipe
 * came back stripped of all components after a chunk save/reload. {@link TravellingItem} now
 * round-trips through {@code ItemStack.CODEC}; these assert components survive.
 *
 * <p>These are GameTests (not pure JUnit) because the codec round-trip needs a live server's
 * registry access for {@code registryAwareOps()}, and the placed-pipe test needs a real world.
 * The tester lives in {@code buildcraft.transport.pipe.flow} so it can read the package-private
 * {@link TravellingItem#stack}.
 */
public class TravellingItemNbtTester {

    private static ItemStack componentBearingStack() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));
        stack.set(DataComponents.DAMAGE, 42);
        return stack;
    }

    /** Unit-level guard: TravellingItem's own write/read round-trip preserves components. */
    public static void testCargoPreservesComponentsAcrossSaveLoad(GameTestHelper helper) {
        ItemStack original = componentBearingStack();

        TravellingItem item = new TravellingItem(original);
        CompoundTag nbt = item.writeToNbt(0L);
        TravellingItem restored = new TravellingItem(nbt, 0L);

        if (restored.stack.isEmpty()) {
            throw new IllegalStateException("Pipe cargo lost its ItemStack entirely on save/load");
        }
        if (!ItemStack.isSameItemSameComponents(original, restored.stack)) {
            throw new IllegalStateException(
                "Pipe cargo lost item components on save/load: expected " + original
                    + " but got " + restored.stack);
        }
        if (restored.stack.getCount() != original.getCount()) {
            throw new IllegalStateException("Pipe cargo count changed on save/load");
        }
        helper.succeed();
    }

    /** End-to-end guard: an item in transit in a REAL placed pipe keeps its components across the
     *  pipe's persisted-NBT round-trip — exactly the chunk save/reload path. TilePipeHolder.writeData
     *  stores {@code pipe.writeToNbt()}; a reload rebuilds via {@code new Pipe(holder, nbt)} ->
     *  PipeFlowItems' load ctor -> {@code new TravellingItem(compound)}. This closes the
     *  list-wrapper + reconstruction gap the unit test above doesn't exercise. */
    public static void testCargoSurvivesPlacedPipeNbtRoundTrip(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder holder = helper.getBlockEntity(pos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder holder = helper.getBlockEntity(pos);*/
        //?}
        holder.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));

        Pipe pipe = (Pipe) holder.getPipe();
        PipeFlowItems flow = (PipeFlowItems) pipe.getFlow();

        ItemStack original = componentBearingStack();
        flow.insertItemsForce(original, Direction.UP, null, 0.04);

        if (flow.getAllItemsForRender().size() != 1) {
            throw new IllegalStateException(
                "Setup: expected exactly one in-transit item after injection, got "
                    + flow.getAllItemsForRender().size());
        }

        // Round-trip through the pipe's persisted NBT, as a chunk reload does.
        Pipe reloaded;
        try {
            reloaded = new Pipe(holder, pipe.writeToNbt());
        } catch (InvalidInputDataException e) {
            throw new IllegalStateException("Pipe NBT round-trip failed to reconstruct the pipe", e);
        }
        PipeFlowItems reloadedFlow = (PipeFlowItems) reloaded.getFlow();

        List<TravellingItem> items = reloadedFlow.getAllItemsForRender();
        if (items.size() != 1) {
            throw new IllegalStateException(
                "In-transit item lost across the placed-pipe NBT round-trip (got " + items.size() + ")");
        }
        ItemStack restored = items.get(0).stack;
        if (!ItemStack.isSameItemSameComponents(original, restored)) {
            throw new IllegalStateException(
                "In-transit item lost components across the placed-pipe NBT round-trip: expected "
                    + original + " but got " + restored);
        }
        helper.succeed();
    }
}
