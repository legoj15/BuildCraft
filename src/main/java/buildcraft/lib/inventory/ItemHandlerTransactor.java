/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.inventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;

import buildcraft.lib.misc.StackUtil;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.TransferAccess;

/** An {@link buildcraft.api.inventory.IItemTransactor} over a block's or an entity's item storage, the inventory
 *  surface pipes use (vanilla containers through the loader's wrapper, modded storage and BuildCraft's own machines
 *  directly). {@link InventoryWrapper} covers only vanilla {@code Container}s, which most modded
 *  inventories are not. The handler decides what may leave or enter each slot, so insert-only and extract-only
 *  slots behave here exactly as they do for a pipe. */
public final class ItemHandlerTransactor extends AbstractInvItemTransactor {
    /** The transactor over {@code entity}'s item storage (the whole inventory, not an automation view), or null
     *  if the entity has none. NeoForge registers it for the vanilla chest and hopper minecarts and boats, and a
     *  modded entity that carries items offers them this way. */
    @Nullable
    public static ItemHandlerTransactor ofEntity(@Nonnull Entity entity) {
        BCItemStorage handler = TransferAccess.findItem(entity);
        return handler == null ? null : new ItemHandlerTransactor(handler);
    }

    private final BCItemStorage handler;

    public ItemHandlerTransactor(BCItemStorage handler) {
        this.handler = handler;
    }

    @Override
    @Nonnull
    protected ItemStack insert(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return StackUtil.EMPTY;
        }
        int inserted;
        try (BCTransaction tx = BCTransaction.openRoot()) {
            inserted = handler.insert(slot, stack, stack.getCount(), tx);
            if (!simulate) {
                tx.commit();
            }
        }
        if (inserted >= stack.getCount()) {
            return StackUtil.EMPTY;
        }
        return stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    @Nonnull
    protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
        ItemStack current = slotContents(slot);
        if (current.isEmpty() || current.getCount() < min || !filter.matches(current.copy())) {
            return StackUtil.EMPTY;
        }
        int wanted = Math.min(current.getCount(), max);
        int extracted;
        try (BCTransaction tx = BCTransaction.openRoot()) {
            extracted = handler.extract(slot, current, wanted, tx);
            if (extracted < min) {
                return StackUtil.EMPTY;
            }
            if (!simulate) {
                tx.commit();
            }
        }
        return current.copyWithCount(extracted);
    }

    @Override
    protected int size() {
        return handler.size();
    }

    @Override
    protected boolean isEmpty(int slot) {
        return slotContents(slot).isEmpty();
    }

    @Override
    @Nonnull
    protected ItemStack slotContents(int slot) {
        return handler.getStack(slot);
    }
}
