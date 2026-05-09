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

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
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

    private final FluidTank tank;

    public WidgetFluidTank(ContainerBC_Neptune container, FluidTank tank) {
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
    @SuppressWarnings("deprecation") // FluidUtil is deprecated in 1.21.11 but still functional
    private void onGuiClicked() {
        Player player = container.player;
        ItemStack held = player.containerMenu.getCarried();
        if (held.isEmpty()) {
            return;
        }

        ItemStack result = transferStackToTank(player, held);
        player.containerMenu.setCarried(result);
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
    @SuppressWarnings("deprecation") // FluidUtil is deprecated in 1.21.11 but still functional
    private ItemStack transferStackToTank(Player player, ItemStack stack) {
        if (player.level().isClientSide()) {
            return stack;
        }

        ItemStack original = stack;
        boolean isCreative = player.getAbilities().instabuild;

        // --- Try filling the tank from the item ---
        {
            ItemStack singleCopy = stack.copyWithCount(1);
            Optional<IFluidHandlerItem> opt = FluidUtil.getFluidHandler(singleCopy);
            if (opt.isPresent()) {
                IFluidHandlerItem itemHandler = opt.get();
                // Simulate draining from the item
                FluidStack drained = itemHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                if (!drained.isEmpty()) {
                    // Simulate filling the tank
                    int accepted = tank.fill(drained, IFluidHandler.FluidAction.SIMULATE);
                    if (accepted > 0) {
                        // Do it for real with a fresh copy
                        ItemStack fillCopy = stack.copyWithCount(1);
                        Optional<IFluidHandlerItem> fillOpt = FluidUtil.getFluidHandler(fillCopy);
                        if (fillOpt.isPresent()) {
                            IFluidHandlerItem fillHandler = fillOpt.get();
                            FluidStack toDrain = drained.copyWithAmount(accepted);
                            FluidStack realDrained = fillHandler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
                            if (!realDrained.isEmpty()) {
                                int reallyFilled = tank.fill(realDrained, IFluidHandler.FluidAction.EXECUTE);
                                if (reallyFilled > 0) {
                                    ItemStack containerResult = fillHandler.getContainer();
                                    if (isCreative) {
                                        return original;
                                    }
                                    if (original.getCount() == 1) {
                                        return containerResult;
                                    } else {
                                        original.shrink(1);
                                        if (!containerResult.isEmpty()) {
                                            if (!player.getInventory().add(containerResult)) {
                                                player.drop(containerResult, false);
                                            }
                                        }
                                        return original;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Try draining the tank into the item ---
        {
            ItemStack drainCopy = stack.copyWithCount(1);
            Optional<IFluidHandlerItem> opt = FluidUtil.getFluidHandler(drainCopy);
            if (opt.isPresent()) {
                IFluidHandlerItem drainHandler = opt.get();
                FluidStack inTank = tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                if (!inTank.isEmpty()) {
                    int filled = drainHandler.fill(inTank, IFluidHandler.FluidAction.SIMULATE);
                    if (filled > 0) {
                        // Actually drain from tank and fill into item
                        FluidStack reallyDrained = tank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                        if (!reallyDrained.isEmpty()) {
                            drainHandler.fill(reallyDrained, IFluidHandler.FluidAction.EXECUTE);
                            ItemStack containerResult = drainHandler.getContainer();
                            if (isCreative) {
                                return original;
                            }
                            if (original.getCount() == 1) {
                                return containerResult;
                            } else {
                                original.shrink(1);
                                if (!containerResult.isEmpty()) {
                                    if (!player.getInventory().add(containerResult)) {
                                        player.drop(containerResult, false);
                                    }
                                }
                                return original;
                            }
                        }
                    }
                }
            }
        }

        // --- Try solid coolant conversion (ice → water, ported from 1.12.2 Tank.map()) ---
        if (BuildcraftFuelRegistry.coolant != null) {
            ItemStack singleCopy = stack.copyWithCount(1);
            ISolidCoolant solidCoolant = BuildcraftFuelRegistry.coolant.getSolidCoolant(singleCopy);
            if (solidCoolant != null) {
                FluidStack fluidCoolant = solidCoolant.getFluidFromSolidCoolant(singleCopy);
                if (fluidCoolant != null && !fluidCoolant.isEmpty()) {
                    int space = tank.getCapacity() - tank.getFluidAmount();
                    if (fluidCoolant.getAmount() <= space) {
                        int filled = tank.fill(fluidCoolant, IFluidHandler.FluidAction.EXECUTE);
                        if (filled > 0) {
                            // Trigger "Ice cool" advancement for using solid coolant
                            buildcraft.lib.misc.AdvancementUtil.unlockAdvancement(
                                player, net.minecraft.resources.ResourceLocation.parse("buildcraftenergy:ice_cool"));
                            if (!isCreative) {
                                stack.shrink(1);
                            }
                            return stack;
                        }
                    }
                }
            }
        }

        return stack;
    }
}
