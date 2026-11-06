/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import javax.annotation.Nullable;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.energy.IEnergyStorage;

import buildcraft.api.core.BCLog;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
*///?}

import buildcraft.lib.transfer.AdapterCache;
import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * The two-way adapter between {@link BCEnergyStorage} and NeoForge's energy handler type: {@code EnergyHandler} on
 * 1.21.10+, the classic {@code IEnergyStorage} on 1.21.1. Each direction unwraps the other's adapter, so a BuildCraft
 * storage that went out as a capability and came back through a lookup is the same object again. Each direction
 * hands out one adapter per wrapped object while that adapter is held ({@link AdapterCache}), so a lookup made while
 * an earlier view of the same handler is still in use gives that view.
 *
 * <p><b>1.21.1.</b> As {@link NeoForgeItemStorages}: a BuildCraft storage seen as an {@code IEnergyStorage} runs each
 * call in a transaction of its own ({@link LegacyTransactions#openInCurrent}), committed unless the call simulates; a
 * classic handler seen as a {@link BCEnergyStorage} answers each insert and extract by simulating and replays the real
 * calls, in order, when the root transaction commits (none if it aborts).
 */
public final class NeoForgeEnergyStorages {
    private NeoForgeEnergyStorages() {}

    //? if >=1.21.10 {
    private static final AdapterCache<BCEnergyStorage, ToNeo> TO_NEO = new AdapterCache<>(ToNeo::new);
    private static final AdapterCache<EnergyHandler, FromNeo> FROM_NEO = new AdapterCache<>(FromNeo::new);

    /** The NeoForge handler for a BuildCraft storage (null stays null). */
    @Nullable
    public static EnergyHandler toNeo(@Nullable BCEnergyStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromNeo view) {
            return view.handler;
        }
        return TO_NEO.get(storage);
    }

    /** The BuildCraft view of a NeoForge handler (null stays null). */
    @Nullable
    public static BCEnergyStorage fromNeo(@Nullable EnergyHandler handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToNeo adapter) {
            return adapter.storage;
        }
        return FROM_NEO.get(handler);
    }

    private record ToNeo(BCEnergyStorage storage) implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return storage.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return storage.insert(amount, NeoForgeTransfer.wrap(transaction));
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return storage.extract(amount, NeoForgeTransfer.wrap(transaction));
        }
    }

    private record FromNeo(EnergyHandler handler) implements BCEnergyStorage {
        @Override
        public long getAmountAsLong() {
            return handler.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return handler.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, BCTransactionContext transaction) {
            return handler.insert(amount, NeoForgeTransfer.unwrap(transaction));
        }

        @Override
        public int extract(int amount, BCTransactionContext transaction) {
            return handler.extract(amount, NeoForgeTransfer.unwrap(transaction));
        }
    }
    //?} else {
    /*private static final AdapterCache<BCEnergyStorage, ToClassic> TO_CLASSIC = new AdapterCache<>(ToClassic::new);
    private static final AdapterCache<IEnergyStorage, FromClassic> FROM_CLASSIC = new AdapterCache<>(FromClassic::new);

    @Nullable
    public static IEnergyStorage toNeo(@Nullable BCEnergyStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromClassic view) {
            return view.handler;
        }
        return TO_CLASSIC.get(storage);
    }

    @Nullable
    public static BCEnergyStorage fromNeo(@Nullable IEnergyStorage handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToClassic adapter) {
            return adapter.storage;
        }
        return FROM_CLASSIC.get(handler);
    }

    private record ToClassic(BCEnergyStorage storage) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (toReceive <= 0) {
                return 0;
            }
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                int received = storage.insert(toReceive, tx);
                if (!simulate) {
                    tx.commit();
                }
                return received;
            }
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (toExtract <= 0) {
                return 0;
            }
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                int extracted = storage.extract(toExtract, tx);
                if (!simulate) {
                    tx.commit();
                }
                return extracted;
            }
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, storage.getAmountAsLong());
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, storage.getCapacityAsLong());
        }

        @Override
        public boolean canExtract() {
            return storage.canExtract();
        }

        @Override
        public boolean canReceive() {
            return storage.canInsert();
        }
    }

    private static final class FromClassic implements BCEnergyStorage {
        final IEnergyStorage handler;
        private final List<Runnable> pending = new ArrayList<>();
        private final BCSnapshotJournal<Integer> journal = new BCSnapshotJournal<>() {
            @Override
            protected Integer createSnapshot() {
                return pending.size();
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                pending.subList(snapshot, pending.size()).clear();
            }

            @Override
            protected void onRootCommit(Integer originalState) {
                List<Runnable> replay = new ArrayList<>(pending);
                pending.clear();
                replay.forEach(Runnable::run);
            }
        };

        FromClassic(IEnergyStorage handler) {
            this.handler = handler;
        }

        @Override
        public long getAmountAsLong() {
            return handler.getEnergyStored();
        }

        @Override
        public long getCapacityAsLong() {
            return handler.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, BCTransactionContext transaction) {
            if (amount <= 0) {
                return 0;
            }
            int received = handler.receiveEnergy(amount, true);
            if (received > 0) {
                journal.updateSnapshots(transaction);
                pending.add(() -> {
                    int real = handler.receiveEnergy(received, false);
                    if (real != received) {
                        BCLog.logger.warn("[platform] {} took {} of a simulated insert of {} FE", handler, real, received);
                    }
                });
            }
            return received;
        }

        @Override
        public int extract(int amount, BCTransactionContext transaction) {
            if (amount <= 0) {
                return 0;
            }
            int extracted = handler.extractEnergy(amount, true);
            if (extracted > 0) {
                journal.updateSnapshots(transaction);
                pending.add(() -> {
                    int real = handler.extractEnergy(extracted, false);
                    if (real != extracted) {
                        BCLog.logger.warn("[platform] {} gave {} of a simulated extract of {} FE", handler, real, extracted);
                    }
                });
            }
            return extracted;
        }

        @Override
        public boolean canInsert() {
            return handler.canReceive();
        }

        @Override
        public boolean canExtract() {
            return handler.canExtract();
        }
    }
    *///?}
}
//?}
