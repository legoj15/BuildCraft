/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.widget;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;


import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.ISolidCoolant;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.Widget_Neptune;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.platform.Platform;
import buildcraft.platform.network.PacketContext;
import buildcraft.platform.transfer.BCTransaction;

/**
 * Widget that allows clicking on a fluid tank in a GUI to fill/drain with the carried item.
 * <p>
 * When the client clicks the tank area, it sends {@link #NET_CLICK} to the server.
 * The server then attempts to transfer fluid between the player's carried item and the tank.
 */
public class WidgetFluidTank extends Widget_Neptune<ContainerBC_Neptune> {
    private static final byte NET_CLICK = 0;

    private final BCFluidTank tank;

    public WidgetFluidTank(ContainerBC_Neptune container, BCFluidTank tank) {
        super(container);
        this.tank = tank;
    }

    @Override
    public void handleWidgetDataServer(PacketContext ctx, PacketBufferBC buffer) {
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
        // A fluid container on the cursor: empty it into the tank, else fill it, else fill an empty container
        // in the inventory (creative leaves the cursor item alone). The platform layer does the item swap.
        if (Platform.transfer().exchangeWithCarriedContainer(player, tank)) {
            return;
        }

        // --- Try solid coolant conversion (ice → water, ported from 1.12.2 Tank.map()) ---
        if (BuildcraftFuelRegistry.coolant != null) {
            ItemStack stack = player.containerMenu.getCarried();
            ItemStack singleCopyCoolant = stack.copyWithCount(1);
            ISolidCoolant solidCoolant = BuildcraftFuelRegistry.coolant.getSolidCoolant(singleCopyCoolant);
            if (solidCoolant != null) {
                BCFluidStack fluidCoolant = solidCoolant.getFluidFromSolidCoolant(singleCopyCoolant);
                if (fluidCoolant != null && !fluidCoolant.isEmpty()) {
                    try (BCTransaction tx = BCTransaction.openRoot()) {
                        int filled = tank.insert(0, fluidCoolant, fluidCoolant.getAmount(), tx);
                        if (filled == fluidCoolant.getAmount()) {
                            tx.commit();
                            // Trigger "Ice cool" advancement for using solid coolant
                            buildcraft.lib.misc.AdvancementUtil.unlockAdvancement(
                                player, net.minecraft.resources.Identifier.parse("buildcraftunofficial:ice_cool"));
                            if (!player.getAbilities().instabuild) {
                                stack.shrink(1);
                            }
                        }
                    }
                }
            }
        }
    }
}
