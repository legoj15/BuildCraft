/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiCache;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCLookupCache;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.CapabilityRegistrar;
import buildcraft.platform.transfer.TransferServices;

/^*
 * {@link TransferServices} on Fabric: a {@link BCTransaction} is a Fabric {@link Transaction} and a BuildCraft journal
 * is a {@link SnapshotParticipant}, so BuildCraft state joins the transactions other mods open (an adapter wraps the
 * {@link TransactionContext} it is handed with {@link #wrap}).
 *
 * <p>Fabric's {@code onFinalCommit} does not pass the original state, so the participant keeps the first snapshot
 * taken since the outer transaction opened and hands that to {@code onRootCommit}, matching NeoForge.
 *
 * <p><b>Lookups.</b> Items go through {@link ItemStorage#SIDED}; registrations become
 * {@code registerForBlockEntity} providers at once, since Fabric registers content eagerly. Gap: Fabric has no
 * standard entity item lookup, so {@link #findItem(Entity)} finds nothing.
 ^/
public final class FabricTransfer implements TransferServices {
    @Override
    public BCTransaction openRoot() {
        return new Owned(Transaction.openOuter());
    }

    @Override
    public <S> JournalHandle newJournal(JournalCallbacks<S> callbacks) {
        Participant<S> participant = new Participant<>(callbacks);
        return tx -> participant.updateSnapshots(unwrap(tx));
    }

    @Override
    public void registerCapabilities(Consumer<CapabilityRegistrar> registration) {
        registration.accept(new CapabilityRegistrar() {
            @Override
            public <T extends BlockEntity> void blockItems(BlockEntityType<T> type,
                    BlockProvider<T, BCItemStorage> provider) {
                ItemStorage.SIDED.registerForBlockEntity(
                    (tile, side) -> FabricItemStorages.toFabric(provider.get(tile, side)), type);
            }
        });
    }

    @Override
    public BCItemStorage findItem(Level level, BlockPos pos, Direction side) {
        return FabricItemStorages.fromFabric(ItemStorage.SIDED.find(level, pos, side));
    }

    @Override
    public BCItemStorage findItem(Entity entity) {
        return null; // Gap: no standard Fabric entity item lookup (see the class notes).
    }

    @Override
    public Object toLoaderItemHandler(BCItemStorage storage) {
        return FabricItemStorages.toFabric(storage);
    }

    @Override
    public BCLookupCache<BCItemStorage> itemCache(ServerLevel level, BlockPos pos, Direction side) {
        BlockApiCache<Storage<ItemVariant>, Direction> cache = BlockApiCache.create(ItemStorage.SIDED, level, pos);
        return new BCLookupCache<>() {
            private Storage<ItemVariant> handler;
            private BCItemStorage view;

            @Override
            public BCItemStorage get() {
                Storage<ItemVariant> current = cache.find(side);
                if (current != handler) {
                    handler = current;
                    view = FabricItemStorages.fromFabric(current);
                }
                return view;
            }
        };
    }

    /^* The BuildCraft view of a Fabric transaction someone else opened: it can be nested into, never committed. ^/

    public static BCTransactionContext wrap(TransactionContext context) {
        return new Borrowed(context);
    }

    /^* The Fabric transaction behind a BuildCraft one. ^/
    public static TransactionContext unwrap(BCTransactionContext transaction) {
        if (transaction instanceof Owned owned) {
            return owned.tx;
        }
        if (transaction instanceof Borrowed borrowed) {
            return borrowed.context;
        }
        throw new IllegalArgumentException("Not a Fabric-backed transaction: " + transaction);
    }

    private record Borrowed(TransactionContext context) implements BCTransactionContext {
        @Override
        public int depth() {
            return context.nestingDepth();
        }

        @Override
        public BCTransaction openNested() {
            return new Owned(context.openNested());
        }
    }

    private static final class Owned implements BCTransaction {
        final Transaction tx;
        private boolean closed;

        Owned(Transaction tx) {
            this.tx = tx;
        }

        @Override
        public int depth() {
            return tx.nestingDepth();
        }

        @Override
        public BCTransaction openNested() {
            return new Owned(tx.openNested());
        }

        @Override
        public void commit() {
            closed = true;
            tx.commit();
        }

        @Override
        public void close() {
            // Fabric's close() throws on an already-closed transaction; the BC contract makes it idempotent.
            if (!closed) {
                closed = true;
                tx.close();
            }
        }
    }

    private static final class Participant<S> extends SnapshotParticipant<S> {
        private static final Object UNSET = new Object();

        private final JournalCallbacks<S> callbacks;
        private Object original = UNSET;

        Participant(JournalCallbacks<S> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        protected S createSnapshot() {
            S snapshot = callbacks.createSnapshot();
            if (original == UNSET) {
                original = snapshot;
            }
            return snapshot;
        }

        @Override
        protected void readSnapshot(S snapshot) {
            callbacks.revertToSnapshot(snapshot);
        }

        @Override
        public void onClose(TransactionContext transaction, TransactionContext.Result result) {
            super.onClose(transaction, result);
            if (transaction.nestingDepth() == 0 && result.wasAborted()) {
                original = UNSET;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void onFinalCommit() {
            Object state = original;
            original = UNSET;
            callbacks.onRootCommit((S) state);
        }
    }
}
*///?}
