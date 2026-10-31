/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IStackFilter;
import buildcraft.lib.misc.StackUtil;

/** {@link AbstractInvItemTransactor#drainInto} is what the stripes pipe empties a minecart through. Every stack it
 *  hands on must really be out of its slot (a stack sent while still in the cart is duplicated), and everything a slot
 *  did give up must be handed on (or it is lost with the cart). The slots here misbehave the ways a modded inventory
 *  can; the real capability and container paths are covered by the stripes minecart game tests. */
public class AbstractInvItemTransactorDrainTest extends VanillaSetupBaseTester {

    /** A bare slot array whose slots can be told to lie, to refuse, or to let only a few items out at a time. */
    private static final class Slots extends AbstractInvItemTransactor {
        final ItemStack[] stacks;
        final boolean[] keepsWhatItReturns;
        final boolean[] refusesExtraction;
        final int[] mostPerExtraction;

        Slots(ItemStack... stacks) {
            this.stacks = stacks;
            keepsWhatItReturns = new boolean[stacks.length];
            refusesExtraction = new boolean[stacks.length];
            mostPerExtraction = new int[stacks.length];
        }

        @Override
        @Nonnull
        protected ItemStack insert(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        @Nonnull
        protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
            ItemStack current = stacks[slot];
            if (current.isEmpty() || refusesExtraction[slot] || !filter.matches(current.copy())) {
                return StackUtil.EMPTY;
            }
            int amount = Math.min(current.getCount(), max);
            if (mostPerExtraction[slot] > 0) {
                amount = Math.min(amount, mostPerExtraction[slot]);
            }
            if (amount < min) {
                return StackUtil.EMPTY;
            }
            if (!simulate && !keepsWhatItReturns[slot]) {
                stacks[slot] = amount == current.getCount() ? StackUtil.EMPTY : current.copyWithCount(current.getCount() - amount);
            }
            return current.copyWithCount(amount);
        }

        @Override
        protected int size() {
            return stacks.length;
        }

        @Override
        protected boolean isEmpty(int slot) {
            return stacks[slot].isEmpty();
        }

        @Override
        @Nonnull
        protected ItemStack slotContents(int slot) {
            return stacks[slot];
        }
    }

    private static int total(List<ItemStack> sent, Item item) {
        int found = 0;
        for (ItemStack stack : sent) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    @Test
    public void everySlotGoesOutAndTheInventoryIsEmpty() {
        Slots slots = new Slots(new ItemStack(Items.DIRT, 10), ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 2));
        List<ItemStack> sent = new ArrayList<>();

        Assertions.assertTrue(slots.drainInto(sent::add), "nothing is left, so the inventory reports it is empty");

        Assertions.assertEquals(10, total(sent, Items.DIRT));
        Assertions.assertEquals(2, total(sent, Items.DIAMOND));
        Assertions.assertTrue(slots.stacks[0].isEmpty() && slots.stacks[2].isEmpty());
    }

    @Test
    public void aSlotThatKeepsWhatItReturnsSendsNothingAndIsReportedAsStillFull() {
        Slots slots = new Slots(new ItemStack(Items.DIRT, 10), new ItemStack(Items.DIAMOND, 2));
        slots.keepsWhatItReturns[1] = true;
        List<ItemStack> sent = new ArrayList<>();

        Assertions.assertFalse(slots.drainInto(sent::add), "the diamond is still in the inventory");

        Assertions.assertEquals(10, total(sent, Items.DIRT), "the honest slot is still emptied");
        Assertions.assertEquals(0, total(sent, Items.DIAMOND), "the diamond never left its slot, so sending it duplicates it");
        Assertions.assertEquals(2, slots.stacks[1].getCount());
    }

    @Test
    public void aSlotThatRefusesExtractionSendsNothingAndIsReportedAsStillFull() {
        Slots slots = new Slots(new ItemStack(Items.DIRT, 10), new ItemStack(Items.DIAMOND, 2));
        slots.refusesExtraction[1] = true;
        List<ItemStack> sent = new ArrayList<>();

        Assertions.assertFalse(slots.drainInto(sent::add));

        Assertions.assertEquals(10, total(sent, Items.DIRT));
        Assertions.assertEquals(0, total(sent, Items.DIAMOND));
        Assertions.assertEquals(2, slots.stacks[1].getCount());
    }

    @Test
    public void aSlotThatLetsOnlyPartOutSendsThatPartAndKeepsTheRest() {
        Slots slots = new Slots(new ItemStack(Items.DIRT, 8));
        slots.mostPerExtraction[0] = 3;
        List<ItemStack> sent = new ArrayList<>();

        Assertions.assertFalse(slots.drainInto(sent::add), "five dirt are still in the slot");

        Assertions.assertEquals(3, total(sent, Items.DIRT), "what did come out must not be lost");
        Assertions.assertEquals(5, slots.stacks[0].getCount());
    }
}
