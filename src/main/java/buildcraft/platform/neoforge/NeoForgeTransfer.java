/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

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

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
*///?}

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

/**
 * {@link TransferServices} on NeoForge. On 1.21.10+ a {@link BCTransaction} is a NeoForge {@code Transaction} and a
 * BuildCraft journal is a NeoForge {@code SnapshotJournal}, so BuildCraft state joins the transactions other mods open
 * (an adapter wraps the {@code TransactionContext} it is handed with {@link #wrap}). On 1.21.1 there is no NeoForge
 * transaction, so {@link LegacyTransactions} stands in and the classic handlers' simulate flag maps onto it.
 *
 * <p><b>Capabilities.</b> Each {@link #registerCapabilities} call subscribes its own {@code RegisterCapabilitiesEvent}
 * listener on the mod bus of the mod being constructed, so registrations keep the order of the calls relative to
 * every other listener of that event. Storages cross as {@link NeoForgeItemStorages}, {@link NeoForgeFluidStorages}
 * and {@link NeoForgeEnergyStorages} adapters, which unwrap each other. Every other {@link BCCapability} (the MJ
 * tokens) is a sided NeoForge {@code BlockCapability} of the same id and type ({@link #blockCap}), its objects handed
 * out as they are; {@code BlockCapability.createSided} interns by id, so another mod creating it shares it.
 */
public final class NeoForgeTransfer implements TransferServices {
    @Override
    public BCTransaction openRoot() {
        //? if >=1.21.10 {
        return new Owned(Transaction.openRoot());
        //?} else {
        /*return LegacyTransactions.openRoot();
        *///?}
    }

    @Override
    // getCurrentOpenedTransaction is deprecated only as a hint to pass the context instead; a simulate-flag call has
    // none to pass, which is the case it exists for.
    @SuppressWarnings("deprecation")
    public BCTransaction openInCurrent() {
        //? if >=1.21.10 {
        TransactionContext current = Transaction.getCurrentOpenedTransaction();
        return new Owned(current == null ? Transaction.openRoot() : Transaction.open(current));
        //?} else {
        /*return LegacyTransactions.openInCurrent();
        *///?}
    }

    @Override
    public <S> JournalHandle newJournal(JournalCallbacks<S> callbacks) {
        //? if >=1.21.10 {
        Journal<S> journal = new Journal<>(callbacks);
        return tx -> journal.updateSnapshots(unwrap(tx));
        //?} else {
        /*return LegacyTransactions.newJournal(callbacks);
        *///?}
    }

    @Override
    public void registerCapabilities(Consumer<CapabilityRegistrar> registration) {
        // Queued in call order, so all of them run in BuildCraft's init order inside the one RegisterCapabilitiesEvent
        // listener BCNeoForgeEntrypoint subscribes.
        CapabilityListeners.add(event -> registration.accept(new Registrar(event)));
    }

    private record Registrar(RegisterCapabilitiesEvent event) implements CapabilityRegistrar {
        @Override
        public <T extends BlockEntity> void blockItems(BlockEntityType<T> type, BlockProvider<T, BCItemStorage> provider) {
            event.registerBlockEntity(itemBlockCap(), type,
                (tile, side) -> NeoForgeItemStorages.toNeo(provider.get(tile, side)));
        }

        @Override
        public <T extends BlockEntity> void blockFluids(BlockEntityType<T> type, BlockProvider<T, BCFluidStorage> provider) {
            event.registerBlockEntity(fluidBlockCap(), type,
                (tile, side) -> NeoForgeFluidStorages.toNeo(provider.get(tile, side)));
        }

        @Override
        public <T extends BlockEntity> void blockEnergy(BlockEntityType<T> type, BlockProvider<T, BCEnergyStorage> provider) {
            event.registerBlockEntity(energyBlockCap(), type,
                (tile, side) -> NeoForgeEnergyStorages.toNeo(provider.get(tile, side)));
        }

        @Override
        public <T extends BlockEntity, S> void blockDirect(BCCapability<S> capability, BlockEntityType<T> type,
                BlockProvider<T, ? extends S> provider) {
            event.registerBlockEntity(blockCap(capability), type, provider::get);
        }

        @Override
        public void itemFluidsDrainOnlyConsumable(ItemLike item, Supplier<? extends DataComponentType<?>> content,
                int capacity) {
            NeoForgeFluidContainers.registerDrainOnlyConsumable(event, item, content, capacity);
        }
    }

    private static final Map<BCCapability<?>, BlockCapability<?, Direction>> DIRECT_CAPS = new ConcurrentHashMap<>();

    /** The sided NeoForge capability of a token's id and type (never a storage token). */
    @SuppressWarnings("unchecked")
    static <S> BlockCapability<S, Direction> blockCap(BCCapability<S> capability) {
        if (StorageTokens.isStorage(capability)) {
            throw new IllegalArgumentException("A storage token has no direct capability: " + capability);
        }
        return (BlockCapability<S, Direction>) DIRECT_CAPS.computeIfAbsent(capability,
            token -> BlockCapability.createSided(token.id(), token.type()));
    }

    //? if >=1.21.10 {
    private static BlockCapability<ResourceHandler<ItemResource>, Direction> itemBlockCap() {
        return Capabilities.Item.BLOCK;
    }
    //?} else {
    /*private static BlockCapability<IItemHandler, Direction> itemBlockCap() {
        return Capabilities.ItemHandler.BLOCK;
    }*/
    //?}

