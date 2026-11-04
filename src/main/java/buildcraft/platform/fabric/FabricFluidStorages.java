/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/^*
 * The two-way adapter between {@link BCFluidStorage} and Fabric's {@code Storage<FluidVariant>}. Each direction
 * unwraps the other's adapter. Amounts cross through {@link FabricFluids}: millibuckets go out exactly as droplets,
 * droplets come back floored to whole millibuckets, and an offer to a BuildCraft storage is floored too (so less than
 * one millibucket is refused).
 *
 * <p><b>Gap.</b> A Fabric storage that is not slotted has no stable indices, so its view is one virtual tank: it shows
 * the first non-blank view's contents, and insert and extract go to the storage as a whole.
 ^/
public final class FabricFluidStorages {
    private FabricFluidStorages() {}

    public static Storage<FluidVariant> toFabric(BCFluidStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromSlotted view) {
            return view.storage;
        }
        if (storage instanceof FromPlain view) {
            return view.storage;
        }
        return new ToFabric(storage);
    }

    public static BCFluidStorage fromFabric(Storage<FluidVariant> storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof ToFabric adapter) {
            return adapter.storage;
        }
        if (storage instanceof SlottedStorage<FluidVariant> slotted) {
            return new FromSlotted(slotted);
        }
        return new FromPlain(storage);
    }

    static BCFluidStack stackOf(FluidVariant variant) {
        return variant.isBlank() ? BCFluidStack.EMPTY : new BCFluidStack(variant.getFluid(), 1, variant.getComponentsPatch());
    }

    private static final class ToFabric implements SlottedStorage<FluidVariant> {
        final BCFluidStorage storage;

        ToFabric(BCFluidStorage storage) {
            this.storage = storage;
        }

        @Override
        public int getSlotCount() {
            return storage.size();
        }

        @Override
        public SingleSlotStorage<FluidVariant> getSlot(int slot) {
            return new Slot(storage, slot);
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int mb = FabricFluids.toMillibuckets(maxAmount);
            if (mb <= 0) {
                return 0;
            }
            return FabricFluids.toDroplets(storage.insert(stackOf(resource), mb, FabricTransfer.wrap(transaction)));
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int mb = FabricFluids.toMillibuckets(maxAmount);
            if (mb <= 0) {
                return 0;
            }
            return FabricFluids.toDroplets(storage.extract(stackOf(resource), mb, FabricTransfer.wrap(transaction)));
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            List<StorageView<FluidVariant>> views = new ArrayList<>(storage.size());
            for (int i = 0; i < storage.size(); i++) {
                views.add(new Slot(storage, i));
            }
            return views.iterator();
        }
    }

    private record Slot(BCFluidStorage storage, int index) implements SingleSlotStorage<FluidVariant> {
        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int mb = FabricFluids.toMillibuckets(maxAmount);
            return mb <= 0 ? 0 : FabricFluids.toDroplets(
                storage.insert(index, stackOf(resource), mb, FabricTransfer.wrap(transaction)));
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int mb = FabricFluids.toMillibuckets(maxAmount);
            return mb <= 0 ? 0 : FabricFluids.toDroplets(
                storage.extract(index, stackOf(resource), mb, FabricTransfer.wrap(transaction)));
        }

        @Override
        public boolean isResourceBlank() {
            return storage.getStack(index).isEmpty();
        }

        @Override
        public FluidVariant getResource() {
            return FabricFluids.toVariant(storage.getStack(index));
        }

        @Override
        public long getAmount() {
            return FabricFluids.toDroplets(storage.getAmount(index));
        }

        @Override
        public long getCapacity() {
            return FabricFluids.toDroplets(storage.getCapacity(index, BCFluidStack.EMPTY));
        }
    }

    private static final class FromSlotted implements BCFluidStorage {
        final SlottedStorage<FluidVariant> storage;

        FromSlotted(SlottedStorage<FluidVariant> storage) {
            this.storage = storage;
        }

        @Override
        public int size() {
            return storage.getSlotCount();
        }

        @Override
        public BCFluidStack getStack(int index) {
            SingleSlotStorage<FluidVariant> slot = storage.getSlot(index);
            return FabricFluids.fromVariant(slot.getResource(), slot.getAmount());
        }

        @Override
        public int getCapacity(int index, BCFluidStack resource) {
            return FabricFluids.toMillibuckets(storage.getSlot(index).getCapacity());
        }

        @Override
        public boolean isValid(int index, BCFluidStack resource) {
            // Gap: Fabric has no per-resource validity query, so this only says the tank takes insertions at all.
            return !resource.isEmpty() && storage.getSlot(index).supportsInsertion();
        }

        @Override
        public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return FabricFluids.toMillibuckets(storage.getSlot(index).insert(FabricFluids.toVariant(resource),
                FabricFluids.toDroplets(amount), FabricTransfer.unwrap(transaction)));
        }

        @Override
        public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return FabricFluids.toMillibuckets(storage.getSlot(index).extract(FabricFluids.toVariant(resource),
                FabricFluids.toDroplets(amount), FabricTransfer.unwrap(transaction)));
        }
    }

    private static final class FromPlain implements BCFluidStorage {
        final Storage<FluidVariant> storage;

        FromPlain(Storage<FluidVariant> storage) {
            this.storage = storage;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public BCFluidStack getStack(int index) {
            for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
                return FabricFluids.fromVariant(view.getResource(), view.getAmount());
            }
            return BCFluidStack.EMPTY;
        }

        @Override
        public int getCapacity(int index, BCFluidStack resource) {
            for (StorageView<FluidVariant> view : storage) {
                return FabricFluids.toMillibuckets(view.getCapacity());
            }
            return 0;
        }

        @Override
        public boolean isValid(int index, BCFluidStack resource) {
            return !resource.isEmpty() && storage.supportsInsertion();
        }

        @Override
        public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return FabricFluids.toMillibuckets(storage.insert(FabricFluids.toVariant(resource),
                FabricFluids.toDroplets(amount), FabricTransfer.unwrap(transaction)));
        }

        @Override
        public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return FabricFluids.toMillibuckets(storage.extract(FabricFluids.toVariant(resource),
                FabricFluids.toDroplets(amount), FabricTransfer.unwrap(transaction)));
        }
    }
}
*///?}
