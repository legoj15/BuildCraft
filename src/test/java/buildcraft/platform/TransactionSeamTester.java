/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.TransferServices;

/**
 * The transaction contract every loader's {@link Platform#transfer()} keeps, run as game tests so the Fabric node
 * (which has no JUnit) runs it too. {@code BCTransactionTester} pins the same journal rules on the two NeoForge
 * engines; these three are the ones Fabric's transaction manager made different: what a journal calls the original
 * state after a nested probe was aborted, whether a root-commit callback may open a transaction, and what a commit that
 * is refused leaves behind.
 */
public final class TransactionSeamTester {
    private TransactionSeamTester() {}

    /** An int cell journaled through the platform; records every root-commit callback's original state. */
    private static final class Cell implements TransferServices.JournalCallbacks<Integer> {
        int value;
        final List<Integer> rootCommits = new ArrayList<>();
        final TransferServices.JournalHandle handle = Platform.transfer().newJournal(this);

        Cell(int value) {
            this.value = value;
        }

        void set(int v, BCTransactionContext tx) {
            handle.updateSnapshots(tx);
            value = v;
        }

        @Override
        public Integer createSnapshot() {
            return value;
        }

        @Override
        public void revertToSnapshot(Integer snapshot) {
            value = snapshot;
        }

        @Override
        public void onRootCommit(Integer originalState) {
            rootCommits.add(originalState);
        }
    }

    /**
     * A snapshot only a nested transaction took, and then aborted, is nothing to report later. The original state a
     * root-commit callback gets is the state before the first change of THAT root, so a machine that changed its own
     * state between roots (a tick, with no transaction in play) is not given the state from before an old probe.
     * Fabric kept the first snapshot "since cleared", and cleared it only when a depth-0 snapshot closed.
     */
    public static void nestedOnlyAbortLeavesNoStaleOriginal(GameTestHelper helper) {
        for (boolean commitProbingRoot : new boolean[] {true, false}) {
            Cell cell = new Cell(1);
            try (BCTransaction root = BCTransaction.openRoot()) {
                try (BCTransaction nested = root.openNested()) {
                    cell.set(9, nested); // the probe: aborted, back to 1
                }
                helper.assertTrue(cell.value == 1, "the aborted probe is reverted (" + cell.value + ")");
                if (commitProbingRoot) {
                    root.commit();
                }
            }
            helper.assertTrue(cell.rootCommits.isEmpty(),
                "a root whose cell only a probe touched reports no change: " + cell.rootCommits);

            cell.value = 5; // a change with no transaction in play
            try (BCTransaction root = BCTransaction.openRoot()) {
                cell.set(1, root);
                root.commit();
            }
            helper.assertTrue(cell.rootCommits.equals(List.of(5)),
                "the original state is the one before this root's change (5), not the probe's (1); probing root "
                    + (commitProbingRoot ? "committed" : "aborted") + ": " + cell.rootCommits);
        }
        helper.succeed();
    }

    /**
     * A root-commit callback may open a transaction (a tile reacting to its contents changing). Fabric runs its
     * journal's {@code onFinalCommit} while the outermost transaction is still closing, where opening one throws, so the
     * callback must run after the close for a root BuildCraft opened. Both ways in are used: a new root, and
     * {@code openInCurrent}, which reads the thread's current transaction.
     */
    public static void rootCommitCallbackMayOpenATransaction(GameTestHelper helper) {
        Cell other = new Cell(0);
        List<String> seen = new ArrayList<>();
        TransferServices.JournalHandle handle = Platform.transfer().newJournal(new TransferServices.JournalCallbacks<Integer>() {
            @Override
            public Integer createSnapshot() {
                return 0;
            }

            @Override
            public void revertToSnapshot(Integer snapshot) {}

            @Override
            public void onRootCommit(Integer originalState) {
                try (BCTransaction tx = BCTransaction.openRoot()) {
                    other.set(8, tx);
                    tx.commit();
                }
                seen.add("root");
                try (BCTransaction tx = BCTransaction.openInCurrent()) {
                    other.set(9, tx);
                    tx.commit();
                }
                seen.add("current");
            }
        });
        try (BCTransaction tx = BCTransaction.openRoot()) {
            handle.updateSnapshots(tx);
            tx.commit();
        }
        helper.assertTrue(seen.equals(List.of("root", "current")), "the callback ran and opened both: " + seen);
        helper.assertTrue(other.value == 9, "the callback's transactions committed (" + other.value + ")");
        // NeoForge folds the second root's report into the first one's, which was still queued; Fabric reports each.
        helper.assertTrue(!other.rootCommits.isEmpty() && other.rootCommits.get(0) == 0,
            "its first root reported the state before its change: " + other.rootCommits);
        helper.succeed();
    }

    /**
     * A commit the loader refuses (here: the transaction still has an open child) changes nothing, so the
     * transaction is still open, and closing it aborts it. A wrapper that counted the refused commit as the end would
     * make the later {@code close} do nothing and leave the thread's root open for every transaction after it.
     */
    public static void refusedCommitLeavesTheTransactionOpenForClose(GameTestHelper helper) {
        BCTransaction outer = BCTransaction.openRoot();
        BCTransaction inner = outer.openNested();
        boolean refused = false;
        try {
            try {
                outer.commit();
            } catch (IllegalStateException expected) {
                refused = true;
            }
        } finally {
            inner.close();
            outer.close();
        }
        helper.assertTrue(refused, "committing a transaction that has an open child is refused");
        // The root is gone: a new one opens (it throws when one is still open on the thread).
        try (BCTransaction fresh = BCTransaction.openRoot()) {
            fresh.commit();
        }
        helper.succeed();
    }
}
