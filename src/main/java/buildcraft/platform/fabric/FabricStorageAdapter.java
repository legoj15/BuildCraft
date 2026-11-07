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

import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import buildcraft.platform.transfer.BCStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/^*
 * The two-way adapter between a BuildCraft storage {@code B} over resource {@code R} and Fabric's {@code Storage<V>},
 * written once for items ({@link FabricItemStorages}) and fluids ({@link FabricFluidStorages}); each of those is a
 * subclass that supplies the resource and unit conversions and the few rules that differ. Each direction unwraps the
 * other's adapter. A BuildCraft storage goes out as a {@link SlottedStorage}, one {@link SingleSlotStorage} per index;
 * a slotted Fabric storage comes back index for index.
 *
 * <p><b>Gap.</b> A Fabric storage that is not slotted has no stable indices, so its view is one virtual slot: it shows
 * the first non-blank view's contents and capacity (the first view's when all are blank; no capacity at all when it has
 * no views, as the top of a composter, which only takes items in), and insert and extract go to the storage as a whole.
 * So a level gate reads such a storage as one slot, whatever it holds besides.
 ^/
abstract class FabricStorageAdapter<R, V extends TransferVariant<?>, B extends BCStorage<R>> {
    // ---- What a kind supplies ----

    abstract boolean isEmpty(R resource);

    abstract R empty();

    abstract V blank();

    /^* A BuildCraft resource naming {@code variant} (its amount is ignored), for a call into a BuildCraft storage. ^/
    abstract R resourceOf(V variant);

    /^* The variant of a non-empty BuildCraft resource. ^/
    abstract V variantOf(R resource);

    /^* The BuildCraft stack of {@code variant} holding a Fabric {@code amount} (empty if nothing is left of it). ^/
    abstract R stackOf(V variant, long amount);

    /^* A Fabric amount in BuildCraft units, rounded down and clamped to {@code int}. ^/
    abstract int toBuildCraftAmount(long amount);

    /^* A BuildCraft amount in Fabric units, exactly. ^/
    abstract long toFabricAmount(int amount);

    /^* The amount at {@code index}, in BuildCraft units. ^/
    abstract int amountAt(B storage, int index);

    /^* The resource a Fabric slot's capacity is asked for. ^/
    abstract R capacityProbe(B storage, int index);

    /^* A foreign capacity (BuildCraft units) as BuildCraft reports it for {@code resource}. ^/
    abstract int boundCapacity(int capacity, R resource);

    /^* A whole-storage insert into a BuildCraft storage, in the order this kind fills its indices. ^/
    abstract int insertAcross(B storage, R resource, int amount, BCTransactionContext transaction);

    /^* A whole-storage extract from a BuildCraft storage. ^/
    abstract int extractAcross(B storage, R resource, int amount, BCTransactionContext transaction);

    abstract B slottedView(SlottedStorage<V> storage);

    abstract B plainView(Storage<V> storage);

    /^* One call into a foreign storage: moves up to {@code amount} (Fabric units) inside {@code transaction}. ^/
    @FunctionalInterface
    interface Mover {
        long move(long amount, TransactionContext transaction);
    }

    /^*
     * Moves up to {@code amount} (BuildCraft units) through {@code mover} and returns how much moved, in BuildCraft
     * units. A kind whose Fabric amounts may not be a whole number of its own units overrides this, so that nothing is
     * booked that did not move.
     ^/
    int moveForeign(Mover mover, int amount, TransactionContext transaction) {
        return toBuildCraftAmount(mover.move(toFabricAmount(amount), transaction));
    }

    // ---- The adapter ----

