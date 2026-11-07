/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge && >=1.21.10 {
import javax.annotation.Nullable;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.platform.transfer.BCStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * The two-way adapter between a BuildCraft storage {@code B} over resource {@code R} and NeoForge's
 * {@code ResourceHandler<V>} (1.21.10+), written once for items ({@link NeoForgeItemStorages}) and fluids
 * ({@link NeoForgeFluidStorages}); each of those is a subclass that supplies the resource conversions and decides how
 * adapters are handed out (fresh, or one per wrapped object). Each direction unwraps the other's adapter, so a
 * BuildCraft storage that went out as a capability and came back through a lookup is the same object again. The
 * 1.21.1 classic handlers ({@code IItemHandler}, {@code IFluidHandler}) share no shape with each other and stay in the
 * kind classes.
 */
abstract class NeoForgeStorageAdapter<R, V extends Resource, B extends BCStorage<R>> {
    // ---- What a kind supplies ----

    abstract boolean isEmpty(R resource);

    /** The empty NeoForge resource. */
    abstract V blank();

    /** The NeoForge resource a non-empty BuildCraft resource names. */
    abstract V variantOf(R resource);

    /** The BuildCraft stack of {@code amount} of {@code resource} (empty for an empty resource or amount). */
    abstract R stackOf(V resource, int amount);

    /** The amount at {@code index}. */
    abstract long amountAt(B storage, int index);

    /** The NeoForge handler for a BuildCraft storage that is not a view of one: a {@link ToNeo} of this kind. */
    abstract ResourceHandler<V> outgoing(B storage);

    /** The BuildCraft view of a handler that is not one of this kind's adapters: a {@link View} of this kind. */
    abstract B incoming(ResourceHandler<V> handler);

    /** The resource a BuildCraft stack names, its amount dropped (the empty resource for an empty stack). */
    final V resourceOf(R stack) {
        return isEmpty(stack) ? blank() : variantOf(stack);
    }

    // ---- The adapter ----

    @Nullable
    final ResourceHandler<V> toNeoHandler(@Nullable B storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof View<?, ?, ?> view && view.kind == this) {
            return viewed(view);
        }
        return outgoing(storage);
    }

    @Nullable
    final B fromNeoHandler(@Nullable ResourceHandler<V> handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToNeo<?, ?, ?> adapter && adapter.kind == this) {
            return wrapped(adapter);
        }
        return incoming(handler);
    }

    // Both casts are checked by the identity test before them: an adapter of this kind holds this kind's types.
    @SuppressWarnings("unchecked")
    private ResourceHandler<V> viewed(View<?, ?, ?> view) {
        return (ResourceHandler<V>) view.handler;
    }

    @SuppressWarnings("unchecked")
    private B wrapped(ToNeo<?, ?, ?> adapter) {
        return (B) adapter.storage;
    }

    /** A BuildCraft storage seen as a NeoForge handler; a kind may subclass it to add the resource-wide calls. */
    static class ToNeo<R, V extends Resource, B extends BCStorage<R>> implements ResourceHandler<V> {
        final NeoForgeStorageAdapter<R, V, B> kind;
        final B storage;

        ToNeo(NeoForgeStorageAdapter<R, V, B> kind, B storage) {
            this.kind = kind;
            this.storage = storage;
        }

        @Override
        public final int size() {
            return storage.size();
        }

        @Override
        public final V getResource(int index) {
            return kind.resourceOf(storage.getStack(index));
        }

        @Override
        public final long getAmountAsLong(int index) {
            return kind.amountAt(storage, index);
        }

        @Override
        public final long getCapacityAsLong(int index, V resource) {
            return storage.getCapacity(index, kind.stackOf(resource, 1));
        }

        @Override
        public final boolean isValid(int index, V resource) {
            return storage.isValid(index, kind.stackOf(resource, 1));
        }

        @Override
        public final int insert(int index, V resource, int amount, TransactionContext transaction) {
            // No precondition check here: each BuildCraft storage answers an empty or negative offer itself, as it did
            // before the platform layer. Inventories answer 0; the fluid tanks and the Tank's column throw
            // (BCFluidTank.movesNothing); a fluid face that refuses this direction outright, or a pipe section, answers 0.
            return storage.insert(index, kind.stackOf(resource, 1), amount, NeoForgeTransfer.wrap(transaction));
        }

        @Override
        public final int extract(int index, V resource, int amount, TransactionContext transaction) {
            return storage.extract(index, kind.stackOf(resource, 1), amount, NeoForgeTransfer.wrap(transaction));
        }

        // Value equality over the storage, as the records this class replaced had.
        @Override
        public final boolean equals(Object other) {
            return other != null && other.getClass() == getClass() && ((ToNeo<?, ?, ?>) other).storage.equals(storage);
        }

        @Override
        public final int hashCode() {
            return storage.hashCode();
        }

        @Override
        public final String toString() {
            return "NeoForge view of " + storage;
        }
    }

    /** A BuildCraft view of a NeoForge handler; a kind's concrete view also implements its BuildCraft storage type. */
    abstract static class View<R, V extends Resource, B extends BCStorage<R>> implements BCStorage<R> {
        final NeoForgeStorageAdapter<R, V, B> kind;
        final ResourceHandler<V> handler;

        View(NeoForgeStorageAdapter<R, V, B> kind, ResourceHandler<V> handler) {
            this.kind = kind;
            this.handler = handler;
        }

        @Override
        public final int size() {
            return handler.size();
        }

        @Override
        public final R getStack(int index) {
            return kind.stackOf(handler.getResource(index), handler.getAmountAsInt(index));
        }

        @Override
        public final int getCapacity(int index, R resource) {
            return handler.getCapacityAsInt(index, kind.resourceOf(resource));
        }

        @Override
        public final boolean isValid(int index, R resource) {
            return !kind.isEmpty(resource) && handler.isValid(index, kind.variantOf(resource));
        }

        @Override
        public final int insert(int index, R resource, int amount, BCTransactionContext transaction) {
            if (kind.isEmpty(resource) || amount <= 0) {
                return 0;
            }
            return handler.insert(index, kind.variantOf(resource), amount, NeoForgeTransfer.unwrap(transaction));
        }

        @Override
        public final int extract(int index, R resource, int amount, BCTransactionContext transaction) {
            if (kind.isEmpty(resource) || amount <= 0) {
                return 0;
            }
            return handler.extract(index, kind.variantOf(resource), amount, NeoForgeTransfer.unwrap(transaction));
        }

        // Value equality over the handler, as the records this class replaced had.
        @Override
        public final boolean equals(Object other) {
            return other != null && other.getClass() == getClass() && ((View<?, ?, ?>) other).handler.equals(handler);
        }

        @Override
        public final int hashCode() {
            return handler.hashCode();
        }

        @Override
        public final String toString() {
            return "BuildCraft view of " + handler;
        }
    }
}
//?}
