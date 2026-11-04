/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.Platform;

/^*
 * A Fabric item fluid storage ({@code FluidStorage.ITEM}) over a fluid held in an item's content component
 * ({@link FabricFluidContent}), that can only be drained and is used up once empty: the fragile fluid shard, as
 * NeoForge's drain-only {@code ItemAccessFluidHandler} makes it there.
 *
 * <p>It acts on one item of the context's stack, the way Fabric's own item storages do: a drain swaps that item for
 * one holding the rest of the fluid, and the last drain removes it. The content is in whole millibuckets, so only
 * whole millibuckets (81 droplets each) are drained; a request below 81 droplets drains nothing.
 *^/
final class FabricDrainOnlyFluidItem implements SingleSlotStorage<FluidVariant> {
    private final ContainerItemContext context;
    private final DataComponentType<?> content;
    private final int capacity;

    FabricDrainOnlyFluidItem(ContainerItemContext context, DataComponentType<?> content, int capacity) {
        this.context = context;
        this.content = content;
        this.capacity = capacity;
    }

    /^* The fluid in the context's current item, or empty once the item is no longer this container. *^/
    private BCFluidStack stored() {
        ItemVariant item = context.getItemVariant();
        if (item.isBlank()) {
            return BCFluidStack.EMPTY;
        }
        return Platform.fluids().getFluidContent(item.toStack(), content);
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        return 0; // drain-only
    }

    @Override
    public boolean supportsInsertion() {
        return false;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        ItemVariant item = context.getItemVariant();
        BCFluidStack held = stored();
        if (held.isEmpty() || !resource.equals(FabricFluids.toVariant(held))) {
            return 0;
        }
        int drained = (int) Math.min(held.getAmount(), maxAmount / FabricFluids.DROPLETS_PER_MB);
        if (drained <= 0) {
            return 0;
        }
        int left = held.getAmount() - drained;
        if (left == 0) {
            // Nothing valid remains of the item: it is used up.
            return context.extract(item, 1, transaction) == 1 ? FabricFluids.toDroplets(drained) : 0;
        }
        ItemStack rest = item.toStack();
        Platform.fluids().setFluidContent(rest, content, held.copyWithAmount(left));
        return context.exchange(ItemVariant.of(rest), 1, transaction) == 1 ? FabricFluids.toDroplets(drained) : 0;
    }

    @Override
    public boolean isResourceBlank() {
        return stored().isEmpty();
    }

    @Override
    public FluidVariant getResource() {
        return FabricFluids.toVariant(stored());
    }

    @Override
    public long getAmount() {
        return FabricFluids.toDroplets(stored().getAmount());
    }

    @Override
    public long getCapacity() {
        return FabricFluids.toDroplets(capacity);
    }
}
*///?}
