/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.robotics.tile.TileRequester;

/** The Requester's request/fulfilment arithmetic — 7.1.x {@code TileRequester} truth table, pinned at
 *  14 cases. The tile is constructed headless (no level): every method under test reads only its two
 *  item handlers. */
public class TileRequesterMathTest extends VanillaSetupBaseTester {

    private static TileRequester requester;

    @BeforeAll
    static void createRequester() {
        // The 26.x BlockEntity ctor validates the state against the block's BE — stone would throw.
        BlockState state = BCRoboticsBlocks.REQUESTER.get().defaultBlockState();
        requester = new TileRequester(BlockPos.ZERO, state);
    }

    private static void setTemplate(int slot, ItemStack stack) {
        requester.invRequests.setStackInSlot(slot, stack);
    }

    // ── getRequest: what is still owed ────────────────────────────────────

    @Test
    public void requestIsEmptyWithoutTemplate() {
        Assertions.assertTrue(requester.getRequest(0).isEmpty(),
                "a slot with no template requests nothing");
    }

    @Test
    public void requestIsFullQuantityWhenNothingDelivered() {
        setTemplate(1, new ItemStack(Items.DIAMOND, 5));
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.DIAMOND, 5), requester.getRequest(1)),
                "an undelivered template requests its full count");
    }

    @Test
    public void requestIsRemainderWhenPartiallyDelivered() {
        setTemplate(2, new ItemStack(Items.DIAMOND, 5));
        requester.invDeliveries.setStackInSlot(2, new ItemStack(Items.DIAMOND, 2));
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.DIAMOND, 3), requester.getRequest(2)),
                "the request is only the shortfall");
    }

    @Test
    public void requestIsEmptyWhenFulfilled() {
        setTemplate(3, new ItemStack(Items.DIAMOND, 5));
        requester.invDeliveries.setStackInSlot(3, new ItemStack(Items.DIAMOND, 5));
        Assertions.assertTrue(requester.getRequest(3).isEmpty(),
                "a satisfied request asks for nothing more");
    }

    @Test
    public void requestIsEmptyWhenDeliveryDoesNotMatchTemplate() {
        setTemplate(4, new ItemStack(Items.DIAMOND, 5));
        requester.invDeliveries.setStackInSlot(4, new ItemStack(Items.DIRT, 64));
        Assertions.assertTrue(requester.getRequest(4).isEmpty(),
                "a wrong-item delivery cannot count toward the request — the slot is spoiled until cleared");
    }

    // ── offerItem: the delivery hand-off ──────────────────────────────────

    @Test
    public void offerRefusesSlotWithoutTemplate() {
        ItemStack offered = new ItemStack(Items.DIAMOND, 5);
        Assertions.assertSame(offered, requester.offerItem(5, offered),
                "no template means the requester wants nothing — the whole stack comes back");
    }

    @Test
    public void offerConsumesMatchingStackUpToTemplateCount() {
        setTemplate(6, new ItemStack(Items.DIAMOND, 5));
        ItemStack leftover = requester.offerItem(6, new ItemStack(Items.DIAMOND, 5));
        Assertions.assertTrue(leftover.isEmpty(), "an exact-count delivery is fully consumed");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.DIAMOND, 5), requester.invDeliveries.getStackInSlot(6)),
                "the delivery lands in the paired slot");
    }

    @Test
    public void offerReturnsExcessOverTemplateCount() {
        setTemplate(7, new ItemStack(Items.DIAMOND, 3));
        ItemStack offered = new ItemStack(Items.DIAMOND, 5);
        ItemStack leftover = requester.offerItem(7, offered);
        Assertions.assertEquals(2, leftover.getCount(),
                "the requester keeps only what it asked for; the excess comes back");
        Assertions.assertEquals(3, requester.invDeliveries.getStackInSlot(7).getCount(),
                "exactly the template count is stored");
    }

    @Test
    public void offerRefusesMismatchedItem() {
        setTemplate(8, new ItemStack(Items.DIAMOND, 5));
        ItemStack offered = new ItemStack(Items.DIRT, 5);
        Assertions.assertSame(offered, requester.offerItem(8, offered),
                "a wrong item is never accepted, even into an empty slot");
        Assertions.assertTrue(requester.invDeliveries.getStackInSlot(8).isEmpty(),
                "the refused offer left nothing behind");
    }

    @Test
    public void offerMergesIntoPartialDeliveryCappedAtTemplate() {
        setTemplate(9, new ItemStack(Items.DIAMOND, 5));
        requester.invDeliveries.setStackInSlot(9, new ItemStack(Items.DIAMOND, 2));
        ItemStack leftover = requester.offerItem(9, new ItemStack(Items.DIAMOND, 4));
        Assertions.assertEquals(1, leftover.getCount(), "2 + 4 against a request of 5 leaves 1 over");
        Assertions.assertEquals(5, requester.invDeliveries.getStackInSlot(9).getCount(),
                "the slot tops out at the requested count");
    }

    @Test
    public void offerRefusesMismatchAgainstExistingDelivery() {
        setTemplate(10, new ItemStack(Items.DIAMOND, 5));
        requester.invDeliveries.setStackInSlot(10, new ItemStack(Items.DIAMOND, 2));
        ItemStack offered = new ItemStack(Items.DIRT, 3);
        Assertions.assertSame(offered, requester.offerItem(10, offered),
                "an offer that matches neither the slot's delivery nor its template is refused whole");
    }

    // ── isFulfilled ───────────────────────────────────────────────────────

    @Test
    public void fulfilmentRequiresMatchingItemAndEnoughCount() {
        setTemplate(11, new ItemStack(Items.DIAMOND, 5));
        Assertions.assertFalse(requester.isFulfilled(11), "empty delivery is not fulfilled");

        requester.invDeliveries.setStackInSlot(11, new ItemStack(Items.DIAMOND, 4));
        Assertions.assertFalse(requester.isFulfilled(11), "one short is not fulfilled");

        requester.invDeliveries.setStackInSlot(11, new ItemStack(Items.DIAMOND, 5));
        Assertions.assertTrue(requester.isFulfilled(11), "count met is fulfilled");

        Assertions.assertTrue(requester.isFulfilled(14), "a slot with no template reads as fulfilled");
    }

    // ── isItemValidForSlot (the insertion checker) ────────────────────────

    @Test
    public void insertionIsValidOnlyAgainstAMatchingTemplate() {
        setTemplate(13, new ItemStack(Items.DIAMOND, 5));
        Assertions.assertFalse(requester.invDeliveries.canSet(13, new ItemStack(Items.DIRT, 1)),
                "an item no slot asked for cannot be inserted");
        Assertions.assertFalse(requester.invDeliveries.canSet(0, new ItemStack(Items.DIAMOND, 1)),
                "a template-less slot accepts nothing");
        Assertions.assertTrue(requester.invDeliveries.canSet(13, new ItemStack(Items.DIAMOND, 1)),
                "the templated item is insertable (pipes and players both)");
    }
}