    final Storage<V> toFabricStorage(B storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof View<?, ?> view && view.adapter == this) {
            return viewed(view);
        }
        return new ToFabric<>(this, storage);
    }

    final B fromFabricStorage(Storage<V> storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof ToFabric<?, ?, ?> adapter && adapter.kind == this) {
            return wrapped(adapter);
        }
        if (storage instanceof SlottedStorage<V> slotted) {
            return slottedView(slotted);
        }
        return plainView(storage);
    }

    // Both casts are checked by the identity test before them: an adapter of this kind holds this kind's types.
    @SuppressWarnings("unchecked")
    private Storage<V> viewed(View<?, ?> view) {
        return (Storage<V>) view.storage;
    }

    @SuppressWarnings("unchecked")
    private B wrapped(ToFabric<?, ?, ?> adapter) {
        return (B) adapter.storage;
    }

    private static final class ToFabric<R, V extends TransferVariant<?>, B extends BCStorage<R>>
        implements SlottedStorage<V> {
        final FabricStorageAdapter<R, V, B> kind;
        final B storage;

        ToFabric(FabricStorageAdapter<R, V, B> kind, B storage) {
            this.kind = kind;
            this.storage = storage;
        }

        @Override
        public int getSlotCount() {
            return storage.size();
        }

        @Override
        public SingleSlotStorage<V> getSlot(int slot) {
            return new Slot<>(kind, storage, slot);
        }

        @Override
        public long insert(V resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int amount = kind.toBuildCraftAmount(maxAmount);
            if (amount <= 0) {
                return 0;
            }
            return kind.toFabricAmount(kind.insertAcross(storage, kind.resourceOf(resource), amount,
                FabricTransfer.wrap(transaction)));
        }

        @Override
        public long extract(V resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int amount = kind.toBuildCraftAmount(maxAmount);
            if (amount <= 0) {
                return 0;
            }
            return kind.toFabricAmount(kind.extractAcross(storage, kind.resourceOf(resource), amount,
                FabricTransfer.wrap(transaction)));
        }

        @Override
        public Iterator<StorageView<V>> iterator() {
            List<StorageView<V>> views = new ArrayList<>(storage.size());
            for (int i = 0; i < storage.size(); i++) {
                views.add(new Slot<>(kind, storage, i));
            }
            return views.iterator();
        }
    }

    private record Slot<R, V extends TransferVariant<?>, B extends BCStorage<R>>(FabricStorageAdapter<R, V, B> kind,
        B storage, int index) implements SingleSlotStorage<V> {
        @Override
        public long insert(V resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int amount = kind.toBuildCraftAmount(maxAmount);
            return amount <= 0 ? 0 : kind.toFabricAmount(
                storage.insert(index, kind.resourceOf(resource), amount, FabricTransfer.wrap(transaction)));
        }

        @Override
        public long extract(V resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int amount = kind.toBuildCraftAmount(maxAmount);
            return amount <= 0 ? 0 : kind.toFabricAmount(
                storage.extract(index, kind.resourceOf(resource), amount, FabricTransfer.wrap(transaction)));
        }

        @Override
        public boolean isResourceBlank() {
            return kind.isEmpty(storage.getStack(index));
        }

        @Override
        public V getResource() {
            R stack = storage.getStack(index);
            return kind.isEmpty(stack) ? kind.blank() : kind.variantOf(stack);
        }

        @Override
        public long getAmount() {
            return kind.toFabricAmount(kind.amountAt(storage, index));
        }

        @Override
        public long getCapacity() {
            return kind.toFabricAmount(storage.getCapacity(index, kind.capacityProbe(storage, index)));
        }
    }

    /^* A BuildCraft view of a Fabric storage; a kind's concrete view also implements its BuildCraft storage type. ^/
    abstract static class View<R, V extends TransferVariant<?>> implements BCStorage<R> {
        final FabricStorageAdapter<R, V, ?> adapter;
        final Storage<V> storage;

        View(FabricStorageAdapter<R, V, ?> adapter, Storage<V> storage) {
            this.adapter = adapter;
            this.storage = storage;
        }

        @Override
        public final boolean isValid(int index, R resource) {
            // Gap: Fabric has no per-resource validity query, so this only says the index takes insertions at all.
            return !adapter.isEmpty(resource) && target(index).supportsInsertion();
        }

        @Override
        public final int insert(int index, R resource, int amount, BCTransactionContext transaction) {
            if (adapter.isEmpty(resource) || amount <= 0) {
                return 0;
            }
            V variant = adapter.variantOf(resource);
            Storage<V> target = target(index);
            return adapter.moveForeign((n, tx) -> target.insert(variant, n, tx), amount,
                FabricTransfer.unwrap(transaction));
        }

        @Override
        public final int extract(int index, R resource, int amount, BCTransactionContext transaction) {
            if (adapter.isEmpty(resource) || amount <= 0) {
                return 0;
            }
            V variant = adapter.variantOf(resource);
            Storage<V> target = target(index);
            return adapter.moveForeign((n, tx) -> target.extract(variant, n, tx), amount,
                FabricTransfer.unwrap(transaction));
        }

        /^* The Fabric storage a call on {@code index} goes to. ^/
        abstract Storage<V> target(int index);
    }

    /^* The view of a slotted Fabric storage, index for index. ^/
    abstract static class FromSlotted<R, V extends TransferVariant<?>> extends View<R, V> {
        private final SlottedStorage<V> slotted;

        FromSlotted(FabricStorageAdapter<R, V, ?> adapter, SlottedStorage<V> storage) {
            super(adapter, storage);
            this.slotted = storage;
        }

        @Override
        public final int size() {
            return slotted.getSlotCount();
        }

        @Override
        public final R getStack(int index) {
            SingleSlotStorage<V> slot = slotted.getSlot(index);
            return adapter.stackOf(slot.getResource(), slot.getAmount());
        }

        @Override
        public final int getCapacity(int index, R resource) {
            return adapter.boundCapacity(adapter.toBuildCraftAmount(slotted.getSlot(index).getCapacity()), resource);
        }

        @Override
        final Storage<V> target(int index) {
            return slotted.getSlot(index);
        }
    }

    /^* The one-virtual-slot view of a Fabric storage that is not slotted (see the class Javadoc's gap). ^/
    abstract static class FromPlain<R, V extends TransferVariant<?>> extends View<R, V> {
        FromPlain(FabricStorageAdapter<R, V, ?> adapter, Storage<V> storage) {
            super(adapter, storage);
        }

        @Override
        public final int size() {
            return 1;
        }

        /^* The view this slot shows: the first holding something, else the first at all (null if there is none). ^/
        private StorageView<V> shown() {
            for (StorageView<V> view : storage.nonEmptyViews()) {
                return view;
            }
            for (StorageView<V> view : storage) {
                return view;
            }
            return null;
        }

        @Override
        public final R getStack(int index) {
            StorageView<V> view = shown();
            return view == null ? adapter.empty() : adapter.stackOf(view.getResource(), view.getAmount());
        }

        @Override
        public final int getCapacity(int index, R resource) {
            // The shown view's own capacity: a level gate divides getStack by this, so both read the same view. With
            // no view at all (an insertion-only storage) there is nothing to measure, so a gate reads no space.
            StorageView<V> view = shown();
            return view == null ? 0 : adapter.boundCapacity(adapter.toBuildCraftAmount(view.getCapacity()), resource);
        }

        @Override
        final Storage<V> target(int index) {
            return storage;
        }
    }
}
*///?}
