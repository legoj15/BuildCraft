/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.TransferServices;

/^*
 * {@link TransferServices} on Fabric: a {@link BCTransaction} is a Fabric {@link Transaction} and a BuildCraft journal
 * is a {@link SnapshotParticipant}, so BuildCraft state joins the transactions other mods open (an adapter wraps the
 * {@link TransactionContext} it is handed with {@link #wrap}).
 *
 * <p>Fabric's {@code onFinalCommit} does not pass the original state, so the participant keeps the first snapshot
 * taken since the outer transaction opened and hands that to {@code onRootCommit}, matching NeoForge.
 ^/
public final class FabricTransfer implements TransferServices {
    @Override
    public BCTransaction openRoot() {
        return new Owned(Transaction.openOuter());
    }

    @Override
    public <S> JournalHandle newJournal(JournalCallbacks<S> callbacks) {
        Participant<S> participant = new Participant<>(callbacks);
        return tx -> participant.updateSnapshots(unwrap(tx));
    }

    /^* The BuildCraft view of a Fabric transaction someone else opened: it can be nested into, never committed. ^/
    public static BCTransactionContext wrap(TransactionContext context) {
        return new Borrowed(context);
    }

    /^* The Fabric transaction behind a BuildCraft one. ^/
    public static TransactionContext unwrap(BCTransactionContext transaction) {
        if (transaction instanceof Owned owned) {
            return owned.tx;
        }
        if (transaction instanceof Borrowed borrowed) {
            return borrowed.context;
        }
        throw new IllegalArgumentException("Not a Fabric-backed transaction: " + transaction);
    }

    private record Borrowed(TransactionContext context) implements BCTransactionContext {
        @Override
        public int depth() {
            return context.nestingDepth();
        }

        @Override
        public BCTransaction openNested() {
            return new Owned(context.openNested());
        }
    }

    private static final class Owned implements BCTransaction {
        final Transaction tx;
        private boolean closed;

        Owned(Transaction tx) {
            this.tx = tx;
        }

        @Override
        public int depth() {
            return tx.nestingDepth();
        }

        @Override
        public BCTransaction openNested() {
            return new Owned(tx.openNested());
        }

        @Override
        public void commit() {
            closed = true;
            tx.commit();
        }

        @Override
        public void close() {
            // Fabric's close() throws on an already-closed transaction; the BC contract makes it idempotent.
            if (!closed) {
                closed = true;
                tx.close();
            }
        }
    }

    private static final class Participant<S> extends SnapshotParticipant<S> {
        private static final Object UNSET = new Object();

        private final JournalCallbacks<S> callbacks;
        private Object original = UNSET;

        Participant(JournalCallbacks<S> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        protected S createSnapshot() {
            S snapshot = callbacks.createSnapshot();
            if (original == UNSET) {
                original = snapshot;
            }
            return snapshot;
        }

        @Override
        protected void readSnapshot(S snapshot) {
            callbacks.revertToSnapshot(snapshot);
        }

        @Override
        public void onClose(TransactionContext transaction, TransactionContext.Result result) {
            super.onClose(transaction, result);
            if (transaction.nestingDepth() == 0 && result.wasAborted()) {
                original = UNSET;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void onFinalCommit() {
            Object state = original;
            original = UNSET;
            callbacks.onRootCommit((S) state);
        }
    }
}
*///?}
