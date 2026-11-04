/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?}

import buildcraft.platform.Platform;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.TransferServices;

/**
 * Commit, abort and nesting for {@link BCTransaction} and its journals, on both engines a NeoForge node carries: the
 * node's own ({@link Platform#transfer()}, a NeoForge {@code Transaction} on 1.21.10+) and {@link LegacyTransactions}
 * (what 1.21.1 uses), so the two cannot drift apart.
 */
public class BCTransactionTester {
    /** How an engine makes a journal. */
    private interface Journals {
        <S> TransferServices.JournalHandle newJournal(TransferServices.JournalCallbacks<S> callbacks);
    }

    /** One engine under test: how it opens a root and makes a journal. */
    private record Engine(String name, Supplier<BCTransaction> root, Journals services) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Engine platformEngine() {
        return new Engine("platform", () -> Platform.transfer().openRoot(), new Journals() {
            @Override
            public <S> TransferServices.JournalHandle newJournal(TransferServices.JournalCallbacks<S> callbacks) {
                return Platform.transfer().newJournal(callbacks);
            }
        });
    }

    static Stream<Arguments> engines() {
        Journals legacy = new Journals() {
            @Override
            public <S> TransferServices.JournalHandle newJournal(TransferServices.JournalCallbacks<S> callbacks) {
                return LegacyTransactions.newJournal(callbacks);
            }
        };
        return Stream.of(
            Arguments.of(platformEngine()),
            Arguments.of(new Engine("legacy", LegacyTransactions::openRoot, legacy)));
    }

    /** An int cell journaled through one engine; records every root-commit callback's original state. */
    private static final class Cell implements TransferServices.JournalCallbacks<Integer> {
        int value;
        final List<Integer> rootCommits = new ArrayList<>();
        final TransferServices.JournalHandle handle;

