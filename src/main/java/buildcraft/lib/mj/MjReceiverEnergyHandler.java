/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;

import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * Presents an {@link IMjReceiver} as a Forge energy handler that only ever RECEIVES: FE pushed in is converted to
 * MJ at the configured {@link MjAPI#getRfConversion() ratio} and handed to the receiver.
 *
 * <p>The sibling of {@link MjBatteryEnergyHandler} for the cases where the thing behind the handler is not a bare
 * {@code MjBattery} but a receiver with behaviour of its own -- the docked robot's charge receiver latches
 * "being charged right now" on every real delivery, which a battery-level wrapper would bypass. It is also the
 * inverse of {@link buildcraft.lib.mj.MjToRfAutoConvertor}, which wraps an FE handler as an MJ receiver.
 *
 * <p>The handler never gates itself on the power mode: a caller decides whether to expose one at all (see
 * {@link MjBatteryEnergyHandler#createIfRfEnabled}'s contract), so this class stays a pure converter.
 *
 * <p><b>Transactions.</b> An {@code IMjReceiver} cannot take power back, so a transactional insert
 * cannot be applied eagerly and rolled back the way the battery wrapper does. Instead each insert only records
 * the MJ it has accepted so far in this transaction (the journaled state, so an abort simply forgets it) and the
 * receiver is paid once, for the whole amount, when the root transaction commits. Capacity is always probed
 * against the receiver with {@code simulate = true}, counting what is already pending, so two inserts in one
 * transaction can never promise more than the receiver would take. On 1.21.1 a classic {@code IEnergyStorage} call
 * runs in a transaction of its own, so a non-simulated insert is paid as the call returns.
 *
 * <p>An insert is final the moment it returns (the sender has already given up that FE), so a commit may never
 * simply drop what the receiver turns out not to take: if its free room shrank between the insert and the commit,
 * the difference is kept as <em>owed</em> MJ. Owed MJ counts against the headroom of every later probe, as pending
 * MJ does, so it is never promised twice, and it is paid with the next commit that goes through this handler. The
 * ledger lives on the handler instance, so it outlasts a transaction only for a caller that keeps the handler.
 *
 * <p>Conversion is exact and lossless: only whole RF are accepted, the receiver is offered exactly
 * {@code rf * mjPerRf} micro-MJ, and a sub-RF remainder is never swallowed.
 */
public class MjReceiverEnergyHandler implements BCEnergyStorage {
    private final IMjReceiver receiver;

    /** Micro-MJ accepted by inserts of the transaction in flight and not yet delivered. */
    private long pendingMj;

    /** Micro-MJ accepted by committed inserts that the receiver had no room for at commit time, still to be paid
     *  Only changed by a root commit, so it needs no snapshot: an abort never touches it. */
    private long owedMj;

    public MjReceiverEnergyHandler(IMjReceiver receiver) {
        this.receiver = receiver;
    }

    private static long mjPerRf() {
        return MjAPI.getRfConversion().mjPerRf;
    }

    /** How many whole RF of {@code amount} the receiver would take on top of {@code alreadyPendingMj}. */
    private int acceptableRf(int amount, long alreadyPendingMj) {
        if (amount <= 0 || !receiver.canReceive()) {
            return 0;
        }
        long mjPerRf = mjPerRf();
        if (mjPerRf <= 0) {
            return 0;
        }
        long requested = alreadyPendingMj + (long) amount * mjPerRf;
        long fits = requested - receiver.receivePower(requested, true);
        long freshMj = fits - alreadyPendingMj;
        if (freshMj <= 0) {
            return 0;
        }
        return (int) Math.min(amount, freshMj / mjPerRf);
    }

    private long storedFe() {
        long mjPerRf = mjPerRf();
        return receiver instanceof IMjReadable readable && mjPerRf > 0 ? readable.getStored() / mjPerRf : 0;
    }

    private long capacityFe() {
        long mjPerRf = mjPerRf();
        return receiver instanceof IMjReadable readable && mjPerRf > 0 ? readable.getCapacity() / mjPerRf : 0;
    }

    private final BCSnapshotJournal<Long> journal = new BCSnapshotJournal<>() {
        @Override
        protected Long createSnapshot() {
            return pendingMj;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            pendingMj = snapshot;
        }

        @Override
        protected void onRootCommit(Long originalState) {
            long toDeliver = owedMj + pendingMj;
            pendingMj = 0;
            // What the receiver would not take (its room shrank since the insert probed it) was accepted all the same:
            // it stays owed, to be probed against and paid again, rather than vanishing with the transaction.
            owedMj = toDeliver > 0 ? receiver.receivePower(toDeliver, false) : 0;
        }
    };

    // --- BCEnergyStorage ---

    @Override
    public long getAmountAsLong() {
        return storedFe();
    }

    @Override
    public long getCapacityAsLong() {
        return capacityFe();
    }

    @Override
    public int insert(int amount, BCTransactionContext transaction) {
        int rf = acceptableRf(amount, owedMj + pendingMj);
        if (rf <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        pendingMj += (long) rf * mjPerRf();
        return rf;
    }

    @Override
    public int extract(int amount, BCTransactionContext transaction) {
        return 0;
    }

    @Override
    public boolean canInsert() {
        return receiver.canReceive();
    }

    @Override
    public boolean canExtract() {
        return false;
    }
}
