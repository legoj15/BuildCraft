/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraftforge.energy.IEnergyStorage;

import buildcraft.api.core.BCLog;
import buildcraft.lib.transfer.AdapterCache;
import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

/^*
 * The two-way adapter between {@link BCEnergyStorage} and Forge's {@code IEnergyStorage}, the Forge twin of
 * {@code NeoForgeEnergyStorages} on 1.21.1 (Forge's handler has NeoForge 21.1's exact method set, so the classic arm
 * ports by package rename). Each direction unwraps the other's adapter, so a BuildCraft storage that went out as a
 * capability and came back through a lookup is the same object again, and each hands out one adapter per wrapped
 * object while it is held ({@link AdapterCache}).
 *
 * <p>A BuildCraft storage seen as an {@code IEnergyStorage} runs each call in a transaction of its own
 * ({@link LegacyTransactions#openInCurrent}), committed unless the call simulates; a classic handler seen as a
 * {@link BCEnergyStorage} answers each insert and extract by simulating and replays the real calls, in order, when the
 * root transaction commits (none if it aborts).
 ^/
public final class ForgeEnergyStorages {
    private ForgeEnergyStorages() {}

    private static final AdapterCache<BCEnergyStorage, ToClassic> TO_CLASSIC = new AdapterCache<>(ToClassic::new);
    private static final AdapterCache<IEnergyStorage, FromClassic> FROM_CLASSIC = new AdapterCache<>(FromClassic::new);

    /^* The Forge handler for a BuildCraft storage (null stays null). ^/
    @Nullable
    public static IEnergyStorage toForge(@Nullable BCEnergyStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromClassic view) {
            return view.handler;
        }
        return TO_CLASSIC.get(storage);
    }

    /^* The BuildCraft view of a Forge handler (null stays null). ^/
    @Nullable
    public static BCEnergyStorage fromForge(@Nullable IEnergyStorage handler) {
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
        /^* One real call owed to the handler at the root commit. ^/
        private record Owed(boolean insert, int amount) {}

        final IEnergyStorage handler;
        private final List<Owed> pending = new ArrayList<>();
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
                List<Owed> replay = new ArrayList<>(pending);
                pending.clear();
                replay.forEach(FromClassic.this::replay);
            }
        };

        FromClassic(IEnergyStorage handler) {
            this.handler = handler;
        }

        private void replay(Owed owed) {
            if (owed.insert) {
                int real = handler.receiveEnergy(owed.amount, false);
                if (real != owed.amount) {
                    BCLog.logger.warn("[platform] {} took {} of a simulated insert of {} FE", handler, real, owed.amount);
                }
            } else {
                int real = handler.extractEnergy(owed.amount, false);
                if (real != owed.amount) {
                    BCLog.logger.warn("[platform] {} gave {} of a simulated extract of {} FE", handler, real, owed.amount);
                }
            }
        }

        /^* The FE the queue already owes the handler in one direction. ^/
        private long owed(boolean insert) {
            long owed = 0;
            for (Owed call : pending) {
                if (call.insert == insert) {
                    owed += call.amount;
                }
            }
            return owed;
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
            // Each call is simulated alone (an FE handler caps every call at its own rate, so a second call may take as
            // much as the first), then capped by the headroom the queue has not already promised away. Nothing owed
            // yet, the simulation stands alone, as it did: a pass-through handler may read a capacity of 0.
            int received = handler.receiveEnergy(amount, true);
            long owed = owed(true);
            if (owed > 0) {
                long room = (long) handler.getMaxEnergyStored() - handler.getEnergyStored() - owed;
                received = (int) Math.min(received, Math.max(0, room));
            }
            if (received > 0) {
                journal.updateSnapshots(transaction);
                pending.add(new Owed(true, received));
            }
            return received;
        }

        @Override
        public int extract(int amount, BCTransactionContext transaction) {
            if (amount <= 0) {
                return 0;
            }
            int extracted = handler.extractEnergy(amount, true);
            long owed = owed(false);
            if (owed > 0) {
                extracted = (int) Math.min(extracted, Math.max(0, (long) handler.getEnergyStored() - owed));
            }
            if (extracted > 0) {
                journal.updateSnapshots(transaction);
                pending.add(new Owed(false, extracted));
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
}
*///?}
