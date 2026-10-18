/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.mj;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.test.FeHandlerAccess;

/**
 * Pins {@link MjReceiverEnergyHandler}, the FE-in / MJ-out converter that lets a docking station on an RF kinesis
 * pipe charge a docked robot: exact lossless conversion at the configured ratio, whole-RF granularity, no swallowed
 * remainders, honest simulation, one delivery per committed transaction, and no phantom capacity when several
 * inserts share a transaction. Runs under the FML-JUnit environment (the config specs are loaded), so the ratio
 * is the real {@code mjRfConversionAmount}; it is pinned per test and restored.
 */
public class MjReceiverEnergyHandlerTest {

    private double previousRatio;

    @BeforeEach
    public void pinRatio() {
        previousRatio = BCLibConfig.mjRfConversionAmount.get();
        BCLibConfig.mjRfConversionAmount.set(0.1); // 10 RF per MJ
    }

    @AfterEach
    public void restoreRatio() {
        BCLibConfig.mjRfConversionAmount.set(previousRatio);
    }

    /** A receiver with {@code room} micro-MJ of headroom that records every REAL delivery. */
    private static final class FakeReceiver implements IMjReceiver, IMjReadable {
        long stored;
        final long capacity;
        boolean accepting = true;
        int realDeliveries;

        FakeReceiver(long stored, long capacity) {
            this.stored = stored;
            this.capacity = capacity;
        }

        @Override
        public boolean canConnect(IMjConnector other) {
            return true;
        }

        @Override
        public boolean canReceive() {
            return accepting;
        }

        @Override
        public long getPowerRequested() {
            return capacity - stored;
        }

        @Override
        public long receivePower(long microJoules, boolean simulate) {
            long taken = Math.min(microJoules, capacity - stored);
            if (!simulate) {
                stored += taken;
                realDeliveries++;
            }
            return microJoules - taken;
        }

        @Override
        public long getStored() {
            return stored;
        }

        @Override
        public long getCapacity() {
            return capacity;
        }
    }

    /** A receiver that is not {@code IMjReadable}, to pin the "bufferless" reading of the handler. */
    private static final class BlindReceiver implements IMjReceiver {
        @Override
        public boolean canConnect(IMjConnector other) {
            return true;
        }

        @Override
        public long getPowerRequested() {
            return Long.MAX_VALUE;
        }

        @Override
        public long receivePower(long microJoules, boolean simulate) {
            return 0;
        }
    }

    @Test
    public void committedInsertConvertsLosslesslyAtTheConfiguredRatio() {
        FakeReceiver receiver = new FakeReceiver(0, 1_000 * MjAPI.MJ);
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);

        Assertions.assertEquals(100, FeHandlerAccess.insert(handler, 100, true));
        Assertions.assertEquals(10 * MjAPI.MJ, receiver.stored, "100 FE at 10 RF/MJ is exactly 10 MJ");
        Assertions.assertEquals(1, receiver.realDeliveries, "one commit is one delivery");

