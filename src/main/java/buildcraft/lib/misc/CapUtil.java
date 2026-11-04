/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import javax.annotation.Nonnull;

import buildcraft.api.capability.BCCapability;
import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.StorageTokens;

/** Capability tokens for BuildCraft's internal capability routing.
 *
 * These are the platform layer's loader-neutral storage tokens ({@link StorageTokens}), which let flow classes stay
 * capability-agnostic while TilePipeHolder resolves them through {@link buildcraft.platform.transfer.TransferAccess}
 * to BuildCraft views of the loader's item, fluid and energy handlers. For MJ capabilities BuildCraft defines its own
 * tokens in MjAPI. */
public class CapUtil {
    /** Token for item storages, resolved to a {@link BCItemStorage}. */
    @Nonnull
    public static final BCCapability<BCItemStorage> CAP_ITEMS = StorageTokens.ITEM;

    /** Token for fluid storages, resolved to a {@link BCFluidStorage}. */
    @Nonnull
    public static final BCCapability<BCFluidStorage> CAP_FLUIDS = StorageTokens.FLUID;

    /** Token for energy (FE/RF) storages, resolved to a {@link BCEnergyStorage}. */
    @Nonnull
    public static final BCCapability<BCEnergyStorage> CAP_ENERGY = StorageTokens.ENERGY;
}