        Cell(Engine engine, int value) {
            this.value = value;
            this.handle = engine.services().newJournal(this);
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

    @ParameterizedTest
    @MethodSource("engines")
    public void commitKeepsTheChange(Engine engine) {
        Cell cell = new Cell(engine, 1);
        try (BCTransaction tx = engine.root().get()) {
            assertEquals(0, tx.depth());
            cell.set(5, tx);
            cell.set(6, tx);
            tx.commit();
        }
        assertEquals(6, cell.value);
        assertEquals(List.of(1), cell.rootCommits, "one root commit, carrying the state before the first change");
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void closeWithoutCommitAborts(Engine engine) {
        Cell cell = new Cell(engine, 1);
        try (BCTransaction tx = engine.root().get()) {
            cell.set(5, tx);
        }
        assertEquals(1, cell.value);
        assertTrue(cell.rootCommits.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void nestedCommitThenOuterAbortReverts(Engine engine) {
        Cell cell = new Cell(engine, 1);
        try (BCTransaction outer = engine.root().get()) {
            try (BCTransaction inner = outer.openNested()) {
                assertEquals(1, inner.depth());
                cell.set(5, inner);
                inner.commit();
            }
            assertEquals(5, cell.value, "a nested commit is visible to the parent");
        }
        assertEquals(1, cell.value, "the outer abort undoes the nested commit");
        assertTrue(cell.rootCommits.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void nestedAbortThenOuterCommit(Engine engine) {
        Cell cell = new Cell(engine, 1);
        try (BCTransaction outer = engine.root().get()) {
            cell.set(2, outer);
            try (BCTransaction inner = outer.openNested()) {
                cell.set(9, inner);
            }
            assertEquals(2, cell.value, "the nested abort restores the parent's state");
            outer.commit();
        }
        assertEquals(2, cell.value);
        assertEquals(List.of(1), cell.rootCommits);
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void snapshotOnlyInTheInnermostDepthStillCommits(Engine engine) {
        Cell cell = new Cell(engine, 3);
        try (BCTransaction outer = engine.root().get()) {
            try (BCTransaction mid = outer.openNested()) {
                try (BCTransaction inner = mid.openNested()) {
                    cell.set(7, inner);
                    inner.commit();
                }
                mid.commit();
            }
            outer.commit();
        }
        assertEquals(7, cell.value);
        assertEquals(List.of(3), cell.rootCommits);
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void onlyOneRootPerThread(Engine engine) {
        try (BCTransaction tx = engine.root().get()) {
            assertThrows(IllegalStateException.class, () -> engine.root().get());
        }
        // and the failed open left nothing behind: a fresh root opens fine
        try (BCTransaction tx = engine.root().get()) {
            tx.commit();
        }
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void closeAfterCommitIsHarmless(Engine engine) {
        Cell cell = new Cell(engine, 1);
        BCTransaction tx = engine.root().get();
        cell.set(4, tx);
        tx.commit();
        tx.close();
        assertEquals(4, cell.value);
        assertEquals(List.of(1), cell.rootCommits);
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void rootCommitCallbackRunsOutsideTheTransaction(Engine engine) {
        // A callback may open a new root (a tile reacting to its contents changing).
        List<String> seen = new ArrayList<>();
        Cell other = new Cell(engine, 0);
        TransferServices.JournalHandle handle = engine.services().newJournal(new TransferServices.JournalCallbacks<Integer>() {
            @Override
            public Integer createSnapshot() {
                return 0;
            }

            @Override
            public void revertToSnapshot(Integer snapshot) {}

            @Override
            public void onRootCommit(Integer originalState) {
                try (BCTransaction tx = engine.root().get()) {
                    other.set(8, tx);
                    tx.commit();
                }
                seen.add("callback");
            }
        });
        try (BCTransaction tx = engine.root().get()) {
            handle.updateSnapshots(tx);
            tx.commit();
        }
        assertEquals(List.of("callback"), seen);
        assertEquals(8, other.value);
        assertEquals(List.of(0), other.rootCommits);
    }

    @Test
    public void bcSnapshotJournalDrivesThePlatformEngine() {
        int[] state = {10};
        List<Integer> commits = new ArrayList<>();
        BCSnapshotJournal<Integer> journal = new BCSnapshotJournal<>() {
            @Override
            protected Integer createSnapshot() {
                return state[0];
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                state[0] = snapshot;
            }

            @Override
            protected void onRootCommit(Integer originalState) {
                commits.add(originalState);
            }
        };
        try (BCTransaction tx = BCTransaction.openRoot()) {
            journal.updateSnapshots(tx);
            state[0] = 11;
        }
        assertEquals(10, state[0]);
        try (BCTransaction tx = BCTransaction.open(null)) {
            journal.updateSnapshots(tx);
            state[0] = 12;
            try (BCTransaction inner = BCTransaction.open(tx)) {
                assertEquals(1, inner.depth());
            }
            tx.commit();
        }
        assertEquals(12, state[0]);
        assertEquals(List.of(10), commits);
    }

    //? if >=1.21.10 {
    @Test
    public void bcJournalJoinsANeoForgeTransaction() {
        // What a capability adapter does: NeoForge hands it a TransactionContext, BC state journals into it.
        Engine platform = platformEngine();

        Cell cell = new Cell(platform, 1);
        try (Transaction neo = Transaction.openRoot()) {
            BCTransactionContext view = NeoForgeTransfer.wrap(neo);
            assertEquals(0, view.depth());
            try (BCTransaction nested = view.openNested()) {
                cell.set(5, nested);
                nested.commit();
            }
            assertEquals(5, cell.value);
        }
        assertEquals(1, cell.value, "aborting the NeoForge transaction reverts the BC state");
        try (Transaction neo = Transaction.openRoot()) {
            cell.set(6, NeoForgeTransfer.wrap(neo));
            neo.commit();
        }
        assertEquals(6, cell.value);
        assertEquals(List.of(1), cell.rootCommits);
    }
    //?}
}
