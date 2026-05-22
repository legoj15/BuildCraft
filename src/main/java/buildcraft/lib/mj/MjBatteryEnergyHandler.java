/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Wraps an {@link MjBattery} as a NeoForge {@link EnergyHandler}, enabling FE/RF mods
 * to push or pull energy from BuildCraft machines.
 *
 * <p>Conversion uses the configurable ratio from {@link MjAPI#getRfConversion()}.
 * Default: 1 MJ = 10 RF (i.e., {@code mjPerRf = MJ / 10}).
 *
 * <p>Extends {@link SnapshotJournal} to properly support NeoForge's transactional API.
 * Snapshots capture the battery's stored power so changes can be rolled back.
 */
public class MjBatteryEnergyHandler extends SnapshotJournal<Long> implements EnergyHandler {
    private final MjBattery battery;

    public MjBatteryEnergyHandler(MjBattery battery) {
        this.battery = battery;
    }

    /**
     * Capability-registration factory that respects the {@code powerMode} config. Returns a
     * handler only when MJ&lt;-&gt;RF autoconversion is enabled; under {@code PowerMode.MJ_ONLY}
     * it returns {@code null}, so the machine exposes no Forge Energy capability and FE cables
     * won't connect — matching that mode's "machines require MJ exclusively" contract.
     */
    public static MjBatteryEnergyHandler createIfRfEnabled(MjBattery battery) {
        return MjAPI.isRfAutoConversionEnabled() ? new MjBatteryEnergyHandler(battery) : null;
    }

    private long mjPerRf() {
        return MjAPI.getRfConversion().mjPerRf;
    }

    // --- SnapshotJournal ---

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

    @Override
    protected void releaseSnapshot(Long snapshot) {
        // No caching needed
    }

    // --- EnergyHandler ---

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
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0) return 0;
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
        updateSnapshots(transaction);
        battery.addPower(finalMj, false);

        return actualRf;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (amount <= 0) return 0;
        long mjpr = mjPerRf();
        if (mjpr <= 0) return 0;

        long mjToExtract = (long) amount * mjpr;

        // Take a snapshot before modifying state
        updateSnapshots(transaction);

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
}
