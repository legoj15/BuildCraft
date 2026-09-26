/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The version-neutral item-fluid helpers in {@link FluidUtilBC} — the one seam every BuildCraft call site uses to
 * read, fill or empty a fluid container item. NeoForge deprecated its classic {@code FluidUtil} for removal at
 * 1.21.9; on 1.21.10+ these helpers ride the Transfer API ({@code Capabilities.Fluid.ITEM} + {@code ItemAccess}),
 * on 1.21.1 (where the classic helpers are still current) they delegate to them. Every test here runs unchanged on
 * every node, pinning that both implementations behave the same: vanilla buckets, BuildCraft's own fragile fluid
 * shard (a consume-on-empty container), empty containers, non-containers and stacked containers.
 */
public class FluidContainerHelperTest extends VanillaSetupBaseTester {

    private static void assertFluid(FluidStack actual, net.minecraft.world.level.material.Fluid fluid, int amount,
            String msg) {
        Assertions.assertFalse(actual.isEmpty(), msg + " — expected " + amount + " mB, got nothing");
        Assertions.assertTrue(actual.is(fluid), msg + " — wrong fluid: " + FluidUtilBC.getDebugString(actual));
        Assertions.assertEquals(amount, actual.getAmount(), msg + " — wrong amount");
    }

    private static ItemStack fragileShard(int waterMb) {
        ItemStack shard = new ItemStack(BCCoreItems.FRAGILE_FLUID_CONTAINER.get());
        ItemFragileFluidContainer.setFluid(shard, new FluidStack(Fluids.WATER, waterMb));
        return shard;
    }

    private static BCFluidTank tank(int capacity, FluidStack contents) {
        BCFluidTank tank = new BCFluidTank(1, capacity);
        if (!contents.isEmpty()) {
            Assertions.assertEquals(contents.getAmount(), tank.fill(0, contents, false), "test setup: pre-fill");
        }
        return tank;
    }

    private static ItemHandlerSimple slot(ItemStack contents, int slotCapacity) {
        ItemHandlerSimple slots = new ItemHandlerSimple(1, slotCapacity);
        slots.setStackInSlot(0, contents);
        return slots;
    }

    // ---- getFluidContained -------------------------------------------------------------------------------------

    @Test
    public void waterBucketContainsOneBucketOfWater() {
        assertFluid(FluidUtilBC.getFluidContained(new ItemStack(Items.WATER_BUCKET)), Fluids.WATER, 1000,
                "a water bucket");
    }

    @Test
    public void lavaBucketContainsOneBucketOfLava() {
        assertFluid(FluidUtilBC.getFluidContained(new ItemStack(Items.LAVA_BUCKET)), Fluids.LAVA, 1000,
                "a lava bucket");
    }

    @Test
    public void emptyBucketContainsNothing() {
        Assertions.assertTrue(FluidUtilBC.getFluidContained(new ItemStack(Items.BUCKET)).isEmpty(),
                "an empty bucket is a container, but it holds no fluid");
    }

    @Test
    public void nonContainerContainsNothing() {
        Assertions.assertTrue(FluidUtilBC.getFluidContained(new ItemStack(Items.STONE)).isEmpty(),
                "stone has no fluid capability at all");
    }

    @Test
    public void emptyStackContainsNothing() {
        Assertions.assertTrue(FluidUtilBC.getFluidContained(ItemStack.EMPTY).isEmpty(),
                "the empty stack must be answered, not thrown on (ItemAccess.forStack rejects it)");
    }

    @Test
    public void buildcraftShardReportsItsContents() {
        assertFluid(FluidUtilBC.getFluidContained(fragileShard(300)), Fluids.WATER, 300,
                "BuildCraft's fragile fluid shard exposes its fluid through the item capability");
    }

    @Test
    public void stackedContainerReportsOneItemsWorth() {
        ItemStack stacked = new ItemStack(Items.WATER_BUCKET, 3);
        assertFluid(FluidUtilBC.getFluidContained(stacked), Fluids.WATER, 1000,
                "a stack of 3 water buckets reports ONE item's fluid, never the count-multiplied total");
        Assertions.assertEquals(3, stacked.getCount(), "reading must not change the stack's count");
    }

