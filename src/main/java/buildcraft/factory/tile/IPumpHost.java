/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

import buildcraft.api.mj.MjBattery;

/**
 * A miner a Pump can stand on. The Pump stops pumping its own column (the host is in the way) and drains the fluid
 * that stopped the host instead, on the host's power. See {@link TilePump}.
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

    /** The pump took a bucket from the blocking fluid's pool: the host should look at its column again. */
    void onBlockingFluidDrained();
}
