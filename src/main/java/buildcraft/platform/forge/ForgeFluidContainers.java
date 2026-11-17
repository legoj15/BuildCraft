/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
//? if >=26.3 {
import net.minecraft.util.Prediction;
//?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import net.minecraftforge.common.SoundActions;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransaction;

/^*
 * Fluid container items on Forge: the Forge twin of {@code NeoForgeFluidContainers} (whose 1.21.1 arm is the shape
 * Forge's classic handlers fit). {@code ForgeTransfer} delegates its six container methods here, and
 * {@link #registerDrainOnlyConsumable} is its fragile-shard registration.
 *
 * <p><b>Item handlers come from the stack's capability, never from {@code FluidUtil}.</b> Forge's
 * {@code FluidUtil.getFluidHandler(ItemStack)} is hard-wired to answer empty (a "TEMP FIX" in Forge 66.1.x), so
 * {@code tryFillContainer}, {@code tryEmptyContainer}, {@code getFluidContained} and the item half of
 * {@code interactWithFluidHandler} never see an item handler. Everything here reads
 * {@code stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)} itself and moves fluid between that handler and a
 * {@link BCFluidStorage} through a {@link BCTransaction}: the tank side is journaled (an aborted transaction leaves it
 * as it was), the container side is a private copy of one item that is simply dropped when the move fails.
 *
 * <p><b>Buckets have no handler on Forge either.</b> Forge's patch that gave {@code BucketItem} a
 * {@code FluidBucketWrapper} is commented out (a TODO about item capabilities), so {@link #installItemCapabilities}
 * attaches one to every stack of exactly the {@code BucketItem} class (as that patch meant to; modded subclasses and the
 * milk bucket are left alone), and {@link #handlerOf} falls back to wrapping such a stack itself when the listener did
 * not exist yet when the stack was made. The wrapper is all-or-nothing: it moves one whole bucket (1000) or nothing.
 *
 * <p><b>Why the listener is installed at construction.</b> Forge fires {@code AttachCapabilitiesEvent.ItemStacks} for a
 * stack only if the event has a listener when the stack is constructed, then gathers lazily on its first capability
 * query; a stack made before there was a listener has no capabilities for good. {@link ForgeFluids}' constructor
 * installs it, which is the first {@code Platform.fluids()} call (BCLib's init, during mod construction). The event
 * fires per stack, so the listener is one identity-map lookup and one class test.
 *
 * <p><b>Sounds and overflow</b> are the two things NeoForge's twin takes from {@code lib.misc} ({@code SoundUtil},
 * {@code EntityUtil}), which sit outside the classes this node compiles; they are repeated here in their one-line
 * forms. The bucket sound is the fluid type's, as Forge's own {@code FluidUtil} asks it, else vanilla's.
 ^/
final class ForgeFluidContainers {
    private ForgeFluidContainers() {}

    /^* The key of the one capability provider this class attaches to an item stack (Forge refuses a duplicate key). ^/
    private static final Identifier CAPABILITY_KEY = Identifier.fromNamespaceAndPath("buildcraftunofficial", "fluid_container");

    /^* The drain-only consumables, by item: filled by {@code FMLCommonSetupEvent}, read by the attach listener. ^/
    private static final Map<Item, DrainOnly> DRAIN_ONLY = new ConcurrentHashMap<>();
    private static boolean installed;

    private record DrainOnly(Supplier<? extends DataComponentType<?>> content, int capacity) {}

    /^* Adds the item-stack listener, once; call it from mod construction (see the class comment). ^/
    static synchronized void installItemCapabilities() {
        if (installed) {
            return;
        }
        installed = true;
        //? if >=1.21.10 {
        ForgeBus.listen(AttachCapabilitiesEvent.ItemStacks.class, event -> {
        //?} else {
        /^ForgeBus.attachCapabilities(ItemStack.class, event -> {
        ^///?}
            ItemStack stack = event.getObject();
            Item item = stack.getItem();
            DrainOnly drainOnly = DRAIN_ONLY.get(item);
            if (drainOnly != null) {
                event.addCapability(CAPABILITY_KEY, new DrainOnlyProvider(stack, drainOnly));
            } else if (item.getClass() == BucketItem.class) {
                event.addCapability(CAPABILITY_KEY, new FluidBucketWrapper(stack));
            }
        });
    }

    /^*
     * Registers {@code item} as a drain-only fluid container that is used up once empty, its fluid in the fluid-content
     * component {@code content} (the fragile fluid shard), as NeoForge's twin does with an {@code ItemAccessFluidHandler}.
     * {@code item} must resolve ({@code asItem}) by now: {@code ForgeTileCapabilities} runs the queued registrations in
     * {@code FMLCommonSetupEvent}, after every registry event, where a deferred item is bound.
     ^/
    static void registerDrainOnlyConsumable(ItemLike item, Supplier<? extends DataComponentType<?>> content,
            int capacity) {
        installItemCapabilities();
        DRAIN_ONLY.put(item.asItem(), new DrainOnly(content, capacity));
    }

    /^* The fluid handler of one item (a stack of one): its capability, or a bucket wrapper for a plain bucket. ^/
    @Nullable
    private static IFluidHandlerItem handlerOf(ItemStack single) {
        if (single.isEmpty()) {
            return null;
        }
        IFluidHandlerItem handler = single.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
        if (handler == null && single.getItem().getClass() == BucketItem.class) {
            handler = new FluidBucketWrapper(single);
        }
        return handler;
    }

    /^* The first fluid any tank of the handler holds, empty if none. Reads only: nothing is drained. ^/
    private static FluidStack firstFluid(IFluidHandlerItem handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack held = handler.getFluidInTank(tank);
            if (!held.isEmpty()) {
                return held;
            }
        }
        return FluidStack.EMPTY;
    }

    static BCFluidStack getFluidContained(ItemStack stack) {
        if (stack.isEmpty()) {
            return BCFluidStack.EMPTY;
        }
        IFluidHandlerItem handler = handlerOf(stack.copyWithCount(1));
        return handler == null ? BCFluidStack.EMPTY : ForgeFluidStacks.fromForge(firstFluid(handler));
    }

    static boolean isFluidContainer(ItemStack stack) {
        return !stack.isEmpty() && handlerOf(stack.copyWithCount(1)) != null;
    }

    static boolean drainContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        ItemStack held = slots.getStack(slot); // a copy, the container handler may change it
        if (held.getCount() != 1) {
            return false; // an empty slot, or a stack with nowhere to put the swapped container
        }
        IFluidHandlerItem container = handlerOf(held);
        if (container == null || moveToTank(container, tank).isEmpty()) {
            return false;
        }
        setSlot.accept(container.getContainer());
        return true;
    }

    static boolean fillContainerSlot(BCItemStorage slots, int slot, Consumer<ItemStack> setSlot, BCFluidStorage tank) {
        ItemStack held = slots.getStack(slot);
        if (held.getCount() != 1) {
            return false;
        }
        IFluidHandlerItem container = handlerOf(held);
        if (container == null || moveFromTank(tank, container).isEmpty()) {
            return false;
        }
        setSlot.accept(container.getContainer());
        return true;
    }

    /^*
     * Empties the container into {@code tank}: what the container offers, or the part of it the tank takes when the
     * container lets that part go (a bucket does not: it gives a whole bucket or nothing, and then the tank is left as
     * it was). Returns what moved, empty if nothing did. The container is changed only when something moved.
     ^/
    private static BCFluidStack moveToTank(IFluidHandlerItem container, BCFluidStorage tank) {
        FluidStack offered = container.drain(Integer.MAX_VALUE, FluidAction.SIMULATE);
        if (offered.isEmpty()) {
            return BCFluidStack.EMPTY;
        }
        BCFluidStack resource = ForgeFluidStacks.fromForge(offered);
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int accepted = tank.insert(resource, resource.getAmount(), tx);
            if (accepted <= 0) {
                return BCFluidStack.EMPTY;
            }
            FluidStack drained = container.drain(new FluidStack(offered, accepted), FluidAction.EXECUTE);
            if (drained.getAmount() != accepted) {
                return BCFluidStack.EMPTY; // the container refuses a part: the transaction closes uncommitted
            }
            tx.commit();
            return resource.copyWithAmount(accepted);
        }
    }

    /^*
     * Fills the container from the first tank of {@code tank} that holds something the container takes (the amount the
     * container accepts of it, which a bucket makes all or nothing). Returns what moved, empty if nothing did. The
     * container is changed only when something moved.
     ^/
    private static BCFluidStack moveFromTank(BCFluidStorage tank, IFluidHandlerItem container) {
        for (int index = 0; index < tank.size(); index++) {
            BCFluidStack held = tank.getStack(index);
            if (held.isEmpty()) {
                continue;
            }
            FluidStack offered = ForgeFluidStacks.toForge(held);
            int fillable = container.fill(offered, FluidAction.SIMULATE);
            if (fillable <= 0) {
                continue;
            }
            try (BCTransaction tx = BCTransaction.openRoot()) {
                int extracted = tank.extract(index, held, fillable, tx);
                if (extracted <= 0) {
                    continue;
                }
                if (container.fill(new FluidStack(offered, extracted), FluidAction.EXECUTE) != extracted) {
                    return BCFluidStack.EMPTY; // the container may have changed: do not try it on another tank
                }
                tx.commit();
                return held.copyWithAmount(extracted);
            }
        }
        return BCFluidStack.EMPTY;
    }

    static boolean interactWithHeldContainer(Player player, InteractionHand hand, BlockPos pos, BCFluidStorage tank) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }
        Level level = player.level();
        if (level.isClientSide()) {
            return true;
        }
        boolean creative = player.getAbilities().instabuild;
        // First try draining the tank into the item (filling a bucket), on a one-item copy of the held stack.
        IFluidHandlerItem container = handlerOf(held.copyWithCount(1));
        if (container == null) {
            return false;
        }
        BCFluidStack moved = moveFromTank(tank, container);
        if (!moved.isEmpty()) {
            playBucketSound(level, pos, moved, true);
            swapHeld(player, hand, held, container.getContainer(), creative);
            return true;
        }
        // Next the item into the tank (emptying a bucket), on a fresh copy so a failed first attempt leaves nothing behind.
        container = handlerOf(held.copyWithCount(1));
        moved = container == null ? BCFluidStack.EMPTY : moveToTank(container, tank);
        if (!moved.isEmpty()) {
            playBucketSound(level, pos, moved, false);
            swapHeld(player, hand, held, container.getContainer(), creative);
            return true;
        }
        return false;
    }

    private static void playBucketSound(Level level, BlockPos pos, BCFluidStack fluid, boolean fill) {
        FluidStack stack = ForgeFluidStacks.toForge(fluid);
        SoundEvent sound = stack.getFluid().getFluidType().getSound(stack,
                fill ? SoundActions.BUCKET_FILL : SoundActions.BUCKET_EMPTY);
        if (sound == null) {
            sound = fill ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY;
        }
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /^* One item of the held stack becomes {@code result}: in place for a single item, else the rest stays and the result
     *  goes to the inventory. A creative player keeps the item. ^/
    private static void swapHeld(Player player, InteractionHand hand, ItemStack held, ItemStack result, boolean creative) {
        if (creative) {
            return;
        }
        if (held.getCount() == 1) {
            player.setItemInHand(hand, result);
        } else {
            held.shrink(1);
            giveOrDrop(player, result);
        }
        player.containerMenu.broadcastChanges();
    }

    /^* Gives {@code stack} to the inventory, dropping what does not fit (lib's {@code EntityUtil.dropOverflow}). ^/
    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            //? if >=26.3 {
            player.drop(stack, false, Prediction.SERVER_ONLY);
            //?} else {
            /^player.drop(stack, false);
            ^///?}
        }
    }

    static boolean exchangeWithCarriedContainer(Player player, BCFluidStorage tank) {
        ItemStack carried = player.containerMenu.getCarried();
        IFluidHandlerItem probe = handlerOf(carried.copyWithCount(1));
        if (probe == null) {
            return false;
        }
        if (player.getAbilities().instabuild) {
            // Creative: act on the detached probe so the cursor item is never consumed. A full container fills the tank,
            // an empty one drains a bucket's worth per click.
            BCFluidStack contained = ForgeFluidStacks.fromForge(firstFluid(probe));
            if (!contained.isEmpty()) {
                try (BCTransaction tx = BCTransaction.openRoot()) {
                    if (tank.insert(0, contained, contained.getAmount(), tx) > 0) {
                        tx.commit();
                    }
                }
                return true;
            }
            BCFluidStack inTank = tank.size() > 0 ? tank.getStack(0) : BCFluidStack.EMPTY;
            if (!inTank.isEmpty()) {
                try (BCTransaction tx = BCTransaction.openRoot()) {
                    if (tank.extract(0, inTank, Math.min(1000, inTank.getAmount()), tx) > 0) {
                        tx.commit();
                    }
                }
            }
            return true;
        }
        // Try filling the tank from the item.
        if (!moveToTank(probe, tank).isEmpty()) {
            updateCarried(player, carried, probe.getContainer());
            return true;
        }
        // Try draining the tank into the item.
        IFluidHandlerItem container = handlerOf(carried.copyWithCount(1));
        if (container != null && !moveFromTank(tank, container).isEmpty()) {
            updateCarried(player, carried, container.getContainer());
            return true;
        }
        // The cursor flow is one-shot per bucket (a full bucket on the cursor has no room to grow, and the tank rejects
        // it as input): fill an empty container in the inventory instead, leaving the cursor as it is.
        return drainTankIntoInventoryContainer(player, tank);
    }

    private static void updateCarried(Player player, ItemStack original, ItemStack result) {
        if (original.getCount() == 1) {
            player.containerMenu.setCarried(result);
        } else {
            original.shrink(1);
            giveOrDrop(player, result);
        }
    }

    /^*
     * Walks the player's inventory for an empty fluid container the tank can fill; only empty ones, never swapping a
     * player's full container. Returns whether one was filled.
     ^/
    private static boolean drainTankIntoInventoryContainer(Player player, BCFluidStorage tank) {
        BCFluidStack tankFluid = tank.size() > 0 ? tank.getStack(0) : BCFluidStack.EMPTY;
        if (tankFluid.isEmpty()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            IFluidHandlerItem container = handlerOf(stack.copyWithCount(1));
            if (container == null || !firstFluid(container).isEmpty()) {
                continue;
            }
            if (!moveFromTank(tank, container).isEmpty()) {
                stack.shrink(1);
                giveOrDrop(player, container.getContainer());
                return true;
            }
        }
        return false;
    }

    /^*
     * The capability of a drain-only consumable (the fragile shard): one item whose fluid is in its content component,
     * that can be drained and never filled, and is used up when the last of its fluid goes, as NeoForge's drain-only
     * {@code ItemAccessFluidHandler} does. Like Forge's own item handlers it acts on a stack of exactly one item.
     ^/
    private static final class DrainOnlyProvider implements IFluidHandlerItem, ICapabilityProvider {
        private final LazyOptional<IFluidHandlerItem> holder = LazyOptional.of(() -> this);
        private final ItemStack container;
        private final DrainOnly config;

        DrainOnlyProvider(ItemStack container, DrainOnly config) {
            this.container = container;
            this.config = config;
        }

        private BCFluidStack contents() {
            return ForgeFluidContent.read(container, config.content().get());
        }

        @Override
        public ItemStack getContainer() {
            return container;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return ForgeFluidStacks.toForge(contents());
        }

        @Override
        public int getTankCapacity(int tank) {
            return config.capacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false; // drain-only
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // cannot fill
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (container.getCount() != 1 || resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidStack held = getFluidInTank(0);
            if (held.isEmpty() || !held.isFluidEqual(resource)) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (container.getCount() != 1 || maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            BCFluidStack held = contents();
            if (held.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int drained = Math.min(held.getAmount(), maxDrain);
            if (action.execute()) {
                int left = held.getAmount() - drained;
                if (left <= 0) {
                    container.shrink(1); // nothing valid remains of the item: it is used up
                } else {
                    ForgeFluidContent.write(container, config.content().get(), held.copyWithAmount(left));
                }
            }
            return ForgeFluidStacks.toForge(held.copyWithAmount(drained));
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            return ForgeCapabilities.FLUID_HANDLER_ITEM.orEmpty(capability, holder);
        }
    }
}
*///?}
