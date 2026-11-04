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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.capability.BCCapability;
import buildcraft.api.fluids.BCFluidStack;

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

    /** The energy storage of the block at {@code pos}, seen from {@code side} (null: unsided), or null if none. */
    BCEnergyStorage findEnergy(Level level, BlockPos pos, Direction side);

    /**
     * The object the loader's capability of {@code capability}'s id and type holds for the block at {@code pos}, seen
     * from {@code side} (null: unsided), as it is (no adapter), or null. Never a storage token ({@link StorageTokens}).
     */
    <T> T findDirect(BCCapability<T> capability, Level level, BlockPos pos, Direction side);

    /** The fluid storage of the block at {@code pos}, seen from {@code side} (null: unsided), or null if none. */
    BCFluidStorage findFluid(Level level, BlockPos pos, Direction side);

    /** A cached {@link #findFluid(Level, BlockPos, Direction)} for one position and side. */
    BCLookupCache<BCFluidStorage> fluidCache(ServerLevel level, BlockPos pos, Direction side);

    // ---- Fluid container items. Each loader has its own idea of how a bucket-like item swaps itself for its filled
    // or emptied twin (NeoForge 1.21.10+: an ItemAccess; 1.21.1: FluidUtil and IFluidHandlerItem.getContainer;
    // Fabric: a ContainerItemContext), so these are whole operations rather than a storage view of the item.

    /** The fluid ONE item of {@code stack} holds (its count is ignored); empty for an empty or non-container item. */
    BCFluidStack getFluidContained(ItemStack stack);

    /** Whether the item is a fluid container at all, an empty one (an empty bucket) included. */
    boolean isFluidContainer(ItemStack stack);

    /**
     * Empties the single container item at {@code slots[slot]} into {@code tank}, the emptied item replacing it in
     * that same slot (never anywhere else). Loaders that cannot route the swap through {@code slots} write the
     * result with {@code setSlot}. Returns whether any fluid moved.
     */
    boolean drainContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank);

    /** {@link #drainContainerSlot}'s twin: fills the container at {@code slots[slot]} from {@code tank}. */
    boolean fillContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank);

    /**
     * A player's right click on a tank with the item in {@code hand}: first fill the item from {@code tank}, else
     * empty it into the tank, swapping the held item (one of a stack; the rest stays, the result goes to the
     * inventory) and playing the bucket sound at {@code pos}. Creative players keep their item. Returns whether
     * fluid moved. Server side only.
     */
    boolean interactWithHeldContainer(Player player, InteractionHand hand, BlockPos pos, BCFluidStorage tank);

    /**
     * A click on a tank in a GUI with a fluid container on the cursor: empty it into {@code tank}, else fill it
     * from {@code tank}, else fill an empty container in the player's inventory. In creative the cursor item is
     * never changed: a full container fills the tank, an empty one drains a bucket. Returns whether the carried
     * item was a fluid container that the click dealt with; false lets the caller try other conversions.
     */
    boolean exchangeWithCarriedContainer(Player player, BCFluidStorage tank);

    /**
     * The loader's own fluid handler for a BuildCraft storage (NeoForge 1.21.10+: a {@code ResourceHandler<FluidResource>};
     * 1.21.1: an {@code IFluidHandler}; Fabric: a {@code Storage<FluidVariant>}), and back. Interim: only the robot
     * API's fluid surface (IFluidHandlerAdv, DockingStation) still exposes loader handlers, until it gets BuildCraft
     * types of its own.
     */
    Object toLoaderFluidHandler(BCFluidStorage storage);

    /** {@link #toLoaderFluidHandler}'s inverse; null stays null. */
    BCFluidStorage fromLoaderFluidHandler(Object handler);

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
