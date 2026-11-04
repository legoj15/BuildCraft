/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}

import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.TransferServices;

/**
 * {@link TransferServices} on NeoForge. On 1.21.10+ a {@link BCTransaction} is a NeoForge {@code Transaction} and a
 * BuildCraft journal is a NeoForge {@code SnapshotJournal}, so BuildCraft state joins the transactions other mods open
 * (an adapter wraps the {@code TransactionContext} it is handed with {@link #wrap}). On 1.21.1 there is no NeoForge
 * transaction, so {@link LegacyTransactions} stands in and the classic handlers' simulate flag maps onto it.
 */
public final class NeoForgeTransfer implements TransferServices {
    @Override
    public BCTransaction openRoot() {
        //? if >=1.21.10 {
        return new Owned(Transaction.openRoot());
        //?} else {
        /*return LegacyTransactions.openRoot();
        *///?}
    }

    @Override
    public <S> JournalHandle newJournal(JournalCallbacks<S> callbacks) {
        //? if >=1.21.10 {
        Journal<S> journal = new Journal<>(callbacks);
        return tx -> journal.updateSnapshots(unwrap(tx));
        //?} else {
        /*return LegacyTransactions.newJournal(callbacks);
        *///?}
    }

    //? if >=1.21.10 {
    /** The BuildCraft view of a NeoForge transaction someone else opened: it can be nested into, never committed. */
    public static BCTransactionContext wrap(TransactionContext context) {
        return new Borrowed(context);
    }

    /** The NeoForge transaction behind a BuildCraft one. */
    public static TransactionContext unwrap(BCTransactionContext transaction) {
        if (transaction instanceof Owned owned) {
            return owned.tx;
        }
        if (transaction instanceof Borrowed borrowed) {
            return borrowed.context;
        }
        throw new IllegalArgumentException("Not a NeoForge-backed transaction: " + transaction);
    }

    private record Borrowed(TransactionContext context) implements BCTransactionContext {
        @Override
        public int depth() {
            return context.depth();
        }

        @Override
        public BCTransaction openNested() {
            return new Owned(Transaction.open(context));
        }
    }

    private static final class Owned implements BCTransaction {
        final Transaction tx;

        Owned(Transaction tx) {
            this.tx = tx;
        }

        @Override
        public int depth() {
            return tx.depth();
        }

        @Override
        public BCTransaction openNested() {
            return new Owned(Transaction.open(tx));
        }

        @Override
        public void commit() {
            tx.commit();
        }

        @Override
        public void close() {
            tx.close();
        }

        @Override
        public String toString() {
            return "BC" + tx;
        }
    }

    private static final class Journal<S> extends SnapshotJournal<S> {
        private final JournalCallbacks<S> callbacks;

        Journal(JournalCallbacks<S> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        protected S createSnapshot() {
            return callbacks.createSnapshot();
        }

        @Override
        protected void revertToSnapshot(S snapshot) {
            callbacks.revertToSnapshot(snapshot);
        }

        @Override
        protected void onRootCommit(S originalState) {
            callbacks.onRootCommit(originalState);
        }
    }
    //?}
}
//?}
