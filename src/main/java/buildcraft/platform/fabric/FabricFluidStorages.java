/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/^*
 * The two-way adapter between {@link BCFluidStorage} and Fabric's {@code Storage<FluidVariant>}: the fluid kind of
 * {@link FabricStorageAdapter}, which holds the shape and the non-slotted gap (one virtual tank). Amounts cross through
 * {@link FabricFluids}: millibuckets go out exactly as droplets, droplets come back floored to whole millibuckets, and
 * an offer to a BuildCraft storage is floored too (so less than one millibucket is refused).
 *
 * <p>A foreign storage may move a droplet count that is not a whole number of millibuckets (a water cauldron level is
 * 27000 droplets). BuildCraft books whole millibuckets, so the remainder would be minted on an insert and destroyed on
 * an extract; {@link #moveForeign} moves only a whole number of millibuckets, and a storage that cannot (a single
 * cauldron level) moves nothing.
 ^/
public final class FabricFluidStorages extends FabricStorageAdapter<BCFluidStack, FluidVariant, BCFluidStorage> {
    private static final FabricFluidStorages KIND = new FabricFluidStorages();

    private FabricFluidStorages() {}

    public static Storage<FluidVariant> toFabric(BCFluidStorage storage) {
        return KIND.toFabricStorage(storage);
    }

    public static BCFluidStorage fromFabric(Storage<FluidVariant> storage) {
        return KIND.fromFabricStorage(storage);
    }

    @Override
    boolean isEmpty(BCFluidStack resource) {
        return resource.isEmpty();
    }

    @Override
    BCFluidStack empty() {
        return BCFluidStack.EMPTY;
    }

    @Override
    FluidVariant blank() {
        return FluidVariant.blank();
    }

    @Override
    BCFluidStack resourceOf(FluidVariant variant) {
        return variant.isBlank() ? BCFluidStack.EMPTY : new BCFluidStack(variant.getFluid(), 1, FabricFluids.componentsOf(variant));
    }

    @Override
    FluidVariant variantOf(BCFluidStack resource) {
        return FabricFluids.toVariant(resource);
    }

    @Override
    BCFluidStack stackOf(FluidVariant variant, long amount) {
        return FabricFluids.fromVariant(variant, amount);
    }

    @Override
    int toBuildCraftAmount(long amount) {
        return FabricFluids.toMillibuckets(amount);
    }

    @Override
    long toFabricAmount(int amount) {
        return FabricFluids.toDroplets(amount);
    }

    @Override
    int amountAt(BCFluidStorage storage, int index) {
        return storage.getAmount(index);
    }

    @Override
    BCFluidStack capacityProbe(BCFluidStorage storage, int index) {
        return BCFluidStack.EMPTY;
    }

    @Override
    int boundCapacity(int capacity, BCFluidStack resource) {
        return capacity;
    }

    @Override
    int insertAcross(BCFluidStorage storage, BCFluidStack resource, int amount, BCTransactionContext transaction) {
        return storage.insert(resource, amount, transaction);
    }

    @Override
    int extractAcross(BCFluidStorage storage, BCFluidStack resource, int amount, BCTransactionContext transaction) {
        return storage.extract(resource, amount, transaction);
    }

    @Override
    BCFluidStorage slottedView(SlottedStorage<FluidVariant> storage) {
        return new Slotted(storage);
    }

    @Override
    BCFluidStorage plainView(Storage<FluidVariant> storage) {
        return new Plain(storage);
    }

    /^*
     * Moves up to {@code millibuckets} through {@code mover} inside {@code transaction}, and returns how many whole
     * millibuckets moved, with exactly that many droplets moved on the foreign side. A foreign storage may move a count
     * that is no whole number of millibuckets (a water cauldron takes a 27000-droplet level at a time), and booking
     * the floor of it would mint the remainder on an insert and destroy it on an extract. So the call is tried in a
     * nested transaction; if it moved a fractional count, it is undone and repeated capped at the whole millibuckets
     * it covered, and if that is still not whole (or moves nothing), nothing moves.
     ^/
    @Override
    int moveForeign(Mover mover, int millibuckets, TransactionContext transaction) {
        long moved;
        try (Transaction probe = transaction.openNested()) {
            moved = mover.move(toFabricAmount(millibuckets), probe);
            if (moved % FabricFluids.DROPLETS_PER_MB == 0) {
                if (moved > 0) {
                    probe.commit();
                }
                return toBuildCraftAmount(moved);
            }
        }
        long whole = moved - moved % FabricFluids.DROPLETS_PER_MB;
        if (whole <= 0) {
            return 0;
        }
        try (Transaction retry = transaction.openNested()) {
            long again = mover.move(whole, retry);
            if (again > 0 && again % FabricFluids.DROPLETS_PER_MB == 0) {
                retry.commit();
                return toBuildCraftAmount(again);
            }
        }
        return 0;
    }

    private static final class Slotted extends FromSlotted<BCFluidStack, FluidVariant> implements BCFluidStorage {
        Slotted(SlottedStorage<FluidVariant> storage) {
            super(KIND, storage);
        }
    }

    private static final class Plain extends FromPlain<BCFluidStack, FluidVariant> implements BCFluidStorage {
        Plain(Storage<FluidVariant> storage) {
            super(KIND, storage);
        }
    }
}
*///?}
