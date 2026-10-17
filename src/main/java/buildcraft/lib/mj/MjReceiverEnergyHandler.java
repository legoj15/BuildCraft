/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import net.neoforged.neoforge.energy.IEnergyStorage;*/
//?}

/**
 * Presents an {@link IMjReceiver} as a Forge energy handler that only ever RECEIVES: FE pushed in is converted to
 * MJ at the configured {@link MjAPI#getRfConversion() ratio} and handed to the receiver.
 *
 * <p>The sibling of {@link MjBatteryEnergyHandler} for the cases where the thing behind the handler is not a bare
 * {@code MjBattery} but a receiver with behaviour of its own -- the docked robot's charge receiver latches
 * "being charged right now" on every real delivery, which a battery-level wrapper would bypass. It is also the
 * inverse of {@link buildcraft.api.mj.MjToRfAutoConvertor}, which wraps an FE handler as an MJ receiver.
 *
 * <p>The handler never gates itself on the power mode: a caller decides whether to expose one at all (see
 * {@link MjBatteryEnergyHandler#createIfRfEnabled}'s contract), so this class stays a pure converter.
 *
 * <p><b>Transactions (1.21.10+).</b> An {@code IMjReceiver} cannot take power back, so a transactional insert
 * cannot be applied eagerly and rolled back the way the battery wrapper does. Instead each insert only records
 * the MJ it has accepted so far in this transaction (the journaled state, so an abort simply forgets it) and the
 * receiver is paid once, for the whole amount, when the root transaction commits. Capacity is always probed
 * against the receiver with {@code simulate = true}, counting what is already pending, so two inserts in one
 * transaction can never promise more than the receiver would take. On 1.21.1 the classic {@code IEnergyStorage}
 * has a plain {@code simulate} flag and the delivery is immediate.
 *
 * <p>Conversion is exact and lossless: only whole RF are accepted, the receiver is offered exactly
 * {@code rf * mjPerRf} micro-MJ, and a sub-RF remainder is never swallowed.
 */
//? if >=1.21.10 {
public class MjReceiverEnergyHandler extends SnapshotJournal<Long> implements EnergyHandler {
//?} else {
/*public class MjReceiverEnergyHandler implements IEnergyStorage {*/
//?}
    private final IMjReceiver receiver;

    /** Micro-MJ accepted by inserts of the transaction in flight and not yet delivered (1.21.10+ only). */
    private long pendingMj;

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

    //? if >=1.21.10 {
    // --- SnapshotJournal ---

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
        long toDeliver = pendingMj;
        pendingMj = 0;
        if (toDeliver > 0) {
            receiver.receivePower(toDeliver, false);
        }
    }

    // --- EnergyHandler ---

    @Override
    public long getAmountAsLong() {
        return storedFe();
    }

    @Override
    public long getCapacityAsLong() {
        return capacityFe();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int rf = acceptableRf(amount, pendingMj);
        if (rf <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pendingMj += (long) rf * mjPerRf();
        return rf;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }
    //?} else {
    /*// --- IEnergyStorage (classic; the boolean replaces the transaction) ---

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, storedFe());
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, capacityFe());
    }

    @Override
    public boolean canReceive() {
        return receiver.canReceive();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        int rf = acceptableRf(amount, 0);
        if (rf > 0 && !simulate) {
            receiver.receivePower((long) rf * mjPerRf(), false);
        }
        return rf;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        return 0;
    }*/
    //?}
}
