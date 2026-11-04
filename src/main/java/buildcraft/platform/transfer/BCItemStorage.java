/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import net.minecraft.world.item.ItemStack;

/**
 * An indexed item storage, BuildCraft's view of an inventory: BuildCraft's own handlers implement it, and
 * {@link TransferAccess} hands out a view of any other mod's inventory. Its shape is NeoForge's
 * {@code ResourceHandler<ItemResource>} with {@code int} amounts and {@link ItemStack}s standing in for resources: a
 * "resource" argument names an item plus its components, and its count is ignored.
 *
 * <p>Changes happen only through {@link #insert} and {@link #extract}, inside a transaction; a storage that changes
 * state journals it with a {@link BCSnapshotJournal}, so an aborted transaction leaves it as it was.
 */
public interface BCItemStorage {
    /** The number of indices (slots). */
    int size();

    /** A copy of the stack at {@code index}, the caller's to keep; {@link ItemStack#EMPTY} if the index is empty. */
    ItemStack getStack(int index);

    /** How many of {@code resource} the index could hold in total; for an empty resource, an estimate. */
    int getCapacity(int index, ItemStack resource);

    /** Whether {@code resource} may ever go into {@code index} (ignoring how full it is). */
    boolean isValid(int index, ItemStack resource);

    /** Inserts up to {@code amount} of {@code resource} into {@code index}; returns how many went in. */
    int insert(int index, ItemStack resource, int amount, BCTransactionContext transaction);

    /** Extracts up to {@code amount} of {@code resource} from {@code index}; returns how many came out. */
    int extract(int index, ItemStack resource, int amount, BCTransactionContext transaction);
}
