/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.widget;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.ISolidCoolant;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.Widget_Neptune;
import buildcraft.lib.net.PacketBufferBC;

/**
 * Widget that allows clicking on a fluid tank in a GUI to fill/drain with the carried item.
 * <p>
 * When the client clicks the tank area, it sends {@link #NET_CLICK} to the server.
 * The server then attempts to transfer fluid between the player's carried item and the tank.
 */
public class WidgetFluidTank extends Widget_Neptune<ContainerBC_Neptune> {
    private static final byte NET_CLICK = 0;

    private final FluidStacksResourceHandler tank;

    public WidgetFluidTank(ContainerBC_Neptune container, FluidStacksResourceHandler tank) {
        super(container);
        this.tank = tank;
    }

    @Override
    public void handleWidgetDataServer(IPayloadContext ctx, PacketBufferBC buffer) {
        byte id = buffer.readByte();
        if (id == NET_CLICK) {
            onGuiClicked();
        }
    }

    /**
     * Send a click event from the client to the server for this tank.
     * Should be called from the Screen's mouseClicked handler.
     */
    public void sendClick() {
        sendWidgetData(buf -> buf.writeByte(NET_CLICK));
    }

    /**
     * Walk the player inventory looking for an empty fluid container we can fill from
     * the tank. Used as a fallback when the cursor is already holding a full bucket of
     * the tank's fluid — without this, repeated clicks on an output tank stop draining
     * after the first bucket because the cursor has no room and the tank rejects the
     * filled bucket as input. Returns {@code true} on success.
     */
    private boolean drainTankIntoInventoryBucket(Player player) {
        FluidResource tankFluid = tank.size() > 0 ? tank.getResource(0) : FluidResource.EMPTY;
        if (tankFluid.isEmpty() || tank.getAmountAsLong(0) <= 0) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack invStack = inv.getItem(i);
            if (invStack.isEmpty()) continue;
            net.neoforged.neoforge.transfer.access.ItemAccess slotAccess =
                    net.neoforged.neoforge.transfer.access.ItemAccess.forPlayerSlot(player, i).oneByOne();
            ResourceHandler<FluidResource> slotCap = slotAccess.getCapability(Capabilities.Fluid.ITEM);
            if (slotCap == null || slotCap.size() == 0) continue;
            // Skip non-empty containers — only fill empty ones to avoid silently swapping
            // the player's existing fluid containers.
            if (!slotCap.getResource(0).isEmpty()) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.move(
                        tank, slotCap, r -> true, Integer.MAX_VALUE, tx);
                if (moved > 0) {
                    tx.commit();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handle a GUI click on this tank. Tries to transfer fluid between the
     * player's carried item and the tank.
     * <p>
     * Ported from 1.12.2 Tank.onGuiClicked / transferStackToTank.
     */
    private void onGuiClicked() {
        Player player = container.player;
        ItemStack held = player.containerMenu.getCarried();
        if (held.isEmpty()) {
            return;
        }

        transferStackToTank(player);
        if (player instanceof ServerPlayer sp) {
            sp.containerMenu.broadcastChanges();
        }
    }

    /**
     * Attempts to transfer fluid between the given stack and this tank.
     * First tries to fill the tank from the item, then tries to drain the tank into the item.
     *
     * @return The resulting ItemStack after the transfer attempt
     */
    private void transferStackToTank(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        boolean isCreative = player.getAbilities().instabuild;

        if (isCreative) {
            // ItemAccess.forInfiniteMaterials had a nasty interaction with the bucket fluid
            // capability: the move(bucket -> tank) path would swap the cursor bucket to an empty
            // one via the infinite-materials sink but fail to actually commit the fluid into the
            // tank, so the player lost their bucket AND got no fluid. Bypass the accessor here
            // and do the bucket-fluid read against a detached copy so the cursor is untouched,
            // then fill the tank with the bucket's full fluid contents.
            ItemStack bucketCopy = carried.copy();
            net.neoforged.neoforge.transfer.access.ItemAccess copyAccess =
                net.neoforged.neoforge.transfer.access.ItemAccess.forStack(bucketCopy);
            ResourceHandler<FluidResource> bucketCap = copyAccess.getCapability(Capabilities.Fluid.ITEM);

            if (bucketCap != null && bucketCap.size() > 0) {
                FluidResource bucketFluid = bucketCap.getResource(0);
                long bucketAmount = bucketCap.getAmountAsLong(0);
                if (!bucketFluid.isEmpty() && bucketAmount > 0) {
                    try (Transaction tx = Transaction.openRoot()) {
                        int filled = tank.insert(0, bucketFluid, (int) bucketAmount, tx);
                        if (filled > 0) {
                            tx.commit();
                        }
                    }
                    return;
                }
                // Empty container in creative: drain one bucket's worth per click, don't swap
                // the carried item (creative abundance — player keeps their empty bucket, tank
                // gives up a bucket of fluid at a time matching the survival-mode feel).
                if (!tank.getResource(0).isEmpty() && tank.getAmountAsLong(0) > 0) {
                    try (Transaction tx = Transaction.openRoot()) {
                        int drained = tank.extract(0,
                            tank.getResource(0),
                            (int) Math.min(1000L, tank.getAmountAsLong(0)),
                            tx);
                        if (drained > 0) {
                            tx.commit();
                        }
                    }
                }
                return;
            }
            // A non-fluid-container item (e.g. an ice block) — fall through to the
            // solid-coolant conversion below, exactly as the survival branch does.
        } else {
            ItemStack original = carried.copy();
            net.neoforged.neoforge.transfer.access.ItemAccess access = net.neoforged.neoforge.transfer.access.ItemAccess.forPlayerCursor(player, player.containerMenu).oneByOne();
            ResourceHandler<FluidResource> itemHandlerIn = access.getCapability(Capabilities.Fluid.ITEM);

            if (itemHandlerIn != null) {
                // Try filling the tank from the item
                try (Transaction tx = Transaction.openRoot()) {
                    int moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.move(
                            itemHandlerIn, tank, r -> true, Integer.MAX_VALUE, tx
                    );
                    if (moved > 0) {
                        tx.commit();
                        return;
                    }
                }

                // Try draining the tank into the item
                try (Transaction tx = Transaction.openRoot()) {
                    int moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.move(
                            tank, itemHandlerIn, r -> true, Integer.MAX_VALUE, tx
                    );
                    if (moved > 0) {
                        tx.commit();
                        return;
                    }
                }

                // Cursor-direct flow above is one-shot per bucket: with a 1-stack bucket the
                // cursor swaps to a filled bucket on the first click, and a second click finds
                // the same fluid in the cursor and the tank with no room to grow on either side.
                // Repeated clicks then appear to do nothing. Fall back to the inventory: locate
                // an empty bucket and fill it, leaving the cursor untouched. This mirrors the
                // way 1.12.2's in-world right-click happily swallowed buckets in succession.
                if (drainTankIntoInventoryBucket(player)) {
                    return;
                }
            }
        }

        // --- Try solid coolant conversion (ice → water, ported from 1.12.2 Tank.map()) ---
        if (BuildcraftFuelRegistry.coolant != null) {
            ItemStack stack = player.containerMenu.getCarried();
            ItemStack singleCopyCoolant = stack.copyWithCount(1);
            ISolidCoolant solidCoolant = BuildcraftFuelRegistry.coolant.getSolidCoolant(singleCopyCoolant);
            if (solidCoolant != null) {
                FluidStack fluidCoolant = solidCoolant.getFluidFromSolidCoolant(singleCopyCoolant);
                if (fluidCoolant != null && !fluidCoolant.isEmpty()) {
                    try (Transaction tx = Transaction.openRoot()) {
                        int filled = tank.insert(0, FluidResource.of(fluidCoolant), fluidCoolant.getAmount(), tx);
                        if (filled == fluidCoolant.getAmount()) {
                            tx.commit();
                            // Trigger "Ice cool" advancement for using solid coolant
                            buildcraft.lib.misc.AdvancementUtil.unlockAdvancement(
                                player, net.minecraft.resources.Identifier.parse("buildcraftunofficial:ice_cool"));
                            if (!isCreative) {
                                stack.shrink(1);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }
}
