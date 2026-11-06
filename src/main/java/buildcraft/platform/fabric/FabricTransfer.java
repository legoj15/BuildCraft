/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiCache;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import buildcraft.api.capability.BCCapability;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCLookupCache;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;
import buildcraft.platform.transfer.CapabilityRegistrar;
import buildcraft.platform.transfer.StorageTokens;
import buildcraft.platform.transfer.TransferServices;

/^*
 * {@link TransferServices} on Fabric: a {@link BCTransaction} is a Fabric {@link Transaction} and a BuildCraft journal
 * is a {@link SnapshotParticipant}, so BuildCraft state joins the transactions other mods open (an adapter wraps the
 * {@link TransactionContext} it is handed with {@link #wrap}).
 *
 * <p>Fabric's {@code onFinalCommit} does not pass the original state, so the participant mirrors
 * {@code SnapshotParticipant}'s own snapshot table, one entry per nesting depth, and hands the snapshot that reaches
 * depth 0 to {@code onRootCommit}: the state before the first change of the outer transaction, as on NeoForge. A
 * snapshot only a nested transaction took, and then aborted, leaves nothing behind.
 *
 * <p><b>Root-commit callbacks.</b> Fabric runs {@code onFinalCommit} while the outermost transaction is still closing,
 * where no transaction can be opened; NeoForge runs {@code onRootCommit} after the root has closed, and a callback
 * there may open one. A root BuildCraft opened itself ({@link #openRoot}) therefore queues its callbacks and runs them
 * once Fabric's close has returned ({@link RootCommits}). Gap: a root another mod opened gives BuildCraft no such
 * moment, so the callbacks of BuildCraft journals it commits run inside that close, and one that opens a transaction
 * there throws.
 *
 * <p><b>Lookups.</b> Items go through {@link ItemStorage#SIDED}; registrations become
 * {@code registerForBlockEntity} providers at once, since Fabric registers content eagerly. Fabric has no standard
 * entity item lookup, so {@link #findItem(Entity)} reads BuildCraft's own ({@link FabricEntityItems}).
 *
 * <p><b>Energy</b> is Team Reborn Energy's {@code EnergyStorage.SIDED}, used only when that optional mod is loaded
 * ({@link FabricEnergyStorages}); without it BuildCraft exposes and finds no FE/RF here. Every other
 * {@link BCCapability} (the MJ tokens) is a {@code BlockApiLookup} of the same id and type, keyed on
 * {@link Direction}, its objects handed out as they are ({@link #lookup}).
 ^/
public final class FabricTransfer implements TransferServices {
    @Override
    public BCTransaction openRoot() {
        return Owned.root(Transaction.openOuter());
    }

    @Override
    // getCurrentUnsafe is deprecated only as a hint to pass the context instead; a simulate-flag call has none to
    // pass, which is the case it exists for (NeoForge's getCurrentOpenedTransaction, likewise).
    @SuppressWarnings("deprecation")
    public BCTransaction openInCurrent() {
        TransactionContext current = Transaction.getCurrentUnsafe();
        return current == null ? Owned.root(Transaction.openOuter()) : Owned.nested(current.openNested());
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

            @Override
            public <T extends BlockEntity> void blockFluids(BlockEntityType<T> type,
                    BlockProvider<T, BCFluidStorage> provider) {
                FluidStorage.SIDED.registerForBlockEntity(
                    (tile, side) -> FabricFluidStorages.toFabric(provider.get(tile, side)), type);
            }

            @Override
            public <T extends BlockEntity> void blockEnergy(BlockEntityType<T> type,
                    BlockProvider<T, BCEnergyStorage> provider) {
                if (energyLoaded()) {
                    FabricEnergyStorages.register(type, provider);
                }
            }

            @Override
            public <T extends BlockEntity, S> void blockDirect(BCCapability<S> capability, BlockEntityType<T> type,
                    BlockProvider<T, ? extends S> provider) {
                lookup(capability).registerForBlockEntity(provider::get, type);
            }

            @Override
            public void itemFluidsDrainOnlyConsumable(ItemLike item, Supplier<? extends DataComponentType<?>> content,
                    int capacity) {
                FluidStorage.ITEM.registerForItems(
                    (stack, context) -> new FabricDrainOnlyFluidItem(context, content.get(), capacity), item);
            }
        });
    }

    private static final Map<BCCapability<?>, BlockApiLookup<?, Direction>> LOOKUPS = new ConcurrentHashMap<>();

    /^* The Fabric lookup of a token's id and type, keyed on Direction (never a storage token). ^/
    @SuppressWarnings("unchecked")
    static <S> BlockApiLookup<S, Direction> lookup(BCCapability<S> capability) {
        if (StorageTokens.isStorage(capability)) {
            throw new IllegalArgumentException("A storage token has no direct lookup: " + capability);
        }
        return (BlockApiLookup<S, Direction>) LOOKUPS.computeIfAbsent(capability,
            token -> BlockApiLookup.get(token.id(), token.type(), Direction.class));
    }

    private static boolean energyLoaded() {
        return FabricLoader.getInstance().isModLoaded(FabricEnergyStorages.MOD_ID);
    }

    @Override
    public BCEnergyStorage findEnergy(Level level, BlockPos pos, Direction side) {
        return energyLoaded() ? FabricEnergyStorages.find(level, pos, side) : null;
    }

    @Override
    public <T> T findDirect(BCCapability<T> capability, Level level, BlockPos pos, Direction side) {
        return lookup(capability).find(level, pos, side);
    }

    @Override
    public BCItemStorage findItem(Level level, BlockPos pos, Direction side) {
        return FabricItemStorages.fromFabric(ItemStorage.SIDED.find(level, pos, side));
    }

    @Override
    public BCItemStorage findItem(Entity entity) {
        Storage<ItemVariant> storage = FabricEntityItems.LOOKUP.find(entity, null);
        return storage == null ? null : FabricItemStorages.fromFabric(storage);
    }


    @Override
    public BCLookupCache<BCItemStorage> itemCache(ServerLevel level, BlockPos pos, Direction side) {
        BlockApiCache<Storage<ItemVariant>, Direction> cache = BlockApiCache.create(ItemStorage.SIDED, level, pos);
        return new BCLookupCache<>() {
            private Storage<ItemVariant> handler;
            private BCItemStorage view;

            @Override
            public BCItemStorage get() {
                if (!level.isLoaded(pos)) {
                    // NeoForge's cache answers null for an unloaded position; Fabric's would load the chunk.
                    handler = null;
                    view = null;
                    return null;
                }
                Storage<ItemVariant> current = cache.find(side);
                if (current != handler) {
                    handler = current;
                    view = FabricItemStorages.fromFabric(current);
                }
                return view;
            }
        };
    }

    @Override
    public BCFluidStorage findFluid(Level level, BlockPos pos, Direction side) {
        return FabricFluidStorages.fromFabric(FluidStorage.SIDED.find(level, pos, side));
    }

    @Override
    public BCLookupCache<BCFluidStorage> fluidCache(ServerLevel level, BlockPos pos, Direction side) {
        BlockApiCache<Storage<FluidVariant>, Direction> cache = BlockApiCache.create(FluidStorage.SIDED, level, pos);
        return new BCLookupCache<>() {
            private Storage<FluidVariant> handler;
            private BCFluidStorage view;

            @Override
            public BCFluidStorage get() {
                if (!level.isLoaded(pos)) {
                    // NeoForge's cache answers null for an unloaded position; Fabric's would load the chunk.
                    handler = null;
                    view = null;
                    return null;
                }
                Storage<FluidVariant> current = cache.find(side);
                if (current != handler) {
                    handler = current;
                    view = FabricFluidStorages.fromFabric(current);
                }
                return view;
            }
        };
    }

    @Override
    public BCFluidStack getFluidContained(ItemStack stack) {
        if (stack.isEmpty()) {
            return BCFluidStack.EMPTY;
        }
        Storage<FluidVariant> container = ContainerItemContext.withConstant(stack.copyWithCount(1)).find(FluidStorage.ITEM);
        if (container != null) {
            for (StorageView<FluidVariant> view : container.nonEmptyViews()) {
                return FabricFluids.fromVariant(view.getResource(), view.getAmount());
            }
        }
        return BCFluidStack.EMPTY;
    }

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return !stack.isEmpty()
            && ContainerItemContext.withConstant(stack.copyWithCount(1)).find(FluidStorage.ITEM) != null;
    }

    @Override
    public boolean drainContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        Storage<FluidVariant> container = slotContainer(slots, slot);
        return container != null && moveAll(container, FabricFluidStorages.toFabric(tank));
    }

    @Override
    public boolean fillContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        Storage<FluidVariant> container = slotContainer(slots, slot);
        return container != null && moveAll(FabricFluidStorages.toFabric(tank), container);
    }

    /^* The fluid storage of the container in one slot, its swapped item written back to that same slot. ^/
    private static Storage<FluidVariant> slotContainer(BCItemStorage slots, int slot) {
        if (!(FabricItemStorages.toFabric(slots) instanceof SlottedStorage<ItemVariant> slotted)) {
            return null;
        }
        return ContainerItemContext.ofSingleSlot(slotted.getSlot(slot)).find(FluidStorage.ITEM);
    }

    private static boolean moveAll(Storage<FluidVariant> from, Storage<FluidVariant> to) {
        // A root of BuildCraft's own, so the root-commit callbacks of the journals it touched run after the close.
        try (Owned root = Owned.root(Transaction.openOuter())) {
            if (StorageUtil.move(from, to, v -> true, Long.MAX_VALUE, root.tx) > 0) {
                root.commit();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean interactWithHeldContainer(Player player, InteractionHand hand, BlockPos pos, BCFluidStorage tank) {
        if (player.getItemInHand(hand).isEmpty()) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        // Fabric's own right-click helper: it swaps the held item, plays the bucket sounds and spares creative items.
        return FluidStorageUtil.interactWithFluidStorage(FabricFluidStorages.toFabric(tank), player, hand);
    }

    @Override
    public boolean exchangeWithCarriedContainer(Player player, BCFluidStorage tank) {
        ItemStack carried = player.containerMenu.getCarried();
        Storage<FluidVariant> tankStorage = FabricFluidStorages.toFabric(tank);
        if (player.getAbilities().instabuild) {
            // Creative: the cursor item never changes. A full container fills the tank, an empty one drains a bucket.
            if (!isFluidContainer(carried)) {
                return false;
            }
            BCFluidStack contained = getFluidContained(carried);
            try (BCTransaction tx = openRoot()) {
                int moved;
                if (!contained.isEmpty()) {
                    moved = tank.insert(0, contained, contained.getAmount(), tx);
                } else {
                    BCFluidStack inTank = tank.getStack(0);
                    moved = inTank.isEmpty() ? 0 : tank.extract(0, inTank, Math.min(1000, inTank.getAmount()), tx);
                }
                if (moved > 0) {
                    tx.commit();
                }
            }
            return true;
        }
        Storage<FluidVariant> container =
            ContainerItemContext.ofPlayerCursor(player, player.containerMenu).find(FluidStorage.ITEM);
        if (container == null) {
            return false;
        }
        if (moveAll(container, tankStorage) || moveAll(tankStorage, container)) {
            return true;
        }
        // The cursor flow is one-shot per bucket: fall back to filling an empty container in the inventory.
        if (tank.getStack(0).isEmpty()) {
            return false;
        }
        PlayerInventoryStorage inventory = PlayerInventoryStorage.of(player);
        for (int i = 0; i < inventory.getSlotCount(); i++) {
            if (inventory.getSlot(i).isResourceBlank()) {
                continue;
            }
            Storage<FluidVariant> slotContainer =
                ContainerItemContext.ofPlayerSlot(player, inventory.getSlot(i)).find(FluidStorage.ITEM);
            if (slotContainer == null || StorageUtil.findStoredResource(slotContainer) != null) {
                continue; // only fill empty containers, never swap full ones
            }
            if (moveAll(tankStorage, slotContainer)) {
                return true;
            }
        }
        return false;
    }


    /^* The BuildCraft view of a Fabric transaction someone else opened: it can be nested into, never committed. ^/
    public static BCTransactionContext wrap(TransactionContext context) {
        return new Borrowed(context);
    }

    // The NeoForge twin (NeoForgeTransfer: unwrap, Borrowed, Owned) is the same shape over NeoForge's Transaction; a new
    // kind of BCTransaction has to be taught to both.
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
            return Owned.nested(context.openNested());
        }
    }

    /^*
     * The root-commit callbacks of one thread's BuildCraft-owned root. Fabric calls a journal's {@code onFinalCommit}
     * from the outermost transaction's close, where its manager still counts that transaction as open: opening a
     * transaction (or reading the current one) there throws. NeoForge runs {@code onRootCommit} after the root has
     * closed, and BuildCraft code relies on being able to open a transaction in one (the contract
     * {@code BCTransactionTester.rootCommitCallbackRunsOutsideTheTransaction} pins). So while a root BuildCraft opened
     * is open on the thread, its callbacks wait here and run when Fabric's close has returned, in order, a callback's
     * own root-commit callbacks after those already waiting (as NeoForge's queue does).
     ^/
    private static final class RootCommits {
        private static final ThreadLocal<RootCommits> THREAD = ThreadLocal.withInitial(RootCommits::new);

        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        private boolean owned;
        private boolean running;

        /^* Runs {@code callback} once the BuildCraft-owned root has closed; at once if the root is another mod's. ^/
        static void deliver(Runnable callback) {
            RootCommits state = THREAD.get();
            if (state.owned) {
                state.pending.add(callback);
            } else {
                callback.run();
            }
        }

        static void rootOpened() {
            THREAD.get().owned = true;
        }

        /^* The BuildCraft-owned root has closed: runs what it queued. ^/
        static void rootClosed() {
            RootCommits state = THREAD.get();
            state.owned = false;
            if (state.running) {
                return; // a callback's own root: the loop below picks its callbacks up
            }
            state.running = true;
            RuntimeException error = null;
            try {
                for (Runnable callback = state.pending.poll(); callback != null; callback = state.pending.poll()) {
                    try {
                        callback.run();
                    } catch (RuntimeException e) {
                        if (error == null) {
                            error = new RuntimeException("Error while closing a transaction", e);
                        } else {
                            error.addSuppressed(e);
                        }
                    }
                }
            } finally {
                state.running = false;
            }
            if (error != null) {
                throw error;
            }
        }
    }

    private static final class Owned implements BCTransaction {
        final Transaction tx;
        /^* Whether BuildCraft opened this as the thread's root, so its root-commit callbacks wait for the close. ^/
        private final boolean root;
        private boolean closed;

        private Owned(Transaction tx, boolean root) {
            this.tx = tx;
            this.root = root;
        }

        static Owned nested(Transaction tx) {
            return new Owned(tx, false);
        }

        static Owned root(Transaction tx) {
            RootCommits.rootOpened();
            return new Owned(tx, true);
        }

        @Override
        public int depth() {
            return tx.nestingDepth();
        }

        @Override
        public BCTransaction openNested() {
            return nested(tx.openNested());
        }

        @Override
        public void commit() {
            end(true);
        }

        @Override
        public void close() {
            // Fabric keeps one Transaction object per nesting depth and reuses it, so its own close() on a stale
            // reference (after a later transaction took the depth) would abort that later one. Once this has ended,
            // the BC contract (a close after commit is harmless) holds by never reaching Fabric again.
            if (!closed) {
                end(false);
            }
        }

        private void end(boolean commit) {
            try {
                if (commit) {
                    tx.commit();
                } else {
                    tx.close();
                }
            } catch (IllegalStateException rejected) {
                // Fabric checks the call (this is the current transaction, on its thread) before it changes anything,
                // so the transaction is still open and stays open for a close() to abort. A failure of a close
                // callback is wrapped in a plain RuntimeException, and comes after the transaction has closed.
                throw rejected;
            } catch (RuntimeException failedCallback) {
                finished(failedCallback);
                throw failedCallback;
            }
            finished(null);
        }

        /^* The transaction has closed: a root runs the callbacks it queued. {@code failure} is an exception on its way out. ^/
        private void finished(RuntimeException failure) {
            closed = true;
            if (root) {
                try {
                    RootCommits.rootClosed();
                } catch (RuntimeException e) {
                    if (failure == null) {
                        throw e;
                    }
                    failure.addSuppressed(e);
                }
            }
        }
    }

    private static final class Participant<S> extends SnapshotParticipant<S> {
        private static final Object UNSET = new Object();

        private final JournalCallbacks<S> callbacks;
        /^*
         * The snapshot Fabric holds for this participant at each nesting depth ({@link #UNSET} for none): the table
         * {@code SnapshotParticipant} keeps privately, kept in step with it by {@link #updateSnapshots} and
         * {@link #onClose}. The entry that reaches depth 0 is the state before the outer transaction's first change.
         ^/
        private final List<Object> held = new ArrayList<>();
        /^* The depth {@link #updateSnapshots} is snapshotting, for {@link #createSnapshot}. ^/
        private int snapshotDepth;
        /^* The snapshot that reached depth 0 as the outer transaction committed, until {@link #onFinalCommit}. ^/
        private Object rootOriginal = UNSET;

        Participant(JournalCallbacks<S> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public void updateSnapshots(TransactionContext transaction) {
            snapshotDepth = transaction.nestingDepth();
            while (held.size() <= snapshotDepth) {
                held.add(UNSET);
            }
            super.updateSnapshots(transaction);
        }

        @Override
        protected S createSnapshot() {
            // Fabric asks only for a depth that holds none.
            S snapshot = callbacks.createSnapshot();
            held.set(snapshotDepth, snapshot);
            return snapshot;
        }

        @Override
        protected void readSnapshot(S snapshot) {
            callbacks.revertToSnapshot(snapshot);
        }

        @Override
        public void onClose(TransactionContext transaction, TransactionContext.Result result) {
            int depth = transaction.nestingDepth();
            Object snapshot = held.set(depth, UNSET);
            super.onClose(transaction, result);
            if (result.wasAborted()) {
                return; // reverted to it, nothing remains
            }
            if (depth > 0) {
                // Committed: the snapshot moves up unless the parent already holds an older one, as Fabric does.
                if (held.get(depth - 1) == UNSET) {
                    held.set(depth - 1, snapshot);
                }
            } else {
                rootOriginal = snapshot; // Fabric's afterOuterClose, and so onFinalCommit, follow
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void onFinalCommit() {
            S state = (S) rootOriginal;
            rootOriginal = UNSET;
            RootCommits.deliver(() -> callbacks.onRootCommit(state));
        }
    }
}
*///?}
