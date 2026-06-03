/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Server-side helpers behind the JEI "+" recipe-transfer handlers for BuildCraft machines that use
 * <em>real</em> input inventories (Assembly Table, Distiller, Heat Exchanger), plus a client-side
 * inventory counter the handlers use to decide whether to enable the button.
 *
 * <p>The transfer is always a <em>move</em>: items inserted into the machine are debited from the
 * player's inventory in the same call, and the server only ever moves what the player actually has —
 * so a tampered client can at most rearrange its own items, never duplicate them.
 */
public final class JeiTransferUtil {
    private JeiTransferUtil() {}

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
     * Move up to {@code max} of {@code want} (by item + components) from the player's inventory into
     * {@code dest}, respecting {@code dest}'s per-slot capacity. Inserts into {@code dest} first,
     * then debits the player by exactly the amount inserted. Returns the number moved.
     */
    public static int moveMatchingToHandler(Inventory playerInv, ItemStack want, int max, ItemHandlerSimple dest) {
        if (want.isEmpty() || max <= 0) return 0;
        int toMove = Math.min(max, countMatching(playerInv, want));
        if (toMove <= 0) return 0;

        int remaining = toMove;
        for (int slot = 0; slot < dest.getSlots() && remaining > 0; slot++) {
            ItemStack attempt = want.copy();
            attempt.setCount(remaining);
            ItemStack leftover = dest.insertItem(slot, attempt, false);
            remaining -= (attempt.getCount() - leftover.getCount());
        }
        int moved = toMove - remaining;
        if (moved > 0) {
            removeFromInventory(playerInv, want, moved);
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

    private static void removeFromInventory(Inventory playerInv, ItemStack want, int count) {
        int remaining = count;
        for (int i = 0; i < playerInv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = playerInv.getItem(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, want)) continue;
            int take = Math.min(remaining, s.getCount());
            s.shrink(take);
            remaining -= take;
            if (s.isEmpty()) {
                playerInv.setItem(i, ItemStack.EMPTY);
            }
        }
        playerInv.setChanged();
    }
}
