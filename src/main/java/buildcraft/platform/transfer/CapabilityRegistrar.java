/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import buildcraft.api.capability.BCCapability;

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

    /** Exposes {@code type}'s energy storage per side ({@code side} is null for an unsided query). */
    <T extends BlockEntity> void blockEnergy(BlockEntityType<T> type, BlockProvider<T, BCEnergyStorage> provider);

    /**
     * Exposes {@code type}'s object for {@code capability} per side. A storage token ({@link StorageTokens}) goes to
     * the matching storage method; any other token becomes the loader's capability of the same id and type, its
     * objects handed out as they are (BuildCraft's MJ capabilities).
     */
    @SuppressWarnings("unchecked")
    default <T extends BlockEntity, S> void block(BCCapability<S> capability, BlockEntityType<T> type,
            BlockProvider<T, ? extends S> provider) {
        if (capability == StorageTokens.ITEM) {
            blockItems(type, (BlockProvider<T, BCItemStorage>) provider);
        } else if (capability == StorageTokens.FLUID) {
            blockFluids(type, (BlockProvider<T, BCFluidStorage>) provider);
        } else if (capability == StorageTokens.ENERGY) {
            blockEnergy(type, (BlockProvider<T, BCEnergyStorage>) provider);
        } else {
            blockDirect(capability, type, provider);
        }
    }

    /** {@link #block} for a token that is not a storage token: the loader capability of the same id and type. */
    <T extends BlockEntity, S> void blockDirect(BCCapability<S> capability, BlockEntityType<T> type,
            BlockProvider<T, ? extends S> provider);

    /**
     * Makes {@code item} a fluid container that can only be drained and is used up once empty, its fluid held in
     * {@code content}, the loader's own simple fluid content component (NeoForge: a {@code SimpleFluidContent}
     * component), up to {@code capacity} millibuckets. The fragile fluid shard.
     */
    void itemFluidsDrainOnlyConsumable(ItemLike item, Supplier<? extends DataComponentType<?>> content, int capacity);

    /** A block entity's storage for one side, or null if that side has none. */
    @FunctionalInterface
    interface BlockProvider<T, S> {
        S get(T tile, Direction side);
    }
}
