/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.Capabilities;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.SoundUtil;
//?} else {
/*import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
*///?}

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransaction;

/**
 * Fluid container items on NeoForge, the bodies that used to sit in {@code FluidUtilBC} and {@code WidgetFluidTank}:
 * on 1.21.10+ through the Transfer API ({@code Capabilities.Fluid.ITEM} behind an {@code ItemAccess}), on 1.21.1
 * through the classic {@code FluidUtil} helpers, each arm exactly as it was. {@code FluidContainerHelperTest} pins
 * that both behave the same.
 */
final class NeoForgeFluidContainers {
    private NeoForgeFluidContainers() {}

    static BCFluidStack getFluidContained(ItemStack stack) {
        if (stack.isEmpty()) {
            return BCFluidStack.EMPTY;
        }
        //? if >=1.21.10 {
        // Reads through a one-by-one ItemAccess over the stack: getResource/getAmount only, nothing is extracted.
        return NeoForgeFluids.fromNeo(FluidUtil.getFirstStackContained(stack));
        //?} else {
        /*return NeoForgeFluids.fromNeo(FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY));*/
        //?}
    }

    static boolean isFluidContainer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        //? if >=1.21.10 {
        return ItemAccess.forStack(stack).oneByOne().getCapability(Capabilities.Fluid.ITEM) != null;
        //?} else {
        /*return FluidUtil.getFluidHandler(stack.copyWithCount(1)).isPresent();*/
        //?}
    }

    static boolean drainContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> container = slotContainer(slots, slot);
        return container != null && moveAll(container, NeoForgeFluidStorages.toNeo(tank));
        //?} else {
        /*FluidActionResult result = FluidUtil.tryEmptyContainer(slots.getStack(slot), NeoForgeFluidStorages.toNeo(tank),
                Integer.MAX_VALUE, null, true);
        if (result.isSuccess()) {
            setSlot.accept(result.getResult());
        }
        return result.isSuccess();*/
        //?}
    }

    static boolean fillContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> container = slotContainer(slots, slot);
        return container != null && moveAll(NeoForgeFluidStorages.toNeo(tank), container);
        //?} else {
        /*FluidActionResult result = FluidUtil.tryFillContainer(slots.getStack(slot), NeoForgeFluidStorages.toNeo(tank),
                Integer.MAX_VALUE, null, true);
        if (result.isSuccess()) {
            setSlot.accept(result.getResult());
        }
        return result.isSuccess();*/
        //?}
    }

    //? if >=1.21.10 {
    // The container handler comes from a STRICT slot access: the filled/emptied item replaces the container in that
    // same slot, never overflowing into the tile's other slots. Everything moves in one root transaction.
    private static ResourceHandler<FluidResource> slotContainer(BCItemStorage slots, int slot) {
        return ItemAccess.forHandlerIndexStrict(NeoForgeItemStorages.toNeo(slots), slot).oneByOne()
                .getCapability(Capabilities.Fluid.ITEM);
    }

    private static boolean moveAll(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to) {
        try (Transaction tx = Transaction.openRoot()) {
            if (ResourceHandlerUtil.move(from, to, r -> true, Integer.MAX_VALUE, tx) > 0) {
                tx.commit();
                return true;
            }
        }
        return false;
    }
    //?}

    static boolean interactWithHeldContainer(Player player, InteractionHand hand, BlockPos pos, BCFluidStorage tank) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return false;
        Level world = player.level();
        if (world.isClientSide()) return true;
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> fluidHandler = NeoForgeFluidStorages.toNeo(tank);
        boolean isCreative = player.getAbilities().instabuild;
        ItemStack singleCopy = held.copyWithCount(1);
        ResourceHandler<FluidResource> itemHandlerIn =
                singleCopy.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forPlayerInteraction(player, hand));
        if (itemHandlerIn != null) {
            // First try draining tank -> item (filling bucket)
            try (Transaction tx = Transaction.openRoot()) {
                FluidResource tankFluid = fluidHandler.size() > 0 ? fluidHandler.getResource(0) : FluidResource.EMPTY;
                int moved = ResourceHandlerUtil.move(fluidHandler, itemHandlerIn, r -> true, Integer.MAX_VALUE, tx);
                if (moved > 0) {
                    tx.commit();
                    if (!tankFluid.isEmpty()) {
                        SoundUtil.playBucketFill(world, pos, NeoForgeFluidStorages.stackOf(tankFluid, moved));
                    }
                    swapHeld(player, hand, held, singleCopy, isCreative);
                    return true;
                }
            }

            // Next try item -> tank (emptying bucket)
            try (Transaction tx = Transaction.openRoot()) {
                FluidResource itemFluid = itemHandlerIn.size() > 0 ? itemHandlerIn.getResource(0) : FluidResource.EMPTY;
                int moved = ResourceHandlerUtil.move(itemHandlerIn, fluidHandler, r -> true, Integer.MAX_VALUE, tx);
                if (moved > 0) {
                    tx.commit();
                    if (!itemFluid.isEmpty()) {
                        SoundUtil.playBucketEmpty(world, pos, NeoForgeFluidStorages.stackOf(itemFluid, moved));
                    }
                    swapHeld(player, hand, held, singleCopy, isCreative);
                    return true;
                }
            }
        }
        return false;
        //?} else {
        /*return FluidUtil.interactWithFluidHandler(player, hand, NeoForgeFluidStorages.toNeo(tank));*/
        //?}
    }

    //? if >=1.21.10 {
    private static void swapHeld(Player player, InteractionHand hand, ItemStack held, ItemStack singleCopy,
            boolean isCreative) {
        if (isCreative) return;
        if (held.getCount() == 1) {
            player.setItemInHand(hand, singleCopy);
        } else {
            held.shrink(1);
            if (!singleCopy.isEmpty() && !player.getInventory().add(singleCopy)) {
                EntityUtil.dropOverflow(player, singleCopy);
            }
        }
        player.containerMenu.broadcastChanges();
    }
    //?}

    static boolean exchangeWithCarriedContainer(Player player, BCFluidStorage tank) {
        ItemStack carried = player.containerMenu.getCarried();
        boolean isCreative = player.getAbilities().instabuild;
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> tankHandler = NeoForgeFluidStorages.toNeo(tank);
        if (isCreative) {
            // ItemAccess.forInfiniteMaterials had a nasty interaction with the bucket fluid
            // capability: the move(bucket -> tank) path would swap the cursor bucket to an empty
            // one via the infinite-materials sink but fail to actually commit the fluid into the
            // tank, so the player lost their bucket AND got no fluid. Bypass the accessor here
            // and do the bucket-fluid read against a detached copy so the cursor is untouched,
            // then fill the tank with the bucket's full fluid contents.
            ItemStack bucketCopy = carried.copy();
            ResourceHandler<FluidResource> bucketCap =
                    ItemAccess.forStack(bucketCopy).getCapability(Capabilities.Fluid.ITEM);
            if (bucketCap == null || bucketCap.size() == 0) {
                // A non-fluid-container item (e.g. an ice block): the caller tries its solid-coolant conversion.
                return false;
            }
            FluidResource bucketFluid = bucketCap.getResource(0);
            long bucketAmount = bucketCap.getAmountAsLong(0);
            if (!bucketFluid.isEmpty() && bucketAmount > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    int filled = tankHandler.insert(0, bucketFluid, (int) bucketAmount, tx);
                    if (filled > 0) {
                        tx.commit();
                    }
                }
                return true;
            }
            // Empty container in creative: drain one bucket's worth per click, don't swap
            // the carried item (creative abundance — player keeps their empty bucket, tank
            // gives up a bucket of fluid at a time matching the survival-mode feel).
            if (!tankHandler.getResource(0).isEmpty() && tankHandler.getAmountAsLong(0) > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    int drained = tankHandler.extract(0, tankHandler.getResource(0),
                            (int) Math.min(1000L, tankHandler.getAmountAsLong(0)), tx);
                    if (drained > 0) {
                        tx.commit();
                    }
                }
            }
            return true;
        }
        ResourceHandler<FluidResource> itemHandlerIn =
                ItemAccess.forPlayerCursor(player, player.containerMenu).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (itemHandlerIn == null) {
            return false;
        }
        // Try filling the tank from the item
        try (Transaction tx = Transaction.openRoot()) {
            if (ResourceHandlerUtil.move(itemHandlerIn, tankHandler, r -> true, Integer.MAX_VALUE, tx) > 0) {
                tx.commit();
                return true;
            }
        }
        // Try draining the tank into the item
        try (Transaction tx = Transaction.openRoot()) {
            if (ResourceHandlerUtil.move(tankHandler, itemHandlerIn, r -> true, Integer.MAX_VALUE, tx) > 0) {
                tx.commit();
                return true;
            }
        }
        // Cursor-direct flow above is one-shot per bucket: with a 1-stack bucket the
        // cursor swaps to a filled bucket on the first click, and a second click finds
        // the same fluid in the cursor and the tank with no room to grow on either side.
        // Repeated clicks then appear to do nothing. Fall back to the inventory: locate
        // an empty bucket and fill it, leaving the cursor untouched. This mirrors the
        // way 1.12.2's in-world right-click happily swallowed buckets in succession.
        return drainTankIntoInventoryBucket(player, tank);
        //?} else {
        /*if (!FluidUtil.getFluidHandler(carried.copy()).isPresent()) {
            return false;
        }
        BCFluidStack contained = NeoForgeFluids.fromNeo(FluidUtil.getFluidContained(carried).orElse(FluidStack.EMPTY));
        if (isCreative) {
            // Creative: act on a detached copy so the cursor item is never consumed.
            if (!contained.isEmpty()) {
                fill(tank, contained);
                return true;
            }
            // Empty container in creative: drain one bucket's worth per click, cursor untouched.
            BCFluidStack inTank = tank.getStack(0);
            if (!inTank.isEmpty() && inTank.getAmount() > 0) {
                try (BCTransaction tx = BCTransaction.openRoot()) {
                    if (tank.extract(0, inTank, Math.min(1000, inTank.getAmount()), tx) > 0) {
                        tx.commit();
                    }
                }
            }
            return true;
        }
        IFluidHandler tankHandler = NeoForgeFluidStorages.toNeo(tank);
        ItemStack single = carried.copyWithCount(1);
        // Try filling the tank from the item.
        FluidActionResult emptied = FluidUtil.tryEmptyContainer(single, tankHandler, Integer.MAX_VALUE, player, true);
        if (emptied.isSuccess()) {
            updateCarried(player, carried, emptied.getResult());
            return true;
        }
        // Try draining the tank into the item.
        FluidActionResult filled = FluidUtil.tryFillContainer(single, tankHandler, Integer.MAX_VALUE, player, true);
        if (filled.isSuccess()) {
            updateCarried(player, carried, filled.getResult());
            return true;
        }
        // Cursor-direct flow is one-shot per bucket; fall back to filling an empty
        // inventory bucket, mirroring the 1.21.10+ path.
        return drainTankIntoInventoryBucket(player, tank);*/
        //?}
    }

    /**
     * Walk the player inventory looking for an empty fluid container we can fill from
     * the tank. Used as a fallback when the cursor is already holding a full bucket of
     * the tank's fluid — without this, repeated clicks on an output tank stop draining
     * after the first bucket because the cursor has no room and the tank rejects the
     * filled bucket as input. Returns {@code true} on success.
     */
    private static boolean drainTankIntoInventoryBucket(Player player, BCFluidStorage tank) {
        BCFluidStack tankFluid = tank.size() > 0 ? tank.getStack(0) : BCFluidStack.EMPTY;
        if (tankFluid.isEmpty() || tankFluid.getAmount() <= 0) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int size = inv.getContainerSize();
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> tankHandler = NeoForgeFluidStorages.toNeo(tank);
        for (int i = 0; i < size; i++) {
            ItemStack invStack = inv.getItem(i);
            if (invStack.isEmpty()) continue;
            ResourceHandler<FluidResource> slotCap =
                    ItemAccess.forPlayerSlot(player, i).oneByOne().getCapability(Capabilities.Fluid.ITEM);
            if (slotCap == null || slotCap.size() == 0) continue;
            // Skip non-empty containers — only fill empty ones to avoid silently swapping
            // the player's existing fluid containers.
            if (!slotCap.getResource(0).isEmpty()) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.move(tankHandler, slotCap, r -> true, Integer.MAX_VALUE, tx);
                if (moved > 0) {
                    tx.commit();
                    return true;
                }
            }
        }
        return false;
        //?} else {
        /*IFluidHandler tankHandler = NeoForgeFluidStorages.toNeo(tank);
        for (int i = 0; i < size; i++) {
            ItemStack invStack = inv.getItem(i);
            if (invStack.isEmpty()) continue;
            // Skip non-empty containers — only fill empty ones to avoid silently swapping
            // the player's existing fluid containers.
            if (!FluidUtil.getFluidContained(invStack).orElse(FluidStack.EMPTY).isEmpty()) continue;
            ItemStack single = invStack.copyWithCount(1);
            FluidActionResult result = FluidUtil.tryFillContainer(single, tankHandler, Integer.MAX_VALUE, player, true);
            if (result.isSuccess()) {
                invStack.shrink(1);
                ItemStack filled = result.getResult();
                if (!filled.isEmpty() && !inv.add(filled)) {
                    player.drop(filled, false);
                }
                return true;
            }
        }
        return false;*/
        //?}
    }

    //? if <1.21.10 {
    /*private static void fill(BCFluidStorage tank, BCFluidStack stack) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            if (tank.insert(0, stack, stack.getAmount(), tx) > 0) {
                tx.commit();
            }
        }
    }

    private static void updateCarried(Player player, ItemStack original, ItemStack result) {
        if (original.getCount() == 1) {
            player.containerMenu.setCarried(result);
        } else {
            original.shrink(1);
            if (!result.isEmpty() && !player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
    }*/
    //?}

    /**
     * Registers {@code item} as a drain-only fluid container that is used up once empty, its fluid in the
     * {@code SimpleFluidContent} component {@code content} (the fragile fluid shard), moved verbatim from
     * {@code BCCore}.
     */
    @SuppressWarnings("unchecked")
    static void registerDrainOnlyConsumable(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event,
            net.minecraft.world.level.ItemLike item,
            java.util.function.Supplier<? extends net.minecraft.core.component.DataComponentType<?>> content,
            int capacity) {
        java.util.function.Supplier<net.minecraft.core.component.DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> component =
            (java.util.function.Supplier<net.minecraft.core.component.DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>>) content;
        //? if >=1.21.10 {
        event.registerItem(
                Capabilities.Fluid.ITEM,
                (stack, ctx) -> new net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler(ctx,
                        component.get(), capacity) {
                    @Override
                    public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource,
                            int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                        return 0; // cannot fill!
                    }

                    @Override
                    public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                        java.util.Objects.checkIndex(index, size());
                        net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

                        int accessAmount = itemAccess.getAmount();
                        if (accessAmount == 0) {
                            return 0;
                        }

                        net.neoforged.neoforge.transfer.item.ItemResource accessResource = itemAccess.getResource();
                        net.neoforged.neoforge.transfer.fluid.FluidResource currentResource = getResourceFrom(accessResource, index);

                        if (resource.equals(currentResource)) {
                            int currentAmountPerItem = getAmountFrom(accessResource, index);
                            int extractedPerItem = Math.min(amount / accessAmount, currentAmountPerItem);

                            if (extractedPerItem > 0) {
                                net.neoforged.neoforge.transfer.item.ItemResource emptiedResource = update(accessResource, index, resource, currentAmountPerItem - extractedPerItem);

                                if (!emptiedResource.isEmpty()) {
                                    return extractedPerItem * itemAccess.exchange(emptiedResource, accessAmount, transaction);
                                } else if (currentAmountPerItem - extractedPerItem == 0) {
                                    // Fully consume the item since it has no valid remaining state
                                    int extractedItems = itemAccess.extract(accessResource, accessAmount, transaction);
                                    if (extractedItems == accessAmount) {
                                        return extractedPerItem * accessAmount;
                                    }
                                }
                            }
                        }
                        return 0;
                    }

                    @Override
                    protected net.neoforged.neoforge.transfer.item.ItemResource update(
                            net.neoforged.neoforge.transfer.item.ItemResource accessResource,
                            int index,
                            net.neoforged.neoforge.transfer.fluid.FluidResource newResource,
                            int newAmount) {
                        if (newAmount == 0) {
                            return net.neoforged.neoforge.transfer.item.ItemResource.EMPTY;
                        }
                        return super.update(accessResource, index, newResource, newAmount);
                    }
                },
                item);
        //?} else {
        /*// 1.21.1: the fragile container is an extract-only, consume-on-empty fluid item. The classic
        // FluidHandlerItemStack.Consumable already shatters the shard when drained (setContainerToEmpty
        // -> container.shrink(1)); overriding canFillFluidType to false makes it un-fillable (the modern
        // ItemAccessFluidHandler override returned 0 from insert for the same reason).
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack.Consumable(
                        component, stack, capacity) {
                    @Override
                    public boolean canFillFluidType(net.neoforged.neoforge.fluids.FluidStack fluid) {
                        return false;
                    }
                },
                item);*/
        //?}
    }
}
//?}