    @Test
    public void readingNeverMutatesTheStack() {
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        ItemStack shard = fragileShard(250);
        ItemStack bucketBefore = bucket.copy();
        ItemStack shardBefore = shard.copy();

        FluidUtilBC.getFluidContained(bucket);
        FluidUtilBC.getFluidContained(shard);

        Assertions.assertTrue(ItemStack.matches(bucketBefore, bucket),
                "reading a bucket's fluid must leave it a full water bucket, got " + bucket);
        Assertions.assertTrue(ItemStack.matches(shardBefore, shard),
                "reading the shard's fluid must not drain (and so shatter) it, got " + shard);
    }

    // ---- isFluidContainer --------------------------------------------------------------------------------------

    @Test
    public void containersAreRecognisedFullOrEmpty() {
        Assertions.assertTrue(FluidUtilBC.isFluidContainer(new ItemStack(Items.BUCKET)), "empty bucket");
        Assertions.assertTrue(FluidUtilBC.isFluidContainer(new ItemStack(Items.WATER_BUCKET)), "water bucket");
        Assertions.assertTrue(FluidUtilBC.isFluidContainer(new ItemStack(Items.BUCKET, 16)), "stacked buckets");
        Assertions.assertTrue(FluidUtilBC.isFluidContainer(fragileShard(100)), "fragile shard");
        Assertions.assertFalse(FluidUtilBC.isFluidContainer(new ItemStack(Items.STONE)), "stone");
        Assertions.assertFalse(FluidUtilBC.isFluidContainer(ItemStack.EMPTY), "the empty stack");
    }

    // ---- drainContainerSlot (container -> tank) ----------------------------------------------------------------

