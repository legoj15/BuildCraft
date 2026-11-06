/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.transfer;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

/** Helpers over {@link BCFluidStorage}, the loader-neutral counterparts of NeoForge's {@code ResourceHandlerUtil}. */
public final class FluidStorageUtil {
    private FluidStorageUtil() {}

    /**
     * Moves up to {@code amount} millibuckets from {@code from} to {@code to}, index by index of {@code from}, of
     * every fluid {@code filter} accepts (it never sees an empty stack). A port of NeoForge's
     * {@code ResourceHandlerUtil.move}: each index's extract is simulated first, the insert goes through
     * {@link BCFluidStorage#insert(BCFluidStack, int, BCTransactionContext)}, and a transfer whose real extract falls
     * short of what was inserted is rolled back. With a null {@code transaction} it runs in a root transaction of its
     * own and commits; otherwise it nests in the given one and commits into it (the caller decides).
     *
     * @return how much moved; 0 for a null storage or a zero amount.
     * @throws IllegalArgumentException for a negative amount.
     */
    public static int move(@Nullable BCFluidStorage from, @Nullable BCFluidStorage to, Predicate<BCFluidStack> filter,
        int amount, @Nullable BCTransactionContext transaction) {
        if (amount < 0) {
            throw new IllegalArgumentException("Expected amount to be non-negative: " + amount);
        }
        if (from == null || to == null || amount == 0) {
            return 0;
        }
        try (BCTransaction sub = BCTransaction.open(transaction)) {
            int totalMoved = 0;
            int size = from.size();
            for (int index = 0; index < size; ++index) {
                BCFluidStack resource = from.getStack(index);
                if (resource.isEmpty() || !filter.test(resource)) {
                    continue;
                }
                int maxExtracted;
                try (BCTransaction simulated = sub.openNested()) {
                    maxExtracted = from.extract(index, resource, amount - totalMoved, simulated);
                }
                if (maxExtracted == 0) {
                    continue;
                }
                try (BCTransaction transfer = sub.openNested()) {
                    int inserted = to.insert(resource, maxExtracted, transfer);
                    // Extract what went in, or roll back if the source will not give exactly that much.
                    if (inserted != from.extract(index, resource, inserted, transfer)) {
                        continue;
                    }
                    totalMoved += inserted;
                    transfer.commit();
                    if (totalMoved >= amount) {
                        break;
                    }
                }
            }
            sub.commit();
            return totalMoved;
        }
    }

    /**
     * {@link #move} in a root transaction of its own, committed when anything moved.
     *
     * @return the fluid that moved (the first matching fluid of {@code from}, with the moved amount), or null if
     *         nothing did.
     */
    @Nullable
    public static BCFluidStack moveAndReport(@Nullable BCFluidStorage from, @Nullable BCFluidStorage to, int max) {
        if (from == null || to == null) {
            return null;
        }
        BCFluidStack firstAvailable = BCFluidStack.EMPTY;
        for (int i = 0; i < from.size(); i++) {
            BCFluidStack stack = from.getStack(i);
            if (!stack.isEmpty()) {
                firstAvailable = stack;
                break;
            }
        }
        if (firstAvailable.isEmpty()) {
            return null;
        }
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int moved = move(from, to, r -> true, max, tx);
            if (moved > 0) {
                tx.commit();
                return firstAvailable.copyWithAmount(moved);
            }
        }
        return null;
    }

    /** A view of {@code storage} that refuses every insert (a drain-only tank face). */
    public static BCFluidStorage extractOnly(BCFluidStorage storage) {
        return new Limited(storage, false, true);
    }

    /** A view of {@code storage} that refuses every extract (a fill-only tank face). */
    public static BCFluidStorage insertOnly(BCFluidStorage storage) {
        return new Limited(storage, true, false);
    }

    private record Limited(BCFluidStorage storage, boolean canInsert, boolean canExtract) implements BCFluidStorage {
        @Override
        public int size() {
            return storage.size();
        }

        @Override
        public BCFluidStack getStack(int index) {
            return storage.getStack(index);
        }

        @Override
        public int getAmount(int index) {
            return storage.getAmount(index);
        }

        @Override
        public int getCapacity(int index, BCFluidStack resource) {
            return storage.getCapacity(index, resource);
        }

        @Override
        public boolean isValid(int index, BCFluidStack resource) {
            return storage.isValid(index, resource);
        }

        @Override
        public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            return canInsert ? storage.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            return canExtract ? storage.extract(index, resource, amount, transaction) : 0;
        }

        // The resource-wide calls go to the storage's own, which a storage may answer itself (a classic handler's
        // view makes each one a single handler call).
        @Override
        public int insert(BCFluidStack resource, int amount, BCTransactionContext transaction) {
            BCFluidStorage.checkNonEmptyNonNegative(resource, amount);
            return canInsert ? storage.insert(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(BCFluidStack resource, int amount, BCTransactionContext transaction) {
            BCFluidStorage.checkNonEmptyNonNegative(resource, amount);
            return canExtract ? storage.extract(resource, amount, transaction) : 0;
        }
    }

    /** Whether every index is empty. */
    public static boolean isEmpty(BCFluidStorage storage) {
        for (int i = 0; i < storage.size(); i++) {
            if (!storage.getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
