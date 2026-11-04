/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The loader's transfer machinery. Common code uses {@link BCTransaction}, {@link BCSnapshotJournal} and
 * {@link TransferAccess} rather than calling this directly; it is the seam those delegate to.
 */
public interface TransferServices {
    /** Opens a root transaction on the current thread (throws if one is already open there). */
    BCTransaction openRoot();

    /** A loader journal driving {@code callbacks}. */
    <S> JournalHandle newJournal(JournalCallbacks<S> callbacks);

    /**
     * Queues {@code registration} to run when the loader takes storage registrations (NeoForge: the
     * {@code RegisterCapabilitiesEvent} of the mod being constructed). Call it from mod construction.
     */
    void registerCapabilities(Consumer<CapabilityRegistrar> registration);

    /** The item storage of the block at {@code pos}, seen from {@code side} (null: unsided), or null if none. */
    BCItemStorage findItem(Level level, BlockPos pos, Direction side);

    /** The whole inventory of {@code entity}, or null if it has none (or the loader has no entity lookup). */
    BCItemStorage findItem(Entity entity);

    /** A cached {@link #findItem(Level, BlockPos, Direction)} for one position and side. */
    BCLookupCache<BCItemStorage> itemCache(ServerLevel level, BlockPos pos, Direction side);

    /**
     * The loader's own item handler for a BuildCraft storage (NeoForge 1.21.10+: a {@code ResourceHandler<ItemResource>};
     * 1.21.1: an {@code IItemHandler}; Fabric: a {@code Storage<ItemVariant>}). Interim: only common code that still
     * calls a loader fluid-container API on an item slot uses it, until that code moves behind the fluid seam.
     */
    Object toLoaderItemHandler(BCItemStorage storage);

    /** The loader journal behind one {@link BCSnapshotJournal}. */
    interface JournalHandle {
        void updateSnapshots(BCTransactionContext transaction);
    }

    /** What a loader journal calls back into: the {@link BCSnapshotJournal} methods, made reachable. */
    interface JournalCallbacks<S> {
        S createSnapshot();

        void revertToSnapshot(S snapshot);

        void onRootCommit(S originalState);
    }
}
