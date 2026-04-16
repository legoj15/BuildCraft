/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.widget;

import java.util.Optional;

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
            net.neoforged.neoforge.transfer.access.ItemAccess access = net.neoforged.neoforge.transfer.access.ItemAccess.forInfiniteMaterials(player, carried.copyWithCount(1));
            ResourceHandler<FluidResource> itemHandlerIn = access.getCapability(Capabilities.Fluid.ITEM);
            
            if (itemHandlerIn != null) {
                try (Transaction tx = Transaction.openRoot()) {
                    int moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.move(
                            itemHandlerIn, tank, r -> true, Integer.MAX_VALUE, tx
                    );
                    if (moved > 0) {
                        tx.commit();
                        return;
                    }
                }
                
                try (Transaction tx = Transaction.openRoot()) {
                    int moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.move(
                            tank, itemHandlerIn, r -> true, Integer.MAX_VALUE, tx
                    );
                    if (moved > 0) {
                        tx.commit();
                        return;
                    }
                }
            }
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
