/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

/**
 * An energy (FE/RF) storage, BuildCraft's view of an energy handler: BuildCraft's own buffers and converters implement
 * it, and {@link TransferAccess} hands out a view of any other mod's. Its shape is NeoForge's {@code EnergyHandler}
 * with {@code int} transfer amounts.
 *
 * <p>Changes happen only through {@link #insert} and {@link #extract}, inside a transaction; a storage that changes
 * state journals it with a {@link BCSnapshotJournal}, so an aborted transaction leaves it as it was. The only way to
 * know whether a storage takes energy is to try: the amount and capacity are hints.
 */
public interface BCEnergyStorage {
    /** The energy stored now. */
    long getAmountAsLong();

    /** The most energy this storage holds (a hint: it may read 0 for a storage that passes energy straight on). */
    long getCapacityAsLong();

    /** Inserts up to {@code amount}; returns how much went in. */
    int insert(int amount, BCTransactionContext transaction);

    /** Extracts up to {@code amount}; returns how much came out. */
    int extract(int amount, BCTransactionContext transaction);

    /**
     * Whether this storage ever takes energy in. Only the 1.21.1 classic handler type carries the flag
     * ({@code IEnergyStorage.canReceive}); a view of another mod's handler elsewhere answers true.
     */
    default boolean canInsert() {
        return true;
    }

    /** {@link #canInsert}'s twin for extraction ({@code IEnergyStorage.canExtract}). */
    default boolean canExtract() {
        return true;
    }
}
