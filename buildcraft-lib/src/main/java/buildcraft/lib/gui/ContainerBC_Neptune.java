/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import io.netty.buffer.Unpooled;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BCLog;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.MessageContainerPayload;
import buildcraft.lib.net.PacketBufferBC;

/**
 * Base container class for all BuildCraft GUIs.
 * Provides shift-click logic, phantom slot handling, and widget sync via
 * {@link MessageContainerPayload}.
 */
public abstract class ContainerBC_Neptune extends AbstractContainerMenu {

    public static final int NET_WIDGET = 0;

    public final Player player;
    private final List<Widget_Neptune<?>> widgets = new ArrayList<>();

    protected ContainerBC_Neptune(MenuType<?> menuType, int containerId, Player player) {
        super(menuType, containerId);
        this.player = player;
    }

    protected void addFullPlayerInventory(int startX, int startY) {
        addFullPlayerInventory(startX, startY, player.getInventory());
    }

    protected void addFullPlayerInventory(int startX, int startY, Inventory inv) {
        for (int sy = 0; sy < 3; sy++) {
            for (int sx = 0; sx < 9; sx++) {
                addSlot(new Slot(inv, sx + sy * 9 + 9, startX + sx * 18, startY + sy * 18));
            }
        }
        for (int sx = 0; sx < 9; sx++) {
            addSlot(new Slot(inv, sx, startX + sx * 18, startY + 58));
        }
    }

    public <W extends Widget_Neptune<?>> W addWidget(W widget) {
        if (widget == null) throw new NullPointerException("widget");
        widgets.add(widget);
        return widget;
    }

    public ImmutableList<Widget_Neptune<?>> getWidgets() {
        return ImmutableList.copyOf(widgets);
    }

    // --- Networking ---

    /**
     * Send a container message to the other side (client↔server).
     * The writer serializes the payload into a {@link PacketBufferBC}.
     */
    public final void sendMessage(int id, IPayloadWriter writer) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        writer.write(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();

        MessageContainerPayload payload = new MessageContainerPayload(containerId, id, bytes);
        if (player.level().isClientSide()) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
        } else if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }

    /**
     * Package-private: called by {@link Widget_Neptune#sendWidgetData} to route
     * widget data through the container's networking.
     */
    void sendWidgetData(Widget_Neptune<?> widget, IPayloadWriter writer) {
        int widgetId = widgets.indexOf(widget);
        if (widgetId == -1) {
            BCLog.logger.warn("[lib.container] sendWidgetData: widget not found! ("
                + (widget == null ? "null" : widget.getClass()) + ") in " + getClass());
            return;
        }
        sendMessage(NET_WIDGET, (buf) -> {
            buf.writeShort(widgetId);
            writer.write(buf);
        });
    }

    /**
     * Handle an incoming container message. Called by {@link MessageContainerPayload#handle}.
     *
     * @param id       the message ID
     * @param buffer   the payload buffer
     * @param isClient true if we're on the client side
     * @param ctx      the payload context
     */
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (id == NET_WIDGET) {
            int widgetId = buffer.readUnsignedShort();
            if (widgetId < 0 || widgetId >= widgets.size()) {
                BCLog.logger.warn("[lib.container] Received invalid widget ID " + widgetId
                    + " (have " + widgets.size() + " widgets)");
                return;
            }
            Widget_Neptune<?> widget = widgets.get(widgetId);
            try {
                if (isClient) {
                    widget.handleWidgetDataClient(ctx, buffer);
                } else {
                    widget.handleWidgetDataServer(ctx, buffer);
                }
            } catch (Exception e) {
                BCLog.logger.warn("[lib.container] Error handling widget data for widget " + widgetId, e);
            }
        }
    }

    // --- Slot handling ---

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        Slot slot = slotId < 0 ? null : this.slots.get(slotId);
        if (slot instanceof SlotPhantom) {
            SlotPhantom phantom = (SlotPhantom) slot;
            ItemStack held = getCarried();
            if (held.isEmpty()) {
                phantom.set(ItemStack.EMPTY);
            } else {
                ItemStack copy = held.copy();
                copy.setCount(1);
                phantom.set(copy);
            }
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return itemstack;

        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        int playerInvSize = 36;
        int containerSlots = this.slots.size() - playerInvSize;

        if (index < containerSlots) {
            // From container to player
            if (!this.moveItemStackTo(slotStack, containerSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player to container
            if (!this.moveItemStackTo(slotStack, 0, containerSlots, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // Subclasses override
    }
}