        BCLibConfig.mjRfConversionAmount.set(0.2); // 5 RF per MJ
        Assertions.assertEquals(50, FeHandlerAccess.insert(handler, 50, true));
        Assertions.assertEquals(20 * MjAPI.MJ, receiver.stored, "the ratio is read live: 50 FE at 5 RF/MJ is 10 MJ more");
    }

    @Test
    public void simulatedInsertReportsTheAmountButDeliversNothing() {
        FakeReceiver receiver = new FakeReceiver(0, 1_000 * MjAPI.MJ);
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);

        Assertions.assertEquals(100, FeHandlerAccess.insert(handler, 100, false));
        Assertions.assertEquals(0, receiver.stored, "a rolled-back insert must not charge anything");
        Assertions.assertEquals(0, receiver.realDeliveries);
        Assertions.assertEquals(100, FeHandlerAccess.insert(handler, 100, false),
            "the abort left no pending state behind: the same probe answers the same");
    }

    @Test
    public void partialHeadroomIsFilledInWholeRfOnly() {
        // 7.5 MJ of room = 75 FE at 10 RF/MJ.
        FakeReceiver receiver = new FakeReceiver(0, 7 * MjAPI.MJ + MjAPI.MJ / 2);
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);

        Assertions.assertEquals(75, FeHandlerAccess.insert(handler, 100, true));
        Assertions.assertEquals(7 * MjAPI.MJ + MjAPI.MJ / 2, receiver.stored);

        // 0.05 MJ of room is half an RF: nothing may be accepted (and no remainder swallowed).
        FakeReceiver nearlyFull = new FakeReceiver(0, MjAPI.MJ / 20);
        Assertions.assertEquals(0, FeHandlerAccess.insert(new MjReceiverEnergyHandler(nearlyFull), 100, true));
        Assertions.assertEquals(0, nearlyFull.stored);
    }

    @Test
    public void refusingReceiverAndNonPositiveAmountsAcceptNothing() {
        FakeReceiver receiver = new FakeReceiver(0, 1_000 * MjAPI.MJ);
        receiver.accepting = false;
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);
        Assertions.assertEquals(0, FeHandlerAccess.insert(handler, 100, true), "a receiver that cannot receive");

        receiver.accepting = true;
        Assertions.assertEquals(0, FeHandlerAccess.insert(handler, 0, true));
        Assertions.assertEquals(0, FeHandlerAccess.insert(handler, -5, true));
        Assertions.assertEquals(0, receiver.stored);
    }

    @Test
    public void theHandlerIsNeverAnFeSource() {
        FakeReceiver receiver = new FakeReceiver(500 * MjAPI.MJ, 1_000 * MjAPI.MJ);
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);
        Assertions.assertEquals(0, FeHandlerAccess.extract(handler, 100));
        Assertions.assertEquals(500 * MjAPI.MJ, receiver.stored, "extract must not touch the receiver");
    }

    @Test
    public void readingBackIsInFeAndZeroForAReceiverThatCannotBeRead() {
        FakeReceiver receiver = new FakeReceiver(30 * MjAPI.MJ, 1_000 * MjAPI.MJ);
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);
        Assertions.assertEquals(300, FeHandlerAccess.stored(handler), "30 MJ is 300 FE at 10 RF/MJ");
        Assertions.assertEquals(10_000, FeHandlerAccess.capacity(handler));

        MjReceiverEnergyHandler blind = new MjReceiverEnergyHandler(new BlindReceiver());
        Assertions.assertEquals(0, FeHandlerAccess.stored(blind));
        Assertions.assertEquals(0, FeHandlerAccess.capacity(blind));
        Assertions.assertEquals(100, FeHandlerAccess.insert(blind, 100, false),
            "a bufferless receiver is still fed: demand is learned by trying, not by reading a buffer");
    }

    //? if >=1.21.10 {
    /** Two inserts inside ONE transaction share the receiver's headroom and are paid in a single delivery on
     *  commit; an abort pays nothing. (1.21.10+ only: the classic 1.21.1 API has no transactions.) */
    @Test
    public void insertsInOneTransactionShareHeadroomAndCommitOnce() {
        FakeReceiver receiver = new FakeReceiver(0, 15 * MjAPI.MJ); // 150 FE of room
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);

        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                 net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            Assertions.assertEquals(100, handler.insert(100, tx));
            Assertions.assertEquals(50, handler.insert(100, tx), "only 50 FE of the 150 are left");
            Assertions.assertEquals(0, handler.insert(1, tx), "now it is full");
            Assertions.assertEquals(0, receiver.stored, "nothing is delivered before the commit");
            tx.commit();
        }
        Assertions.assertEquals(15 * MjAPI.MJ, receiver.stored);
        Assertions.assertEquals(1, receiver.realDeliveries, "both inserts are paid in one delivery");

        FakeReceiver other = new FakeReceiver(0, 15 * MjAPI.MJ);
        MjReceiverEnergyHandler aborted = new MjReceiverEnergyHandler(other);
        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                 net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            Assertions.assertEquals(100, aborted.insert(100, tx));
            // no commit: close() aborts
        }
        Assertions.assertEquals(0, other.stored);
        Assertions.assertEquals(0, other.realDeliveries);
        Assertions.assertEquals(150, FeHandlerAccess.insert(aborted, 150, false),
            "the aborted insert left no phantom pending MJ behind");
    }

    /** An insert is accepted (and the FE taken from the sender) against headroom probed at insert time; if the
     *  receiver's free room shrinks before the root commit, the MJ it cannot take is not forgotten: it stays owed
     *  and is paid on the next commit that goes through the handler. */
    @Test
    public void aShortfallAtCommitIsKeptAndPaidOnTheNextCommit() {
        FakeReceiver receiver = new FakeReceiver(0, 20 * MjAPI.MJ);
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);

        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                 net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            Assertions.assertEquals(100, handler.insert(100, tx), "10 MJ accepted against 20 MJ of room");
            receiver.stored = 15 * MjAPI.MJ; // the receiver's free room shrinks to 5 MJ before the commit
            tx.commit();
        }
        Assertions.assertEquals(20 * MjAPI.MJ, receiver.stored, "the commit paid the 5 MJ the receiver had room for");

        receiver.stored = 0; // the receiver is drained again: 20 MJ of room
        Assertions.assertEquals(10, FeHandlerAccess.insert(handler, 10, true), "1 MJ more");
        Assertions.assertEquals(6 * MjAPI.MJ, receiver.stored,
            "the next commit paid the 1 MJ AND the 5 MJ owed from the shortfall, which had been accepted too");
    }

    /** What is owed fills headroom the receiver has not got yet, so a later insert is not promised room that the
     *  owed MJ will take. */
    @Test
    public void whatIsOwedCountsAgainstTheHeadroomOfLaterInserts() {
        FakeReceiver receiver = new FakeReceiver(0, 20 * MjAPI.MJ);
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);
        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                 net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            handler.insert(100, tx);
            receiver.stored = 15 * MjAPI.MJ;
            tx.commit();
        }
        Assertions.assertEquals(20 * MjAPI.MJ, receiver.stored, "precondition: 5 MJ is owed");

        receiver.stored = 18 * MjAPI.MJ; // 2 MJ of room: less than the 5 MJ owed
        Assertions.assertEquals(0, FeHandlerAccess.insert(handler, 100, false), "the room is spoken for");

        receiver.stored = 10 * MjAPI.MJ; // 10 MJ of room, 5 MJ of it owed
        Assertions.assertEquals(50, FeHandlerAccess.insert(handler, 200, false), "only the 5 MJ beyond the debt");
        Assertions.assertEquals(50, FeHandlerAccess.insert(handler, 200, true));
        Assertions.assertEquals(20 * MjAPI.MJ, receiver.stored, "the debt and the new MJ together exactly filled it");
    }

    /** A rolled-back transaction forgets only its own inserts, never the debt of an earlier committed one. */
    @Test
    public void anAbortedTransactionLeavesWhatIsOwedAlone() {
        FakeReceiver receiver = new FakeReceiver(0, 20 * MjAPI.MJ);
        MjReceiverEnergyHandler handler = new MjReceiverEnergyHandler(receiver);
        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                 net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            handler.insert(100, tx);
            receiver.stored = 15 * MjAPI.MJ;
            tx.commit();
        }
        receiver.stored = 0;

        Assertions.assertEquals(10, FeHandlerAccess.insert(handler, 10, false), "a probe, rolled back");
        Assertions.assertEquals(0, receiver.stored, "nothing was delivered by the abort");

        Assertions.assertEquals(10, FeHandlerAccess.insert(handler, 10, true));
        Assertions.assertEquals(6 * MjAPI.MJ, receiver.stored, "the debt survived the abort and is paid now");
    }
    //?}
}
