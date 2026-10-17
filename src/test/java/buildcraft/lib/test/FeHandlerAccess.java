/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?} else {
/*import net.neoforged.neoforge.energy.IEnergyStorage;*/
//?}

/**
 * Version-neutral access to a Forge energy handler for tests. The handler type cliffs at 1.21.10 (Transfer API
 * {@code EnergyHandler} + {@code Transaction}, versus the classic {@code IEnergyStorage} + a {@code simulate}
 * flag); these helpers take and return plain {@code Object}/{@code int}/{@code long} so test bodies stay
 * directive-free. Each call opens and closes its own root transaction, so a committed insert has already been
 * delivered by the time it returns.
 */
public final class FeHandlerAccess {

    private FeHandlerAccess() {}

    /** Inserts up to {@code amount} FE; commits only when {@code commit}. Returns what the handler accepted. */
    public static int insert(Object handler, int amount, boolean commit) {
        //? if >=1.21.10 {
        try (Transaction tx = Transaction.openRoot()) {
            int accepted = ((EnergyHandler) handler).insert(amount, tx);
            if (commit) {
                tx.commit();
            }
            return accepted;
        }
        //?} else {
        /*return ((IEnergyStorage) handler).receiveEnergy(amount, !commit);*/
        //?}
    }

    /** Extracts (and commits) up to {@code amount} FE. */
    public static int extract(Object handler, int amount) {
        //? if >=1.21.10 {
        try (Transaction tx = Transaction.openRoot()) {
            int taken = ((EnergyHandler) handler).extract(amount, tx);
            tx.commit();
            return taken;
        }
        //?} else {
        /*return ((IEnergyStorage) handler).extractEnergy(amount, false);*/
        //?}
    }

    public static long stored(Object handler) {
        //? if >=1.21.10 {
        return ((EnergyHandler) handler).getAmountAsLong();
        //?} else {
        /*return ((IEnergyStorage) handler).getEnergyStored();*/
        //?}
    }

    public static long capacity(Object handler) {
        //? if >=1.21.10 {
        return ((EnergyHandler) handler).getCapacityAsLong();
        //?} else {
        /*return ((IEnergyStorage) handler).getMaxEnergyStored();*/
        //?}
    }
}
