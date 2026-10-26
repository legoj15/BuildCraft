/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.viewer;

import java.util.function.Predicate;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Server-side helpers behind the JEI and REI "+" recipe-transfer handlers for BuildCraft machines that use
 * <em>real</em> input inventories (Assembly Table, Distiller, Heat Exchanger), plus a client-side
 * inventory counter the handlers use to decide whether to enable the button.
 *
 * <p>The transfer is always a <em>move</em>: items inserted into the machine are debited from the
 * player's inventory in the same call, and the server only ever moves what the player actually has —
 * so a tampered client can at most rearrange its own items, never duplicate them.
 */
public final class TransferUtil {
    private TransferUtil() {}

    /** Total count of stacks in {@code playerInv} matching {@code want} by item + components. */
    public static int countMatching(Inventory playerInv, ItemStack want) {
        if (want.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < playerInv.getContainerSize(); i++) {
            ItemStack s = playerInv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, want)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /**
     * Move up to {@code max} items from the stacks of {@code playerInv} that {@code accepts} into {@code dest},
     * respecting {@code dest}'s per-slot capacity. Walks the inventory in slot order; for each accepted stack it
     * inserts into {@code dest} first, then debits that stack by exactly the amount inserted, so what the player
     * loses is what the machine gained. Returns the number moved.
     *
     * <p>{@code accepts} is the machine's own test (an ingredient's {@code test}, an exact stack match, ...), so a
     * transfer takes the stacks the machine would have taken if the player had placed them by hand.
     */
    public static int moveMatchingToHandler(Container playerInv, Predicate<ItemStack> accepts, int max,
            ItemHandlerSimple dest) {
        if (max <= 0) return 0;
        int moved = 0;
        for (int i = 0; i < playerInv.getContainerSize() && moved < max; i++) {
            ItemStack held = playerInv.getItem(i);
            if (held.isEmpty() || !accepts.test(held)) continue;

            int offered = Math.min(max - moved, held.getCount());
            ItemStack leftover = held.copyWithCount(offered);
            for (int slot = 0; slot < dest.getSlots() && !leftover.isEmpty(); slot++) {
                leftover = dest.insertItem(slot, leftover, false);
            }
            int inserted = offered - leftover.getCount();
            if (inserted > 0) {
                held.shrink(inserted);
                if (held.isEmpty()) {
                    playerInv.setItem(i, ItemStack.EMPTY);
                }
                moved += inserted;
            }
        }
        if (moved > 0) {
            playerInv.setChanged();
        }
        return moved;
    }

    /**
     * Move a single filled {@code bucket} from the player's inventory into {@code dest} at
     * {@code slot} (a single-bucket container slot), if the slot is empty and the player has one.
     * Returns true if a bucket was moved.
     */
    public static boolean moveBucketToSlot(Inventory playerInv, Item bucket, ItemHandlerSimple dest, int slot) {
        if (bucket == null || bucket == Items.AIR) return false;
        if (slot < 0 || slot >= dest.getSlots()) return false;
        if (!dest.getStackInSlot(slot).isEmpty()) return false;

        for (int i = 0; i < playerInv.getContainerSize(); i++) {
            ItemStack s = playerInv.getItem(i);
            if (s.isEmpty() || !s.is(bucket)) continue;

            ItemStack one = s.copy();
            one.setCount(1);
            ItemStack leftover = dest.insertItem(slot, one, false);
            if (leftover.isEmpty()) {
                s.shrink(1);
                if (s.isEmpty()) {
                    playerInv.setItem(i, ItemStack.EMPTY);
                }
                playerInv.setChanged();
                return true;
            }
            return false; // couldn't insert (slot capacity) — nothing taken
        }
        return false;
    }
}