    @Test
    public void waterBucketEmptiesIntoTank() {
        ItemHandlerSimple slots = slot(new ItemStack(Items.WATER_BUCKET), 1);
        BCFluidTank tank = tank(4000, FluidStack.EMPTY);

        Assertions.assertTrue(FluidUtilBC.drainContainerSlot(slots, 0, tank), "a water bucket drains into room");
        assertFluid(tank.getFluidStack(0), Fluids.WATER, 1000, "the tank after the bucket emptied");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.BUCKET), slots.stacks.get(0)),
                "the slot must now hold exactly one empty bucket, got " + slots.stacks.get(0));
    }

    @Test
    public void bucketWillNotPartiallyEmpty() {
        ItemHandlerSimple slots = slot(new ItemStack(Items.WATER_BUCKET), 1);
        BCFluidTank tank = tank(500, FluidStack.EMPTY);

        Assertions.assertFalse(FluidUtilBC.drainContainerSlot(slots, 0, tank),
                "a bucket is all-or-nothing: 500 mB of room cannot take it");
        Assertions.assertTrue(tank.getFluidStack(0).isEmpty(), "nothing may reach the tank");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.WATER_BUCKET), slots.stacks.get(0)),
                "the bucket must stay full, got " + slots.stacks.get(0));
    }

    @Test
    public void bucketWillNotEmptyIntoADifferentFluid() {
        ItemHandlerSimple slots = slot(new ItemStack(Items.WATER_BUCKET), 1);
        BCFluidTank tank = tank(4000, new FluidStack(Fluids.LAVA, 1000));

        Assertions.assertFalse(FluidUtilBC.drainContainerSlot(slots, 0, tank), "water cannot join lava");
        assertFluid(tank.getFluidStack(0), Fluids.LAVA, 1000, "the lava tank is untouched");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.WATER_BUCKET), slots.stacks.get(0)),
                "the bucket must stay full");
    }

    @Test
    public void emptyAndNonContainersDoNothingWhenDrained() {
        ItemHandlerSimple bucketSlot = slot(new ItemStack(Items.BUCKET), 1);
        ItemHandlerSimple stoneSlot = slot(new ItemStack(Items.STONE), 1);
        ItemHandlerSimple emptySlot = slot(ItemStack.EMPTY, 1);
        BCFluidTank tank = tank(4000, FluidStack.EMPTY);

        Assertions.assertFalse(FluidUtilBC.drainContainerSlot(bucketSlot, 0, tank), "empty bucket");
        Assertions.assertFalse(FluidUtilBC.drainContainerSlot(stoneSlot, 0, tank), "stone");
        Assertions.assertFalse(FluidUtilBC.drainContainerSlot(emptySlot, 0, tank), "empty slot");
        Assertions.assertTrue(tank.getFluidStack(0).isEmpty(), "the tank stays empty");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.BUCKET), bucketSlot.stacks.get(0)), "bucket kept");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.STONE), stoneSlot.stacks.get(0)), "stone kept");
    }

    @Test
    public void buildcraftShardShattersWhenEmptied() {
        ItemHandlerSimple slots = slot(fragileShard(300), 1);
        BCFluidTank tank = tank(4000, FluidStack.EMPTY);

        Assertions.assertTrue(FluidUtilBC.drainContainerSlot(slots, 0, tank), "the shard drains into the tank");
        assertFluid(tank.getFluidStack(0), Fluids.WATER, 300, "the tank after the shard emptied");
        Assertions.assertTrue(slots.stacks.get(0).isEmpty(),
                "a fully drained fragile shard is consumed, got " + slots.stacks.get(0));
    }

    @Test
    public void stackedContainersAreNeverDrainedOrDeleted() {
        ItemHandlerSimple slots = slot(new ItemStack(Items.WATER_BUCKET, 2), 64);
        BCFluidTank tank = tank(8000, FluidStack.EMPTY);

        Assertions.assertFalse(FluidUtilBC.drainContainerSlot(slots, 0, tank),
                "a >1 stack has nowhere to put the emptied container, so nothing may happen");
        Assertions.assertTrue(tank.getFluidStack(0).isEmpty(), "no fluid may be created from a refused transfer");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.WATER_BUCKET, 2), slots.stacks.get(0)),
                "both full buckets must survive — the old path swapped the whole stack for ONE empty bucket, got "
                        + slots.stacks.get(0));
    }

    // ---- fillContainerSlot (tank -> container) -----------------------------------------------------------------

    @Test
    public void emptyBucketFillsFromTank() {
        ItemHandlerSimple slots = slot(new ItemStack(Items.BUCKET), 1);
        BCFluidTank tank = tank(4000, new FluidStack(Fluids.WATER, 1500));

        Assertions.assertTrue(FluidUtilBC.fillContainerSlot(slots, 0, tank), "an empty bucket fills from 1500 mB");
        assertFluid(tank.getFluidStack(0), Fluids.WATER, 500, "the tank after one bucket was taken");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.WATER_BUCKET), slots.stacks.get(0)),
                "the slot must now hold exactly one water bucket, got " + slots.stacks.get(0));
    }

    @Test
    public void bucketWillNotPartiallyFill() {
        ItemHandlerSimple slots = slot(new ItemStack(Items.BUCKET), 1);
        BCFluidTank tank = tank(4000, new FluidStack(Fluids.WATER, 500));

        Assertions.assertFalse(FluidUtilBC.fillContainerSlot(slots, 0, tank), "500 mB cannot fill a bucket");
        assertFluid(tank.getFluidStack(0), Fluids.WATER, 500, "the tank is untouched");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.BUCKET), slots.stacks.get(0)),
                "the bucket stays empty");
    }

    @Test
    public void fullAndNonContainersDoNothingWhenFilled() {
        ItemHandlerSimple fullSlot = slot(new ItemStack(Items.WATER_BUCKET), 1);
        ItemHandlerSimple stoneSlot = slot(new ItemStack(Items.STONE), 1);
        ItemHandlerSimple emptySlot = slot(ItemStack.EMPTY, 1);
        BCFluidTank tank = tank(4000, new FluidStack(Fluids.WATER, 2000));

        Assertions.assertFalse(FluidUtilBC.fillContainerSlot(fullSlot, 0, tank), "a full bucket takes no more");
        Assertions.assertFalse(FluidUtilBC.fillContainerSlot(stoneSlot, 0, tank), "stone");
        Assertions.assertFalse(FluidUtilBC.fillContainerSlot(emptySlot, 0, tank), "empty slot");
        assertFluid(tank.getFluidStack(0), Fluids.WATER, 2000, "the tank is untouched");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.WATER_BUCKET), fullSlot.stacks.get(0)),
                "the full bucket is kept");
    }

    @Test
    public void emptyTankFillsNothing() {
        ItemHandlerSimple slots = slot(new ItemStack(Items.BUCKET), 1);
        BCFluidTank tank = tank(4000, FluidStack.EMPTY);

        Assertions.assertFalse(FluidUtilBC.fillContainerSlot(slots, 0, tank), "an empty tank fills nothing");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.BUCKET), slots.stacks.get(0)),
                "the bucket stays empty");
    }

    @Test
    public void stackedContainersAreNeverFilledOrDeleted() {
        ItemHandlerSimple slots = slot(new ItemStack(Items.BUCKET, 2), 64);
        BCFluidTank tank = tank(4000, new FluidStack(Fluids.WATER, 2000));

        Assertions.assertFalse(FluidUtilBC.fillContainerSlot(slots, 0, tank),
                "a >1 stack has nowhere to put the filled container, so nothing may happen");
        assertFluid(tank.getFluidStack(0), Fluids.WATER, 2000, "no fluid may vanish into a refused transfer");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.BUCKET, 2), slots.stacks.get(0)),
                "both empty buckets must survive, got " + slots.stacks.get(0));
    }

    // ---- a stalled machine must not touch its tile ---------------------------------------------------------------
    // The Distiller / Heat Exchanger retry their container slots every few ticks. When the move cannot happen (tank
    // full, bucket already full) the slot's change callback — the tile's setChanged(), which dirties the chunk and
    // pings comparators — must not fire; a real swap fires it exactly once, as setStackInSlot did on the old path.

    private static ItemHandlerSimple watchedSlot(ItemStack contents, List<ItemStack> changes) {
        ItemHandlerSimple slots = slot(contents, 1);
        slots.setCallback((handler, slot, before, after) -> changes.add(after.copy()));
        return slots;
    }

    @Test
    public void stalledDrainDoesNotReportASlotChange() {
        List<ItemStack> changes = new ArrayList<>();
        ItemHandlerSimple slots = watchedSlot(new ItemStack(Items.WATER_BUCKET), changes);
        BCFluidTank full = tank(1000, new FluidStack(Fluids.WATER, 1000));

        Assertions.assertFalse(FluidUtilBC.drainContainerSlot(slots, 0, full), "a full tank takes no bucket");
        Assertions.assertEquals(List.of(), changes, "a refused drain changed nothing, so the tile must not be dirtied");
    }

    @Test
    public void stalledFillDoesNotReportASlotChange() {
        List<ItemStack> changes = new ArrayList<>();
        ItemHandlerSimple slots = watchedSlot(new ItemStack(Items.WATER_BUCKET), changes);
        BCFluidTank source = tank(4000, new FluidStack(Fluids.WATER, 2000));

        Assertions.assertFalse(FluidUtilBC.fillContainerSlot(slots, 0, source), "a full bucket takes no more");
        Assertions.assertEquals(List.of(), changes, "a refused fill changed nothing, so the tile must not be dirtied");
    }

    @Test
    public void successfulSwapReportsExactlyOneSlotChange() {
        List<ItemStack> drained = new ArrayList<>();
        ItemHandlerSimple drainSlot = watchedSlot(new ItemStack(Items.WATER_BUCKET), drained);
        Assertions.assertTrue(FluidUtilBC.drainContainerSlot(drainSlot, 0, tank(4000, FluidStack.EMPTY)), "drain");
        Assertions.assertEquals(1, drained.size(), "one swap, one callback — got " + drained);
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.BUCKET), drained.get(0)),
                "the callback must report the final empty bucket, got " + drained.get(0));

        List<ItemStack> filled = new ArrayList<>();
        ItemHandlerSimple fillSlot = watchedSlot(new ItemStack(Items.BUCKET), filled);
        Assertions.assertTrue(FluidUtilBC.fillContainerSlot(fillSlot, 0,
                tank(4000, new FluidStack(Fluids.WATER, 1000))), "fill");
        Assertions.assertEquals(1, filled.size(), "one swap, one callback — got " + filled);
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.WATER_BUCKET), filled.get(0)),
                "the callback must report the final water bucket, got " + filled.get(0));
    }
}
