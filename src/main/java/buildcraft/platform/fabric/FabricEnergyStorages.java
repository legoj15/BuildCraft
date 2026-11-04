/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import team.reborn.energy.api.EnergyStorage;

import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.CapabilityRegistrar;

/^*
 * Energy on Fabric is Team Reborn Energy ({@code team_reborn_energy}), an optional mod BuildCraft compiles against but
 * does not ship: {@link FabricTransfer} reaches this class only when that mod is loaded, so its types are never
 * linked otherwise (without it, BuildCraft blocks expose and find no FE/RF on Fabric; the MJ capabilities are
 * unaffected). The two-way adapter between {@link BCEnergyStorage} and {@link EnergyStorage}; each direction unwraps
 * the other's adapter. Amounts are {@code long} there and {@code int} here: an offer above
 * {@link Integer#MAX_VALUE} is capped.
 ^/
final class FabricEnergyStorages {
    static final String MOD_ID = "team_reborn_energy";

    private FabricEnergyStorages() {}

    static <T extends BlockEntity> void register(BlockEntityType<T> type,
            CapabilityRegistrar.BlockProvider<T, BCEnergyStorage> provider) {
        EnergyStorage.SIDED.registerForBlockEntity((tile, side) -> toFabric(provider.get(tile, side)), type);
    }

    static BCEnergyStorage find(Level level, BlockPos pos, Direction side) {
        return fromFabric(EnergyStorage.SIDED.find(level, pos, side));
    }

    static EnergyStorage toFabric(BCEnergyStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromFabric view) {
            return view.handler;
        }
        return new ToFabric(storage);
    }

    static BCEnergyStorage fromFabric(EnergyStorage handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToFabric adapter) {
            return adapter.storage;
        }
        return new FromFabric(handler);
    }

    private static int capped(long amount) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
    }

    private record ToFabric(BCEnergyStorage storage) implements EnergyStorage {
        @Override
        public boolean supportsInsertion() {
            return storage.canInsert();
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            return storage.insert(capped(maxAmount), FabricTransfer.wrap(transaction));
        }

        @Override
        public boolean supportsExtraction() {
            return storage.canExtract();
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            return storage.extract(capped(maxAmount), FabricTransfer.wrap(transaction));
        }

        @Override
        public long getAmount() {
            return storage.getAmountAsLong();
        }

        @Override
        public long getCapacity() {
            return storage.getCapacityAsLong();
        }
    }

    private record FromFabric(EnergyStorage handler) implements BCEnergyStorage {
        @Override
        public long getAmountAsLong() {
            return handler.getAmount();
        }

        @Override
        public long getCapacityAsLong() {
            return handler.getCapacity();
        }

        @Override
        public int insert(int amount, BCTransactionContext transaction) {
            return amount <= 0 ? 0 : capped(handler.insert(amount, FabricTransfer.unwrap(transaction)));
        }

        @Override
        public int extract(int amount, BCTransactionContext transaction) {
            return amount <= 0 ? 0 : capped(handler.extract(amount, FabricTransfer.unwrap(transaction)));
        }

        @Override
        public boolean canInsert() {
            return handler.supportsInsertion();
        }

        @Override
        public boolean canExtract() {
            return handler.supportsExtraction();
        }
    }
}
*///?}
