/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import buildcraft.platform.Platform;

/**
 * A transaction BuildCraft opened, and so may commit. Use it with try-with-resources: {@link #close()} without a
 * {@link #commit()} aborts it, reverting every journal that took a snapshot inside it.
 *
 * <pre>{@code
 * try (BCTransaction tx = BCTransaction.openRoot()) {
 *     int moved = storage.insert(stack, tx);
 *     if (!simulate) tx.commit();
 * }
 * }</pre>
 *
 * <p>Transactions are bound to the thread that opened them, and only one root may be open per thread.
 */
public interface BCTransaction extends BCTransactionContext, AutoCloseable {
    /** Opens a root transaction on this thread. Throws if one is already open here. */
    static BCTransaction openRoot() {
        return Platform.transfer().openRoot();
    }

    /**
     * Opens a transaction nested in the one open on this thread, or a root if none is: what a call that carries only
     * a {@code simulate} flag (no transaction of its own) runs in, so it joins a transaction its caller opened.
     */
    static BCTransaction openInCurrent() {
        return Platform.transfer().openInCurrent();
    }

    /** Opens a root transaction if {@code parent} is null, otherwise a transaction nested in it. */
    static BCTransaction open(BCTransactionContext parent) {
        return parent == null ? openRoot() : parent.openNested();
    }

    /**
     * Commits this transaction: its changes move into the parent, or become final for a root. Closes it. A commit of a
     * transaction that has already ended throws {@link IllegalStateException}, on every loader.
     */
    void commit();

    /** Aborts this transaction unless it was already committed or closed. */
    @Override
    void close();
}
