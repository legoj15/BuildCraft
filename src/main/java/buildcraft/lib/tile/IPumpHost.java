/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;

import buildcraft.api.mj.MjBattery;

/**
 * A miner a Pump can stand on (the Mining Well and the Quarry). The Pump stops pumping its own column (the host
 * is in the way) and drains the fluid that stopped the host instead, on the host's power.
 * See {@code buildcraft.factory.tile.TilePump}.
 */
public interface IPumpHost {

    /** The fluid cell the host stopped at, or null while it is not stopped by fluid. Server side; called on every
     *  rebuild and every 30 ticks by an idle pump, so it must be cheap (a cached value, not a scan). */
    @Nullable
    BlockPos getBlockingFluid();

    /** The battery the pump draws on once its own is empty, while the host is not {@link #hasWork() working}. */
    MjBattery getBattery();

    /** Whether the host is digging (and so keeps its power to itself). */
    boolean hasWork();

    /** The host's owner, or null when unowned: a pump borrows power only from a host with its own owner. */
    @Nullable
    GameProfile getOwner();

    /** The pump is short of power for a bucket and is about to draw on the host's battery. A host that stops taking
     *  power once it has no work of its own takes it again for a while, so its battery does not run dry under the
     *  pump. */
    default void onPumpWantsPower() {
    }

    /** The pump took a bucket from the blocking fluid's pool: the host should look at its column again. */
    void onBlockingFluidDrained();
}
