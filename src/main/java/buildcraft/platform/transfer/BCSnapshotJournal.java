/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import buildcraft.platform.Platform;

/**
 * Transaction support for one piece of BuildCraft state, mapped onto NeoForge {@code SnapshotJournal}, Fabric
 * {@code SnapshotParticipant}, or BuildCraft's own manager on 1.21.1.
 *
 * <p>Call {@link #updateSnapshots} before every change made under a transaction. The journal then snapshots the state
 * once per transaction depth, reverts to that snapshot if the transaction aborts, and calls {@link #onRootCommit} once
 * the root transaction commits, with the state as it was before the first change.
 *
 * @param <S> the snapshot type; a mutable state must be copied in {@link #createSnapshot}.
 */
public abstract class BCSnapshotJournal<S> {
    private TransferServices.JournalHandle handle;

    /** A copy of the current state, enough to undo every change made since. */
    protected abstract S createSnapshot();

    /** Restores a state {@link #createSnapshot} returned. */
    protected abstract void revertToSnapshot(S snapshot);

    /**
     * After the root transaction committed a change: {@code originalState} is the state before the first change.
     * It runs once the root has closed, so it may open transactions of its own. (Fabric keeps that for a root
     * BuildCraft opened itself; a root another mod opened runs it inside the close, where opening one throws.)
     */
    protected void onRootCommit(S originalState) {}

    /** Snapshots the state for {@code transaction}'s depth unless that depth already has a snapshot. */
    public final void updateSnapshots(BCTransactionContext transaction) {
        if (handle == null) {
            handle = Platform.transfer().newJournal(new TransferServices.JournalCallbacks<S>() {
                @Override
                public S createSnapshot() {
                    return BCSnapshotJournal.this.createSnapshot();
                }

                @Override
                public void revertToSnapshot(S snapshot) {
                    BCSnapshotJournal.this.revertToSnapshot(snapshot);
                }

                @Override
                public void onRootCommit(S originalState) {
                    BCSnapshotJournal.this.onRootCommit(originalState);
                }
            });
        }
        handle.updateSnapshots(transaction);
    }
}
