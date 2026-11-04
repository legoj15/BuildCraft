/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import net.minecraft.resources.Identifier;

import buildcraft.api.capability.BCCapability;

/**
 * The storage tokens: the three kinds of block capability the platform does not hand out as they are, but maps onto
 * the loader's own storage capability and adapts both ways ({@link TransferAccess#find}). Their ids name no loader
 * capability; every other {@link BCCapability} is looked up directly under its own id.
 */
public final class StorageTokens {
    private StorageTokens() {}

    /** Item storages: {@link BCItemStorage}. */
    public static final BCCapability<BCItemStorage> ITEM = BCCapability.of(
        Identifier.fromNamespaceAndPath("buildcraftunofficial", "platform_item_storage"), BCItemStorage.class);

    /** Fluid storages: {@link BCFluidStorage}. */
    public static final BCCapability<BCFluidStorage> FLUID = BCCapability.of(
        Identifier.fromNamespaceAndPath("buildcraftunofficial", "platform_fluid_storage"), BCFluidStorage.class);

    /** Energy (FE/RF) storages: {@link BCEnergyStorage}. */
    public static final BCCapability<BCEnergyStorage> ENERGY = BCCapability.of(
        Identifier.fromNamespaceAndPath("buildcraftunofficial", "platform_energy_storage"), BCEnergyStorage.class);

    /** Whether {@code token} is one of the three storage tokens (mapped and adapted, never looked up by id). */
    public static boolean isStorage(BCCapability<?> token) {
        return token == ITEM || token == FLUID || token == ENERGY;
    }
}
