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

/**
 * Fluid container items on NeoForge, the bodies that used to sit in {@code FluidUtilBC} and {@code WidgetFluidTank}:
 * on 1.21.10+ through the Transfer API ({@code Capabilities.Fluid.ITEM} behind an {@code ItemAccess}), on 1.21.1
 * through the classic {@code FluidUtil} helpers, each arm exactly as it was, except the creative halves: the held-item
 * interaction on 1.21.1, where FluidUtil hands a creative player's container back unchanged, so that arm gives the
 * filled or emptied result itself (see {@link #interactWithHeldContainer}); and the cursor exchange, whose creative
 * branch follows vanilla's {@code ItemUtils.createFilledResult} — the cursor item stays, the fluid moves a whole
 * container's worth and the result is given once. On 1.21.10+ the held-item interaction leaves the whole item swap to
 * the one-item player-interaction access. {@code FluidContainerHelperTest} covers the slot helpers (read,
 * is-container, drain, fill) with the same assertions on every node; {@code CreativeCursorTankTester} covers the
 * cursor exchange and the held-item interaction with a held stack.
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
        // The player-interaction access does the whole item swap itself: in survival it takes one item from the hand
        // slot and stows the result back there or in the inventory, in creative (CreativeItemAccess) it keeps the
        // hand and stows the result only when the inventory holds none like it (vanilla createFilledResult).
        // oneByOne: over a stacked access a bucket's handler moves a whole stack's worth or nothing.
        // In creative it is CreativeItemAccess, not NeoForge's forInfiniteMaterials, whose "has one" check misses the
        // offhand and armour (#241).
        ItemAccess access = player.getAbilities().instabuild ? new CreativeItemAccess(player, held)
                : ItemAccess.forPlayerInteraction(player, hand);
        ResourceHandler<FluidResource> itemHandlerIn =
                access.oneByOne().getCapability(Capabilities.Fluid.ITEM);
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
                    player.containerMenu.broadcastChanges();
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
                    player.containerMenu.broadcastChanges();
                    return true;
                }
            }
        }
        return false;
        //?} else {
        /*IFluidHandler tankHandler = NeoForgeFluidStorages.toNeo(tank);
        if (!player.getAbilities().instabuild) {
            // Survival is FluidUtil's own path, untouched: its try*AndStow stows a multi-item result through the
            // player's item-handler capability (main inventory, armour and the offhand), which a plain
            // Inventory.add over the 36 main slots would not reach.
            return FluidUtil.interactWithFluidHandler(player, hand, tankHandler);
        }
        // Creative: FluidUtil hands the held container back unchanged (its try*AndStow creative branches never
        // stow the result), so a creative pour gave no empty bucket. The moves still ride FluidUtil's try*
        // helpers, which play the fluid's bucket sound, but the player keeps the held item and is given the result
        // only when the inventory holds none like it (vanilla createFilledResult, as the arm above and Forge's twin).
        // First try draining the tank into the item (filling a bucket), on a one-item copy of the held stack.
        FluidActionResult filled = FluidUtil.tryFillContainer(held.copyWithCount(1), tankHandler,
                Integer.MAX_VALUE, player, true);
        if (filled.isSuccess()) {
            giveCreativeResult(player, filled.getResult());
            return true;
        }
        // Next the item into the tank (emptying a bucket), on a fresh copy so a failed first attempt leaves
        // nothing behind.
        FluidActionResult emptied = FluidUtil.tryEmptyContainer(held.copyWithCount(1), tankHandler,
                Integer.MAX_VALUE, player, true);
        if (emptied.isSuccess()) {
            giveCreativeResult(player, emptied.getResult());
            return true;
        }
        return false;*/
        //?}
    }

    static boolean exchangeWithCarriedContainer(Player player, BCFluidStorage tank) {
        ItemStack carried = player.containerMenu.getCarried();
        boolean isCreative = player.getAbilities().instabuild;
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> tankHandler = NeoForgeFluidStorages.toNeo(tank);
        if (carried.isEmpty()) {
            return false; // an empty cursor is nothing to exchange
        }
        ResourceHandler<FluidResource> itemHandlerIn;
        if (isCreative) {
            // Vanilla's ItemUtils.createFilledResult (CreativeItemAccess): the cursor item is never taken and the
            // filled or emptied result joins the main inventory only when the inventory holds none like it.
            // oneByOne as survival: over a stacked access a bucket's handler moves a whole stack's worth or nothing.
            itemHandlerIn = new CreativeItemAccess(player, carried).oneByOne()
                    .getCapability(Capabilities.Fluid.ITEM);
        } else {
            itemHandlerIn = ItemAccess.forPlayerCursor(player, player.containerMenu).oneByOne()
                    .getCapability(Capabilities.Fluid.ITEM);
        }
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
        if (isCreative) {
            return false; // the inventory-bucket fallback below is survival-only: it fills a bucket the player owns
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
        if (isCreative) {
            // Vanilla's ItemUtils.createFilledResult: FluidUtil acts on detached one-item copies, so the cursor item
            // is never consumed, and giveCreativeResult hands the filled or emptied container over only when the
            // inventory holds none like it. No inventory fallback: that spends a bucket the player owns.
            IFluidHandler tankHandler = NeoForgeFluidStorages.toNeo(tank);
            FluidActionResult emptied = FluidUtil.tryEmptyContainer(carried.copyWithCount(1), tankHandler,
                    Integer.MAX_VALUE, player, true);
            if (emptied.isSuccess()) {
                giveCreativeResult(player, emptied.getResult());
                return true;
            }
            FluidActionResult filled = FluidUtil.tryFillContainer(carried.copyWithCount(1), tankHandler,
                    Integer.MAX_VALUE, player, true);
            if (filled.isSuccess()) {
                giveCreativeResult(player, filled.getResult());
                return true;
            }
            return false;
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

    //? if >=1.21.10 {
    /**
     * NeoForge's {@code ItemAccess.forInfiniteMaterials} with vanilla's "already has one" check: the access never
     * gives up its item, and a result is added (one, to the main slots) only when {@code Inventory.contains} finds
     * none like it anywhere, offhand and armour included, as {@code ItemUtils.createFilledResult} and Forge's twin
     * do. NeoForge's own checks the 36 main slots only (#241). The check reads the live inventory, which the
     * wrapper's journal keeps current inside a transaction, so a second insert in one transaction sees the first.
     */
    private static final class CreativeItemAccess implements ItemAccess {
        private final Player player;
        private final net.neoforged.neoforge.transfer.item.ItemResource resource;
        private final int amount;

        CreativeItemAccess(Player player, ItemStack contents) {
            this.player = player;
            this.resource = net.neoforged.neoforge.transfer.item.ItemResource.of(contents);
            this.amount = contents.getCount();
        }

        @Override
        public net.neoforged.neoforge.transfer.item.ItemResource getResource() {
            return resource;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public int insert(net.neoforged.neoforge.transfer.item.ItemResource inserted, int count,
                net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(inserted, count);
            if (count > 0 && !player.getInventory().contains(inserted.toStack())) {
                net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper.of(player).getMainSlots()
                        .insert(inserted, 1, transaction);
            }
            return count; // always accepted, as forInfiniteMaterials
        }

        @Override
        public int extract(net.neoforged.neoforge.transfer.item.ItemResource extracted, int count,
                net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(extracted, count);
            return count; // the creative player's item is never used up
        }
    }
    //?}

    //? if <1.21.10 {
    /*private static void updateCarried(Player player, ItemStack original, ItemStack result) {
        if (original.getCount() == 1) {
            player.containerMenu.setCarried(result);
        } else {
            original.shrink(1);
            if (!result.isEmpty() && !player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
    }

    // Vanilla's createFilledResult: a creative player keeps the item they acted on and is given the result only when
    // the inventory holds none like it (nothing is dropped when it is full), which FluidUtil's try*AndStow does not
    // follow: in creative it hands the container back unchanged and never stows the result. Both creative branches
    // come here — the held-item one (whose survival twin goes through FluidUtil.interactWithFluidHandler) and the
    // cursor exchange.
    private static void giveCreativeResult(Player player, ItemStack result) {
        if (!result.isEmpty() && !player.getInventory().contains(result)) {
            player.getInventory().add(result);
            player.containerMenu.broadcastChanges();
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
