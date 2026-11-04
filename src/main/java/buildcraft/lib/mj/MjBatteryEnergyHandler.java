/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

import java.util.function.BooleanSupplier;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * Wraps an {@link MjBattery} as a Forge energy handler, enabling FE/RF mods
 * to push or pull energy from BuildCraft machines.
 *
 * <p>Conversion uses the configurable ratio from {@link MjAPI#getRfConversion()}.
 * Default: 1 MJ = 10 RF (i.e., {@code mjPerRf = MJ / 10}).
 *
 * <p>A loader-neutral {@link BCEnergyStorage} backed by a {@link BCSnapshotJournal} (snapshots capture the
 * battery's stored power so transactional changes can be rolled back); the platform layer hands it to
 * other mods as the loader's energy handler (on 1.21.1 a classic {@code IEnergyStorage}, whose
 * {@code simulate} flag is a transaction that is never committed).
 */
public class MjBatteryEnergyHandler implements BCEnergyStorage {
    private final MjBattery battery;
    /** Insertion gate: FE pushes are refused while this returns false (e.g. a finished quarry that
     *  has no work to spend power on — parity with the MJ receiver's {@code getPowerRequested == 0}).
     *  Defaults to "always accept" for machines that should bank power whenever it's offered. */
    private final BooleanSupplier acceptInsert;

    public MjBatteryEnergyHandler(MjBattery battery) {
        this(battery, () -> true);
    }

    public MjBatteryEnergyHandler(MjBattery battery, BooleanSupplier acceptInsert) {
        this.battery = battery;
        this.acceptInsert = acceptInsert;
    }

    /**
     * Capability-registration factory that respects the {@code powerMode} config. Returns a
     * handler only when MJ&lt;-&gt;RF autoconversion is enabled; under {@code PowerMode.MJ_ONLY}
     * it returns {@code null}, so the machine exposes no Forge Energy capability and FE cables
     * won't connect — matching that mode's "machines require MJ exclusively" contract.
     */
    public static MjBatteryEnergyHandler createIfRfEnabled(MjBattery battery) {
        return createIfRfEnabled(battery, () -> true);
    }

    /**
     * As {@link #createIfRfEnabled(MjBattery)}, but with an insertion gate so the machine can refuse
     * FE pushes while it has nothing to spend power on (used by the quarry for MJ/FE parity).
     */
    public static MjBatteryEnergyHandler createIfRfEnabled(MjBattery battery, BooleanSupplier acceptInsert) {
        return MjAPI.isRfAutoConversionEnabled() ? new MjBatteryEnergyHandler(battery, acceptInsert) : null;
    }

    private long mjPerRf() {
        return MjAPI.getRfConversion().mjPerRf;
    }

    private final BCSnapshotJournal<Long> journal = new BCSnapshotJournal<>() {
        @Override
        protected Long createSnapshot() {
            return battery.getStored();
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            // Restore the battery to the snapshotted level
            long current = battery.getStored();
            if (current > snapshot) {
                battery.extractPower(current - snapshot, current - snapshot);
            } else if (current < snapshot) {
                battery.addPower(snapshot - current, false);
            }
        }
    };

    // --- BCEnergyStorage ---

    @Override
    public long getAmountAsLong() {
        long mjpr = mjPerRf();
        if (mjpr <= 0) return 0;
        return battery.getStored() / mjpr;
    }

    @Override
    public long getCapacityAsLong() {
        long mjpr = mjPerRf();
        if (mjpr <= 0) return 0;
        return battery.getCapacity() / mjpr;
    }

    @Override
    public int insert(int amount, BCTransactionContext transaction) {
        if (amount <= 0 || !acceptInsert.getAsBoolean()) return 0;
        long mjpr = mjPerRf();
        if (mjpr <= 0) return 0;

        long mjToAdd = (long) amount * mjpr;
        long space = battery.getCapacity() - battery.getStored();
        if (space <= 0) return 0;

        long actualMj = Math.min(mjToAdd, space);
        int actualRf = (int) (actualMj / mjpr);
        if (actualRf <= 0) return 0;

        long finalMj = (long) actualRf * mjpr;

        // Take a snapshot before modifying state
        journal.updateSnapshots(transaction);
        battery.addPower(finalMj, false);

        return actualRf;
    }

    @Override
    public int extract(int amount, BCTransactionContext transaction) {
        if (amount <= 0) return 0;
        long mjpr = mjPerRf();
        if (mjpr <= 0) return 0;

        long mjToExtract = (long) amount * mjpr;

        // Take a snapshot before modifying state
        journal.updateSnapshots(transaction);

        long extracted = battery.extractPower(0, mjToExtract);
        int extractedRf = (int) (extracted / mjpr);

        if (extractedRf > 0) {
            // Return any remainder (rounding difference) to the battery
            long actualMj = (long) extractedRf * mjpr;
            long remainder = extracted - actualMj;
            if (remainder > 0) {
                battery.addPower(remainder, false);
            }
        } else if (extracted > 0) {
            // Extracted too little to convert to even 1 RF; return it
            battery.addPower(extracted, false);
        }

        return extractedRf;
    }

    @Override
    public boolean canInsert() {
        return acceptInsert.getAsBoolean();
    }
}
