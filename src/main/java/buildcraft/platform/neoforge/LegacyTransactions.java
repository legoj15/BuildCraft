/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.TransferServices;

/**
 * BuildCraft's own transaction manager, for the 1.21.1 node: NeoForge 21.1 has no transfer transactions, only the
 * classic handlers' simulate flag. It keeps the semantics of the 1.21.10+ NeoForge manager that the other nodes use
 * (one root per thread, one snapshot per journal and depth, abort reverts, a committed nested snapshot moves to the
 * parent, root-commit callbacks run after the root closed), so BuildCraft storage code behaves the same on every node.
 * Plain Java, so every NeoForge node compiles it and the transaction tests can drive it directly.
 */
public final class LegacyTransactions {
    private LegacyTransactions() {}

    private static final ThreadLocal<Manager> MANAGERS = ThreadLocal.withInitial(Manager::new);

    /** Opens a root transaction on the current thread. */
    public static BCTransaction openRoot() {
        return MANAGERS.get().open(null);
    }

    /**
     * Opens a transaction nested in the one open on this thread, or a root if none is: what a classic-handler call
     * (which carries only a simulate flag) runs in, so a handler reached from inside a BuildCraft transaction joins it
     * rather than failing to open a second root.
     */
    public static BCTransaction openInCurrent() {
        Manager manager = MANAGERS.get();
        return manager.open(manager.currentDepth >= 0 ? manager.stack.get(manager.currentDepth) : null);
    }

    /** A journal driving {@code callbacks} under this manager's transactions. */
    public static <S> TransferServices.JournalHandle newJournal(TransferServices.JournalCallbacks<S> callbacks) {
        return new Journal<>(callbacks);
    }

    private static final class Manager {
        final Thread thread = Thread.currentThread();
        final List<Tx> stack = new ArrayList<>();
        final ArrayDeque<Journal<?>> rootCommitQueue = new ArrayDeque<>();
        int currentDepth = -1;
        boolean processingRootCommitQueue;

        Tx open(Tx parent) {
            if (parent != null) {
                validateCurrent(parent);
            } else if (currentDepth >= 0) {
                throw new IllegalStateException("A root transaction is already open on thread " + thread.getName());
            }
            Tx tx;
            if (stack.size() == ++currentDepth) {
                tx = new Tx(this, currentDepth);
                stack.add(tx);
            } else {
                tx = stack.get(currentDepth);
            }
            tx.open = true;
            return tx;
        }

        void validateThread() {
            if (Thread.currentThread() != thread) {
                throw new IllegalStateException("Transaction used on thread " + Thread.currentThread().getName()
                    + " but it belongs to thread " + thread.getName());
            }
        }

        void validateCurrent(Tx tx) {
            validateThread();
            if (currentDepth < 0 || stack.get(currentDepth) != tx || !tx.open) {
                throw new IllegalStateException("Transaction at depth " + tx.depth
                    + " is not the current open transaction (current depth " + currentDepth + ")");
            }
        }

        RuntimeException processRootCommitQueue(RuntimeException error) {
            if (processingRootCommitQueue) {
                // A root-commit callback opened and committed a transaction: the outer loop picks its journals up.
                return error;
            }
            processingRootCommitQueue = true;
            while (!rootCommitQueue.isEmpty()) {
                try {
                    rootCommitQueue.remove().callOnRootCommit();
                } catch (RuntimeException e) {
                    error = addError(error, e);
                }
            }
            processingRootCommitQueue = false;
            return error;
        }
    }

    private static RuntimeException addError(RuntimeException error, RuntimeException e) {
        if (error == null) {
            return new RuntimeException("Error while closing a transaction", e);
        }
        error.addSuppressed(e);
        return error;
    }

    private static final class Tx implements BCTransaction {
        final Manager manager;
        final int depth;
        final List<Journal<?>> journalsToClose = new ArrayList<>();
        boolean open;

        Tx(Manager manager, int depth) {
            this.manager = manager;
            this.depth = depth;
        }

        @Override
        public int depth() {
            manager.validateThread();
            return depth;
        }

        @Override
        public BCTransaction openNested() {
            return manager.open(this);
        }

        @Override
        public void commit() {
            close(false);
        }

        @Override
        public void close() {
            if (open && manager.currentDepth >= depth) {
                close(true);
            }
        }

        private void close(boolean aborted) {
            manager.validateCurrent(this);
            open = false;
            RuntimeException error = null;
            for (Journal<?> journal : journalsToClose) {
                try {
                    journal.onClose(this, aborted);
                } catch (RuntimeException e) {
                    error = addError(error, e);
                }
            }
            journalsToClose.clear();
            manager.currentDepth--;
            if (manager.currentDepth == -1) {
                error = manager.processRootCommitQueue(error);
            }
            if (error != null) {
                throw error;
            }
        }
    }

    private static final class Journal<S> implements TransferServices.JournalHandle {
        private static final Object NO_SNAPSHOT = new Object();

        final TransferServices.JournalCallbacks<S> callbacks;
        final List<Object> snapshots = new ArrayList<>();
        Object originalState = NO_SNAPSHOT;

        Journal(TransferServices.JournalCallbacks<S> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public void updateSnapshots(BCTransactionContext transaction) {
            if (!(transaction instanceof Tx tx)) {
                throw new IllegalArgumentException("Not a BuildCraft 1.21.1 transaction: " + transaction);
            }
            tx.manager.validateCurrent(tx);
            int depth = tx.depth;
            while (snapshots.size() <= depth) {
                snapshots.add(NO_SNAPSHOT);
            }
            if (snapshots.get(depth) == NO_SNAPSHOT) {
                snapshots.set(depth, callbacks.createSnapshot());
                tx.journalsToClose.add(this);
            }
        }

        @SuppressWarnings("unchecked")
        void onClose(Tx tx, boolean aborted) {
            int depth = tx.depth;
            Object snapshot = snapshots.remove(depth);
            if (aborted) {
                callbacks.revertToSnapshot((S) snapshot);
            } else if (depth == 0) {
                if (originalState == NO_SNAPSHOT) {
                    originalState = snapshot;
                    tx.manager.rootCommitQueue.add(this);
                }
                // else: a root-commit callback changed this journal again; the queued callback still covers it.
            } else if (snapshots.get(depth - 1) == NO_SNAPSHOT) {
                snapshots.set(depth - 1, snapshot);
                tx.manager.stack.get(depth - 1).journalsToClose.add(this);
            }
            // else: the parent already holds an older snapshot, which wins.
        }

        @SuppressWarnings("unchecked")
        void callOnRootCommit() {
            Object original = originalState;
            originalState = NO_SNAPSHOT;
            callbacks.onRootCommit((S) original);
        }
    }
}
//?}
