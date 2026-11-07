/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

/**
 * The indexed shape {@link BCItemStorage} and {@link BCFluidStorage} share, over their resource type {@code R} (an
 * {@code ItemStack} or a {@code BCFluidStack}, whose amount is ignored where it names a resource). It exists so a loader
 * adapter can be written once over both; BuildCraft code names the item or fluid storage, never this.
 */
public interface BCStorage<R> {
    /** The number of indices. */
    int size();

    /** A copy of the contents of {@code index}, the caller's to keep; the empty resource if the index is empty. */
    R getStack(int index);

    /** How much of {@code resource} the index could hold in total. */
    int getCapacity(int index, R resource);

    /** Whether {@code resource} may ever go into {@code index} (ignoring how full it is). */
    boolean isValid(int index, R resource);

    /** Inserts up to {@code amount} of {@code resource} into {@code index}; returns how much went in. */
    int insert(int index, R resource, int amount, BCTransactionContext transaction);

    /** Extracts up to {@code amount} of {@code resource} from {@code index}; returns how much came out. */
    int extract(int index, R resource, int amount, BCTransactionContext transaction);
}
