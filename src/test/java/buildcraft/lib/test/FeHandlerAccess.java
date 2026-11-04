/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCTransaction;

/**
 * Access to an energy handler for tests: a BuildCraft {@link BCEnergyStorage} (what BuildCraft's own pipes, stations
 * and machines hand out), or a NeoForge handler a capability lookup returned, which is seen through the platform's
 * adapter. These helpers take a plain {@code Object} and return {@code int}/{@code long}, so test bodies stay
 * directive-free. Each call opens and closes its own root transaction, so a committed insert has already been
 * delivered by the time it returns.
 */
public final class FeHandlerAccess {

    private FeHandlerAccess() {}

    private static BCEnergyStorage storage(Object handler) {
        if (handler instanceof BCEnergyStorage storage) {
            return storage;
        }
        // Fabric's lookup (TransferAccess.findEnergy) already hands out BuildCraft storages.
        //? if neoforge && >=1.21.10 {
        return buildcraft.platform.neoforge.NeoForgeEnergyStorages.fromNeo(
                (net.neoforged.neoforge.transfer.energy.EnergyHandler) handler);
        //?} elif neoforge {
        /*return buildcraft.platform.neoforge.NeoForgeEnergyStorages.fromNeo(
                (net.neoforged.neoforge.energy.IEnergyStorage) handler);*/
        //?} else {
        /*throw new IllegalArgumentException("not a BuildCraft energy storage: " + handler);*/
        //?}
    }

    /** Inserts up to {@code amount} FE; commits only when {@code commit}. Returns what the handler accepted. */
    public static int insert(Object handler, int amount, boolean commit) {
        if (amount < 0) {
            return 0; // the classic handlers answered 0; BuildCraft storages reject a negative amount
        }
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int accepted = storage(handler).insert(amount, tx);
            if (commit) {
                tx.commit();
            }
            return accepted;
        }
    }

    /** Extracts (and commits) up to {@code amount} FE. */
    public static int extract(Object handler, int amount) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int taken = storage(handler).extract(amount, tx);
            tx.commit();
            return taken;
        }
    }

    public static long stored(Object handler) {
        return storage(handler).getAmountAsLong();
    }

    public static long capacity(Object handler) {
        return storage(handler).getCapacityAsLong();
    }
}
