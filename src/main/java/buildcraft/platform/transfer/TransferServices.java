/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

/**
 * The loader's transfer machinery. Common code uses {@link BCTransaction} and {@link BCSnapshotJournal} rather than
 * calling this directly; it is the seam those two delegate to.
 */
public interface TransferServices {
    /** Opens a root transaction on the current thread (throws if one is already open there). */
    BCTransaction openRoot();

    /** A loader journal driving {@code callbacks}. */
    <S> JournalHandle newJournal(JournalCallbacks<S> callbacks);

    /** The loader journal behind one {@link BCSnapshotJournal}. */
    interface JournalHandle {
        void updateSnapshots(BCTransactionContext transaction);
    }

    /** What a loader journal calls back into: the {@link BCSnapshotJournal} methods, made reachable. */
    interface JournalCallbacks<S> {
        S createSnapshot();

        void revertToSnapshot(S snapshot);

        void onRootCommit(S originalState);
    }
}
