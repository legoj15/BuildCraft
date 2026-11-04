/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransactionContext;

/** The FE/RF battery the FE engine and the MJ dynamo share: a loader-neutral {@link BCEnergyStorage} with NeoForge's
 * {@code SimpleEnergyHandler} semantics on every node (the 1.21.1 classic {@code EnergyStorage} it used to be there
 * agrees on every call BuildCraft and a classic caller make).
 *
 * <p>{@code (capacity, maxInsert, maxExtract)} limit it as they did; {@link #set} changes the amount at once and calls
 * {@link #onFeChanged()} straight away, while an insert or extract journals the amount and calls it when the root
 * transaction commits (never when it aborts). */
public class BCFeStorage implements BCEnergyStorage {
    private final int capacity;
    private final int maxInsert;
    private final int maxExtract;
    private int energy;

    private final BCSnapshotJournal<Integer> journal = new BCSnapshotJournal<>() {
        @Override
        protected Integer createSnapshot() {
            return energy;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            energy = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            if (energy != originalState) {
                onFeChanged();
            }
        }
    };

    public BCFeStorage(int capacity, int maxInsert, int maxExtract) {
        checkNonNegative(capacity);
        checkNonNegative(maxInsert);
        checkNonNegative(maxExtract);
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
    }

    private static void checkNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Expected value to be non-negative: " + value);
        }
    }

    /** Overwrites the stored amount; calls {@link #onFeChanged()} at once if it changed. */
    public void set(int amount) {
        checkNonNegative(amount);
        if (energy != amount) {
            energy = amount;
            onFeChanged();
        }
    }

    /** Called whenever the stored amount changes (via {@code set}, insert, or extract). Override to react. */
    protected void onFeChanged() {}

    @Override
    public long getAmountAsLong() {
        return energy;
    }

    @Override
    public long getCapacityAsLong() {
        return capacity;
    }

    @Override
    public int insert(int amount, BCTransactionContext transaction) {
        checkNonNegative(amount);
        int inserted = Math.min(capacity - energy, Math.min(amount, maxInsert));
        if (inserted > 0) {
            journal.updateSnapshots(transaction);
            energy += inserted;
            return inserted;
        }
        return 0;
    }

    @Override
    public int extract(int amount, BCTransactionContext transaction) {
        checkNonNegative(amount);
        int extracted = Math.min(energy, Math.min(amount, maxExtract));
        if (extracted > 0) {
            journal.updateSnapshots(transaction);
            energy -= extracted;
            return extracted;
        }
        return 0;
    }

    @Override
    public boolean canInsert() {
        return maxInsert > 0;
    }

    @Override
    public boolean canExtract() {
        return maxExtract > 0;
    }
}
