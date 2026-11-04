/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import javax.annotation.Nonnull;

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;

import buildcraft.platform.transfer.BCCapability;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCItemStorage;

/** Capability tokens for BuildCraft's internal capability routing.
 *
 * In NeoForge 1.21.11 the capability system uses typed BlockCapability tokens
 * rather than the old CapabilityInject/@CapabilityInject approach. These
 * constants let flow classes stay capability-agnostic while TilePipeHolder
 * routes them to the actual NeoForge level.getCapability() calls.
 *
 * For MJ capabilities BuildCraft defines its own tokens in MjAPI.
 * For items and fluids we wrap the NeoForge capability tokens. On 1.21.10+ those
 * are the Transfer-API tokens (Capabilities.Item/Fluid/Energy.BLOCK); on 1.21.1
 * they are the classic handler tokens (Capabilities.ItemHandler/FluidHandler/EnergyStorage.BLOCK). */
public class CapUtil {
    /** Token for item storages: the loader-neutral {@link BCCapability#ITEM}, which the pipe holder resolves through
     *  {@link buildcraft.platform.transfer.TransferAccess} to a {@link buildcraft.platform.transfer.BCItemStorage}. */
    @Nonnull
    public static final BCCapability<BCItemStorage> CAP_ITEMS = BCCapability.ITEM;

    /** Token for fluid storages: the loader-neutral {@link BCCapability#FLUID}, which the pipe holder resolves through
     *  {@link buildcraft.platform.transfer.TransferAccess} to a {@link buildcraft.platform.transfer.BCFluidStorage}. */
    @Nonnull
    public static final BCCapability<BCFluidStorage> CAP_FLUIDS = BCCapability.FLUID;

    //? if >=1.21.10 {
    /** Token for energy handler capability. Routes to Capabilities.Energy.BLOCK. */
    @Nonnull
    public static final Object CAP_ENERGY = Capabilities.Energy.BLOCK;
    //?} else {
    /*@Nonnull
    public static final Object CAP_ENERGY = Capabilities.EnergyStorage.BLOCK;*/
    //?}
}