    //? if >=1.21.10 {
    private static BlockCapability<ResourceHandler<FluidResource>, Direction> fluidBlockCap() {
        return Capabilities.Fluid.BLOCK;
    }
    //?} else {
    /*private static BlockCapability<IFluidHandler, Direction> fluidBlockCap() {
        return Capabilities.FluidHandler.BLOCK;
    }*/
    //?}

    @Override
    @Nullable
    public BCItemStorage findItem(Level level, BlockPos pos, @Nullable Direction side) {
        return NeoForgeItemStorages.fromNeo(level.getCapability(itemBlockCap(), pos, side));
    }

    @Override
    @Nullable
    public BCItemStorage findItem(Entity entity) {
        //? if >=1.21.10 {
        return NeoForgeItemStorages.fromNeo(entity.getCapability(Capabilities.Item.ENTITY));
        //?} else {
        /*return NeoForgeItemStorages.fromNeo(entity.getCapability(Capabilities.ItemHandler.ENTITY));
        *///?}
    }


    @Override
    public BCLookupCache<BCItemStorage> itemCache(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        var cache = BlockCapabilityCache.create(itemBlockCap(), level, pos, side);
        return new BCLookupCache<>() {
            private Object handler;
            private BCItemStorage view;

            @Override
            @Nullable
            public BCItemStorage get() {
                var current = cache.getCapability();
                if (current != handler) {
                    handler = current;
                    view = NeoForgeItemStorages.fromNeo(current);
                }
                return view;
            }
        };
    }

    //? if >=1.21.10 {
    private static BlockCapability<EnergyHandler, Direction> energyBlockCap() {
        return Capabilities.Energy.BLOCK;
    }
    //?} else {
    /*private static BlockCapability<IEnergyStorage, Direction> energyBlockCap() {
        return Capabilities.EnergyStorage.BLOCK;
    }*/
    //?}

    @Override
    @Nullable
    public BCEnergyStorage findEnergy(Level level, BlockPos pos, @Nullable Direction side) {
        return NeoForgeEnergyStorages.fromNeo(level.getCapability(energyBlockCap(), pos, side));
    }

    @Override
    @Nullable
    public <T> T findDirect(BCCapability<T> capability, Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(blockCap(capability), pos, side);
    }

    @Override
    @Nullable
    public BCFluidStorage findFluid(Level level, BlockPos pos, @Nullable Direction side) {
        return NeoForgeFluidStorages.fromNeo(level.getCapability(fluidBlockCap(), pos, side));
    }

    @Override
    public BCLookupCache<BCFluidStorage> fluidCache(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        var cache = BlockCapabilityCache.create(fluidBlockCap(), level, pos, side);
        return new BCLookupCache<>() {
            private Object handler;
            private BCFluidStorage view;

            @Override
            @Nullable
            public BCFluidStorage get() {
                var current = cache.getCapability();
                if (current != handler) {
                    handler = current;
                    view = NeoForgeFluidStorages.fromNeo(current);
                }
                return view;
            }
        };
    }

    @Override
    public BCFluidStack getFluidContained(ItemStack stack) {
        return NeoForgeFluidContainers.getFluidContained(stack);
    }

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return NeoForgeFluidContainers.isFluidContainer(stack);
    }

    @Override
    public boolean drainContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        return NeoForgeFluidContainers.drainContainerSlot(slots, slot, setSlot, tank);
    }

    @Override
    public boolean fillContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        return NeoForgeFluidContainers.fillContainerSlot(slots, slot, setSlot, tank);
    }

    @Override
    public boolean interactWithHeldContainer(Player player, InteractionHand hand, BlockPos pos, BCFluidStorage tank) {
        return NeoForgeFluidContainers.interactWithHeldContainer(player, hand, pos, tank);
    }

    @Override
    public boolean exchangeWithCarriedContainer(Player player, BCFluidStorage tank) {
        return NeoForgeFluidContainers.exchangeWithCarriedContainer(player, tank);
    }


    //? if >=1.21.10 {
    /** The BuildCraft view of a NeoForge transaction someone else opened: it can be nested into, never committed. */
    public static BCTransactionContext wrap(TransactionContext context) {
        return new Borrowed(context);
    }

    /** The NeoForge transaction behind a BuildCraft one. */
    public static TransactionContext unwrap(BCTransactionContext transaction) {
        if (transaction instanceof Owned owned) {
            return owned.tx;
        }
        if (transaction instanceof Borrowed borrowed) {
            return borrowed.context;
        }
        throw new IllegalArgumentException("Not a NeoForge-backed transaction: " + transaction);
    }

    private record Borrowed(TransactionContext context) implements BCTransactionContext {
        @Override
        public int depth() {
            return context.depth();
        }

        @Override
        public BCTransaction openNested() {
            return new Owned(Transaction.open(context));
        }
    }

    private static final class Owned implements BCTransaction {
        final Transaction tx;

        Owned(Transaction tx) {
            this.tx = tx;
        }

        @Override
        public int depth() {
            return tx.depth();
        }

        @Override
        public BCTransaction openNested() {
            return new Owned(Transaction.open(tx));
        }

        @Override
        public void commit() {
            tx.commit();
        }

        @Override
        public void close() {
            tx.close();
        }

        @Override
        public String toString() {
            return "BC" + tx;
        }
    }

    private static final class Journal<S> extends SnapshotJournal<S> {
        private final JournalCallbacks<S> callbacks;

        Journal(JournalCallbacks<S> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        protected S createSnapshot() {
            return callbacks.createSnapshot();
        }

        @Override
        protected void revertToSnapshot(S snapshot) {
            callbacks.revertToSnapshot(snapshot);
        }

        @Override
        protected void onRootCommit(S originalState) {
            callbacks.onRootCommit(originalState);
        }
    }
    //?}
}
//?}
