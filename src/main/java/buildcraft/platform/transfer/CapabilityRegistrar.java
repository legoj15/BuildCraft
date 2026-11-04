/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Where BuildCraft exposes its storages to other mods. A subsystem hands a registration callback to
 * {@link TransferServices#registerCapabilities}; the loader replays it at the right time (NeoForge: inside
 * {@code RegisterCapabilitiesEvent}, in call order), and each registration becomes the loader's own (a NeoForge
 * block capability, a Fabric {@code BlockApiLookup} provider), adapted from the BuildCraft storage type.
 */
public interface CapabilityRegistrar {
    /** Exposes {@code type}'s item storage per side ({@code side} is null for an unsided query). */
    <T extends BlockEntity> void blockItems(BlockEntityType<T> type, BlockProvider<T, BCItemStorage> provider);

    /** Exposes {@code type}'s fluid storage per side ({@code side} is null for an unsided query). */
    <T extends BlockEntity> void blockFluids(BlockEntityType<T> type, BlockProvider<T, BCFluidStorage> provider);

    /** A block entity's storage for one side, or null if that side has none. */
    @FunctionalInterface
    interface BlockProvider<T, S> {
        S get(T tile, Direction side);
    }
}
