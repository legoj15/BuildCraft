/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
//?} else {
/*import net.neoforged.neoforge.energy.EnergyStorage;*/
//?}

/** Version-neutral FE/RF battery used by the FE engine and the MJ dynamo.
 *
 * <p>On 1.21.10+ it is a Transfer-API {@code SimpleEnergyHandler} (exposed to neighbours as an
 * {@code EnergyHandler} capability); on 1.21.1 (no Transfer API) it is a classic
 * {@code net.neoforged.neoforge.energy.EnergyStorage} (an {@code IEnergyStorage}). Both honour the same
 * {@code (capacity, maxInsert, maxExtract)} construction and expose {@code getAmountAsLong()} /
 * {@code set(int)} plus an {@link #onFeChanged()} hook, so the engine/dynamo bodies stay identical across
 * nodes — only the neighbour-facing cap fetch (which differs by node) gates its body. */
//? if >=1.21.10 {
public class BCFeStorage extends SimpleEnergyHandler {
    public BCFeStorage(int capacity, int maxInsert, int maxExtract) {
        super(capacity, maxInsert, maxExtract);
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onFeChanged();
    }

    /** Called whenever the stored amount changes (via {@code set}, insert, or extract). Override to react. */
    protected void onFeChanged() {}
}
//?} else {
/*public class BCFeStorage extends EnergyStorage {
    public BCFeStorage(int capacity, int maxInsert, int maxExtract) {
        super(capacity, maxInsert, maxExtract);
    }

    public long getAmountAsLong() {
        return this.energy;
    }

    public void set(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (this.energy != amount) {
            this.energy = amount;
            onFeChanged();
        }
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int received = super.receiveEnergy(toReceive, simulate);
        if (received > 0 && !simulate) {
            onFeChanged();
        }
        return received;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int extracted = super.extractEnergy(toExtract, simulate);
        if (extracted > 0 && !simulate) {
            onFeChanged();
        }
        return extracted;
    }

    protected void onFeChanged() {}
}*/
//?}
