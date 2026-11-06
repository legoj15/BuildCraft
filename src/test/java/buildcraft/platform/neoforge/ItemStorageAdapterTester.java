/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.Items;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?}

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.lib.transfer.ItemStorageUtil;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransaction;

/**
 * The item side of the transfer seam: BuildCraft's {@code insertStacking} port, and {@link NeoForgeItemStorages}, the
 * adapter every BuildCraft item capability and every lookup of another mod's inventory now goes through.
 */
public class ItemStorageAdapterTester extends VanillaSetupBaseTester {

    private static ItemHandlerSimple simple(ItemStack... contents) {
        ItemHandlerSimple handler = new ItemHandlerSimple(contents.length);
        for (int i = 0; i < contents.length; i++) {
            handler.setStackInSlot(i, contents[i]);
        }
        return handler;
    }

    private static List<String> slots(BCItemStorage storage) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.getStack(i);
            out.add(stack.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + "x" + stack.getCount());
        }
        return out;
    }

    @Test
    public void insertStackingFillsOccupiedSlotsFirstAndCommitsWithoutATransaction() {
        // Slot 1 is empty, slot 2 already holds 60 stone: the 10 offered top slot 2 up (4) before slot 1 gets 6.
        ItemHandlerSimple handler = simple(new ItemStack(Items.DIRT, 3), ItemStack.EMPTY, new ItemStack(Items.STONE, 60));
        int inserted = ItemStorageUtil.insertStacking(handler, new ItemStack(Items.STONE), 10, null);
        assertEquals(10, inserted);
        assertEquals(List.of("dirtx3", "stonex6", "stonex64"), slots(handler));
    }

    @Test
    public void insertStackingInsideATransactionRollsBackWithIt() {
        ItemHandlerSimple handler = simple(ItemStack.EMPTY, ItemStack.EMPTY);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(70, ItemStorageUtil.insertStacking(handler, new ItemStack(Items.STONE), 70, tx));
        }
        assertEquals(List.of("-", "-"), slots(handler), "the caller's transaction aborted, so nothing went in");
    }

    @Test
    public void insertStackingRefusesWhatNeoForgeRefuses() {
        ItemHandlerSimple handler = simple(ItemStack.EMPTY);
        assertThrows(IllegalArgumentException.class, () -> ItemStorageUtil.insertStacking(handler, ItemStack.EMPTY, 1, null));
        assertThrows(IllegalArgumentException.class,
            () -> ItemStorageUtil.insertStacking(handler, new ItemStack(Items.STONE), -1, null));
        assertEquals(0, ItemStorageUtil.insertStacking(null, new ItemStack(Items.STONE), 5, null));
        assertEquals(0, ItemStorageUtil.insertStacking(handler, new ItemStack(Items.STONE), 0, null));
    }

    @Test
    public void adaptersUnwrapEachOther() {
        ItemHandlerSimple handler = simple(ItemStack.EMPTY);
        var neo = NeoForgeItemStorages.toNeo(handler);
        assertSame(handler, NeoForgeItemStorages.fromNeo(neo), "a BuildCraft storage comes back as itself");
    }

    //? if >=1.21.10 {
    /** What {@code ResourceHandlerUtil.insertStacking(handler, resource, n, null)} does, the call TileChute and
     *  InventoryUtil made before the seam: a null transaction opens a root of its own and commits it, and the order
     *  is occupied indices first, then empty ones. BuildCraft's port must land the same stacks in the same slots. */
    @Test
    public void insertStackingMatchesNeoForgeOnANeoForgeHandler() {
        ItemStacksResourceHandler neo = new ItemStacksResourceHandler(4);
        neo.set(1, ItemResource.of(Items.STONE), 50);
        neo.set(3, ItemResource.of(Items.STONE), 63);
        int neoInserted = ResourceHandlerUtil.insertStacking(neo, ItemResource.of(Items.STONE), 100, null);

        ItemStacksResourceHandler twin = new ItemStacksResourceHandler(4);
        twin.set(1, ItemResource.of(Items.STONE), 50);
        twin.set(3, ItemResource.of(Items.STONE), 63);
        BCItemStorage view = NeoForgeItemStorages.fromNeo(twin);
        int bcInserted = ItemStorageUtil.insertStacking(view, new ItemStack(Items.STONE), 100, null);

        assertEquals(neoInserted, bcInserted);
        assertEquals(slots(NeoForgeItemStorages.fromNeo(neo)), slots(view));
        assertEquals(List.of("stonex64", "stonex64", "stonex21", "stonex64"),
            slots(view), "1 + 14 into the occupied slots, then 64 and 21 into the empty ones");
    }

    @Test
    public void aBuildCraftStorageJoinsANeoForgeTransaction() {
        List<Integer> changes = new ArrayList<>();
        ItemHandlerSimple handler = new ItemHandlerSimple(1, (h, slot, before, after) -> changes.add(after.getCount()));
        ResourceHandler<ItemResource> neo = NeoForgeItemStorages.toNeo(handler);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(5, neo.insert(0, ItemResource.of(Items.APPLE), 5, tx));
            assertEquals(5, handler.getStack(0).getCount(), "visible inside the transaction");
        }
        assertTrue(handler.getStack(0).isEmpty(), "the NeoForge abort reverted the BuildCraft slot");
        assertEquals(List.of(), changes, "nothing was committed, so nothing was reported");
        try (Transaction tx = Transaction.openRoot()) {
            neo.insert(0, ItemResource.of(Items.APPLE), 5, tx);
            tx.commit();
        }
        assertEquals(5, handler.getStack(0).getCount());
        assertEquals(List.of(5), changes, "one root commit, one change callback");
        assertEquals(64, neo.getCapacityAsInt(0, ItemResource.of(Items.APPLE)));
    }

    @Test
    public void aNeoForgeHandlerSeenAsABuildCraftStorage() {
        ItemStacksResourceHandler neo = new ItemStacksResourceHandler(2);
        neo.set(0, ItemResource.of(Items.DIRT), 10);
        BCItemStorage view = NeoForgeItemStorages.fromNeo(neo);
        assertSame(neo, NeoForgeItemStorages.toNeo(view), "another mod's handler goes back out as itself");
        assertEquals(2, view.size());
        assertTrue(ItemStack.matches(new ItemStack(Items.DIRT, 10), view.getStack(0)));
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(4, view.extract(0, new ItemStack(Items.DIRT), 4, tx));
            assertEquals(0, view.extract(0, new ItemStack(Items.STONE), 4, tx), "a different item does not match");
        }
        assertEquals(10, neo.getAmountAsInt(0), "the BuildCraft abort reverted the NeoForge handler");
    }
    //?}

    //? if <1.21.10 {
    /*/^* 1.21.1: a classic handler can only simulate inside a transaction, so its view simulates and replays the real
     *  calls when the root commits, and an abort leaves it untouched. ^/
    @Test
    public void aClassicHandlerReplaysOnRootCommitOnly() {
        net.neoforged.neoforge.items.ItemStackHandler classic = new net.neoforged.neoforge.items.ItemStackHandler(2);
        classic.setStackInSlot(0, new ItemStack(Items.DIRT, 10));
        BCItemStorage view = NeoForgeItemStorages.fromNeo(classic);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(4, view.extract(0, new ItemStack(Items.DIRT), 4, tx));
            assertEquals(10, classic.getStackInSlot(0).getCount(), "nothing real happens before the commit");
        }
        assertEquals(10, classic.getStackInSlot(0).getCount(), "an abort replays nothing");
        assertEquals(30, ItemStorageUtil.insertStacking(view, new ItemStack(Items.STONE), 30, null));
        assertEquals(30, classic.getStackInSlot(1).getCount(), "the root commit replayed the insert");
    }

    /^* A classic handler's simulations cannot see what the open transaction already owes it, so each lookup of the
     *  handler used to be a fresh view and each insert was promised against the untouched slot: two promises of the
     *  same free space, and the replay lost one with only a log line. Every lookup is now one view, and a second
     *  call on a slot gets only what the first left. ^/
    @Test
    public void aClassicSlotIsNeverPromisedTwiceInOneTransaction() {
        net.neoforged.neoforge.items.ItemStackHandler classic = new net.neoforged.neoforge.items.ItemStackHandler(1);
        classic.setStackInSlot(0, new ItemStack(Items.DIRT, 54));
        BCItemStorage first = NeoForgeItemStorages.fromNeo(classic);
        BCItemStorage second = NeoForgeItemStorages.fromNeo(classic);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(10, first.insert(0, new ItemStack(Items.DIRT), 10, tx));
            assertEquals(0, second.insert(0, new ItemStack(Items.DIRT), 10, tx), "the 10 free were already promised");
            assertEquals(0, first.insert(0, new ItemStack(Items.DIRT), 10, tx));
            tx.commit();
        }
        assertEquals(64, classic.getStackInSlot(0).getCount());

        try (BCTransaction tx = BCTransaction.openRoot()) {
            assertEquals(40, first.extract(0, new ItemStack(Items.DIRT), 40, tx));
            assertEquals(24, second.extract(0, new ItemStack(Items.DIRT), 40, tx), "64 held, 40 already promised");
            assertEquals(0, first.insert(0, new ItemStack(Items.DIRT), 1, tx), "an extract is owed on the slot");
            tx.commit();
        }
        assertTrue(classic.getStackInSlot(0).isEmpty(), "every promise replayed in full");
    }

    @Test
    public void aBuildCraftStorageSeenAsAClassicHandler() {
        ItemHandlerSimple handler = simple(ItemStack.EMPTY);
        net.neoforged.neoforge.items.IItemHandler classic = NeoForgeItemStorages.toNeo(handler);
        assertEquals(2, classic.insertItem(0, new ItemStack(Items.APPLE, 66), true).getCount());
        assertTrue(handler.getStack(0).isEmpty(), "a simulation changes nothing");
        assertTrue(classic.insertItem(0, new ItemStack(Items.APPLE, 5), false).isEmpty());
        assertEquals(5, handler.getStack(0).getCount());
        assertEquals(3, classic.extractItem(0, 3, false).getCount());
        assertEquals(64, classic.getSlotLimit(0));
    }

    /^* 1.21.1 handed BuildCraft's plain inventories out as {@code IItemHandlerModifiable}, and NeoForge's own
     *  {@code SlotItemHandler.set} casts to it, so a foreign container wrapping a BuildCraft machine died with a
     *  ClassCastException once the platform adapter stopped being one. The wrapped (insert-only, extract-only,
     *  combined) handlers were never modifiable, and stay that way. ^/
    @Test
    public void aPlainBuildCraftInventoryIsAnIItemHandlerModifiable() {
        ItemHandlerSimple handler = simple(ItemStack.EMPTY, new ItemStack(Items.DIRT, 3));
        net.neoforged.neoforge.items.IItemHandler classic = NeoForgeItemStorages.toNeo(handler);
        assertTrue(classic instanceof net.neoforged.neoforge.items.IItemHandlerModifiable,
            "a plain BuildCraft inventory must be settable through the classic handler, as it was");
        ((net.neoforged.neoforge.items.IItemHandlerModifiable) classic).setStackInSlot(0, new ItemStack(Items.APPLE, 7));
        assertEquals(7, handler.getStack(0).getCount(), "the set reached the BuildCraft inventory");
        assertSame(handler, NeoForgeItemStorages.fromNeo(classic), "and it still unwraps to itself");

        assertTrue(!(NeoForgeItemStorages.toNeo(new buildcraft.lib.tile.item.WrappedItemHandlerExtract(handler))
            instanceof net.neoforged.neoforge.items.IItemHandlerModifiable), "a wrapped handler was never modifiable");
    }

    /^* The Quarry and the Mining Well exposed NeoForge's {@code EmptyItemHandler}, an {@code IItemHandlerModifiable}
     *  whose set does nothing; they now expose {@link ItemStorageUtil#EMPTY}, which must answer a foreign slot's set
     *  the same way instead of a ClassCastException. ^/
    @Test
    public void theQuarryAndMiningWellEmptyStorageTakesASlotSetAsANoOp() {
        net.neoforged.neoforge.items.IItemHandler classic = NeoForgeItemStorages.toNeo(ItemStorageUtil.EMPTY);
        assertTrue(classic instanceof net.neoforged.neoforge.items.IItemHandlerModifiable,
            "the empty storage must be modifiable through the classic handler, as EmptyItemHandler is");
        new net.neoforged.neoforge.items.SlotItemHandler(classic, 0, 0, 0).set(new ItemStack(Items.DIRT));
    }

    /^* 1.21.1's classic {@code getStackInSlot} on a BuildCraft inventory handed out the stack the inventory holds, not
     *  a copy, so a foreign slot that shrinks {@code getItem()} in place (vanilla's {@code Slot} pattern) changed the
     *  inventory. ^/
    @Test
    public void aClassicSlotOfABuildCraftInventoryIsTheLiveStack() {
        ItemHandlerSimple handler = simple(new ItemStack(Items.DIRT, 3));
        new net.neoforged.neoforge.items.SlotItemHandler(NeoForgeItemStorages.toNeo(handler), 0, 0, 0).getItem().shrink(1);
        assertEquals(2, handler.getStack(0).getCount(), "the shrink wrote through to the inventory, as it did");
    }

    /^* The stone engine's fuel slot was an anonymous {@code IItemHandlerModifiable}: {@code setStackInSlot} replaced
     *  the stack and marked the tile changed, and ignored any slot but 0. ^/
    @Test
    public void aSingleStackStorageIsAnIItemHandlerModifiableThatReportsTheSet() {
        ItemStack[] held = { ItemStack.EMPTY };
        int[] changed = { 0 };
        buildcraft.lib.transfer.SingleStackStorage fuel = new buildcraft.lib.transfer.SingleStackStorage() {
            @Override
            protected ItemStack getStack() {
                return held[0];
            }

            @Override
            protected void setStack(ItemStack stack) {
                held[0] = stack;
            }

            @Override
            protected void onRootCommit(ItemStack originalState) {
                changed[0]++;
            }
        };
        net.neoforged.neoforge.items.IItemHandler classic = NeoForgeItemStorages.toNeo(fuel);
        assertTrue(classic instanceof net.neoforged.neoforge.items.IItemHandlerModifiable);
        var modifiable = (net.neoforged.neoforge.items.IItemHandlerModifiable) classic;
        modifiable.setStackInSlot(0, new ItemStack(Items.COAL, 5));
        assertEquals(5, held[0].getCount());
        assertEquals(1, changed[0], "the owner is told once");
        modifiable.setStackInSlot(1, new ItemStack(Items.COAL, 9));
        assertEquals(5, held[0].getCount(), "a slot past the only one is ignored");
        assertEquals(1, changed[0]);

        modifiable.getStackInSlot(0).shrink(1);
        assertEquals(4, held[0].getCount(), "getStackInSlot is the held stack, not a copy");

        // Any other slot answered as an empty, refusing slot instead of throwing.
        assertTrue(classic.getStackInSlot(1).isEmpty());
        assertEquals(3, classic.insertItem(1, new ItemStack(Items.COAL, 3), false).getCount());
        assertTrue(classic.extractItem(1, 1, false).isEmpty());
        assertTrue(!classic.isItemValid(1, new ItemStack(Items.COAL)));
        assertEquals(net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE, classic.getSlotLimit(1));
        assertEquals(4, held[0].getCount(), "and left the fuel alone");
    }*/
    //?}
}
