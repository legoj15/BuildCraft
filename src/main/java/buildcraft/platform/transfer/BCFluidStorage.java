/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import buildcraft.api.fluids.BCFluidStack;

/**
 * An indexed fluid storage, BuildCraft's view of a set of tanks: BuildCraft's own tanks implement it, and
 * {@link TransferAccess} hands out a view of any other mod's. Its shape is NeoForge's
 * {@code ResourceHandler<FluidResource>} with {@code int} millibuckets and {@link BCFluidStack}s standing in for
 * resources: a "resource" argument names a fluid plus its components, and its amount is ignored.
 *
 * <p>Changes happen only through {@link #insert} and {@link #extract}, inside a transaction; a storage that changes
 * state journals it with a {@link BCSnapshotJournal}, so an aborted transaction leaves it as it was.
 */
public interface BCFluidStorage {
    /** The number of indices (tanks). */
    int size();

    /** A copy of the fluid at {@code index}, the caller's to keep; {@link BCFluidStack#EMPTY} if the tank is empty. */
    BCFluidStack getStack(int index);

    /** The amount at {@code index}, in millibuckets. */
    default int getAmount(int index) {
        return getStack(index).getAmount();
    }

    /**
     * How many millibuckets of {@code resource} the index could hold in total; for an empty resource, the tank's
     * capacity. A resource the index refuses ({@link #isValid}) may report 0.
     */
    int getCapacity(int index, BCFluidStack resource);

    /** Whether {@code resource} may ever go into {@code index} (ignoring how full it is). */
    boolean isValid(int index, BCFluidStack resource);

    /** Inserts up to {@code amount} of {@code resource} into {@code index}; returns how much went in. */
    int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction);

    /** Extracts up to {@code amount} of {@code resource} from {@code index}; returns how much came out. */
    int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction);

    /**
     * Inserts up to {@code amount} of {@code resource} across the indices, in index order (NeoForge's
     * {@code ResourceHandler.insert(resource, amount, transaction)}).
     *
     * @throws IllegalArgumentException for an empty resource or a negative amount.
     */
    default int insert(BCFluidStack resource, int amount, BCTransactionContext transaction) {
        checkNonEmptyNonNegative(resource, amount);
        int inserted = 0;
        int size = size();
        for (int index = 0; index < size; index++) {
            inserted += insert(index, resource, amount - inserted, transaction);
            if (inserted == amount) {
                break;
            }
        }
        return inserted;
    }

    /**
     * Extracts up to {@code amount} of {@code resource} across the indices, in index order (NeoForge's
     * {@code ResourceHandler.extract(resource, amount, transaction)}).
     *
     * @throws IllegalArgumentException for an empty resource or a negative amount.
     */
    default int extract(BCFluidStack resource, int amount, BCTransactionContext transaction) {
        checkNonEmptyNonNegative(resource, amount);
        int extracted = 0;
        int size = size();
        for (int index = 0; index < size; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
            if (extracted == amount) {
                break;
            }
        }
        return extracted;
    }

    /** The precondition NeoForge's {@code TransferPreconditions.checkNonEmptyNonNegative} enforces. */
    static void checkNonEmptyNonNegative(BCFluidStack resource, int amount) {
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Expected resource to be non-empty: " + resource);
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Expected amount to be non-negative: " + amount);
        }
    }
}
