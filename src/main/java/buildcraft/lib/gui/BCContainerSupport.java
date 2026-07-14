/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

import io.netty.buffer.Unpooled;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BCLog;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.misc.RegistryUtilBC;
import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.MessageContainerPayload;
import buildcraft.lib.net.PacketBufferBC;

/**
 * The per-instance state + shared logic behind the {@link BCContainer} mixin. Held as a field by the two
 * BuildCraft menu bases ({@link ContainerBC_Neptune} on {@code AbstractContainerMenu} and
 * {@link ContainerBCCrafting} on {@code RecipeBookMenu}) so the widget list, {@code sendMessage}/
 * {@code readMessage} channel, phantom-slot handling and {@code addFullPlayerInventory} live exactly
 * once — the two bases can't share a common BC superclass because one must extend
 * {@link net.minecraft.world.inventory.RecipeBookMenu}.
 *
 * <p>The slot-touching helpers ({@link #addFullPlayerInventory}, {@link #quickMoveStack}) reach the
 * <em>protected</em> {@code AbstractContainerMenu} operations ({@code addSlot}, {@code moveItemStackTo})
 * through functional callbacks — the caller passes {@code this::addSlot} / {@code this::moveItemStackTo}
 * from inside its own class body, where protected access is legal. The read-only {@code slots} list and
 * {@code getCarried()} are public, so this helper touches them directly.
 */
public class BCContainerSupport {

    /** Bridges the container's protected {@code moveItemStackTo(stack, start, end, backwards)}. */
    @FunctionalInterface
    public interface SlotMover {
        boolean move(ItemStack stack, int startSlot, int endSlot, boolean backwards);
    }

    final AbstractContainerMenu menu;
    final Player player;
    private final List<Widget_Neptune<?>> widgets = new ArrayList<>();

    public BCContainerSupport(AbstractContainerMenu menu, Player player) {
        this.menu = menu;
        this.player = player;
    }

    // --- Widgets ---

    public <W extends Widget_Neptune<?>> W addWidget(W widget) {
        if (widget == null) throw new NullPointerException("widget");
        widgets.add(widget);
        return widget;
    }

    public ImmutableList<Widget_Neptune<?>> getWidgets() {
        return ImmutableList.copyOf(widgets);
    }

    public void sendWidgetData(Widget_Neptune<?> widget, IPayloadWriter writer) {
        int widgetId = widgets.indexOf(widget);
        if (widgetId == -1) {
            BCLog.logger.warn("[lib.container] sendWidgetData: widget not found! ("
                + (widget == null ? "null" : widget.getClass()) + ") in " + menu.getClass());
            return;
        }
        sendMessage(ContainerBC_Neptune.NET_WIDGET, (buf) -> {
            buf.writeShort(widgetId);
            writer.write(buf);
        });
    }

    // --- Networking ---

    /**
     * Send a container message to the other side (client↔server). The writer serializes the payload
     * into a {@link PacketBufferBC}.
     */
    public void sendMessage(int id, IPayloadWriter writer) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        writer.write(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();

        MessageContainerPayload payload = new MessageContainerPayload(menu.containerId, id, bytes);
        if (player.level().isClientSide()) {
            //? if >=1.21.10 {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
            //?} else {
            /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);*/
            //?}
        } else if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }

    /**
     * Handle the container-agnostic message IDs (widget sync + JEI ghost-slot set). Returns
     * {@code true} if it consumed the message; the calling base then handles its own IDs (recipe-book
     * placement on {@link ContainerBCCrafting}, machine-specific transfers on the subclasses).
     */
    public boolean readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (id == ContainerBC_Neptune.NET_WIDGET) {
            int widgetId = buffer.readUnsignedShort();
            if (widgetId < 0 || widgetId >= widgets.size()) {
                BCLog.logger.warn("[lib.container] Received invalid widget ID " + widgetId
                    + " (have " + widgets.size() + " widgets)");
                return true;
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
            return true;
        } else if (id == ContainerBC_Neptune.NET_GHOST_SLOT_SET && !isClient) {
            // Server-side: JEI ghost ingredient dropped on a phantom slot.
            int slotIdx = buffer.readUnsignedShort();
            String itemId = buffer.readUtf();
            List<Slot> slots = menu.slots;
            if (slotIdx >= 0 && slotIdx < slots.size() && slots.get(slotIdx) instanceof SlotPhantom phantom) {
                net.minecraft.world.item.Item ghostItem = RegistryUtilBC.getValue(
                    BuiltInRegistries.ITEM, Identifier.parse(itemId));
                if (ghostItem != null) {
                    phantom.set(new ItemStack(ghostItem, 1));
                }
            }
            return true;
        }
        return false;
    }

    // --- Slot layout / movement (protected AbstractContainerMenu ops via callbacks) ---

    public void addFullPlayerInventory(int startX, int startY, Inventory inv, Consumer<Slot> addSlot) {
        for (int sy = 0; sy < 3; sy++) {
            for (int sx = 0; sx < 9; sx++) {
                addSlot.accept(new Slot(inv, sx + sy * 9 + 9, startX + sx * 18, startY + sy * 18));
            }
        }
        for (int sx = 0; sx < 9; sx++) {
            addSlot.accept(new Slot(inv, sx, startX + sx * 18, startY + 58));
        }
    }

    /**
     * Shared shift-click behaviour: container→player uses vanilla {@code moveItemStackTo} (via
     * {@code mover}); player→container uses {@link #moveItemStackToValid} so items skip phantom /
     * display / output-only slots. Byte-identical to the pre-refactor {@code ContainerBC_Neptune}.
     */
    public ItemStack quickMoveStack(int index, SlotMover mover) {
        ItemStack itemstack = ItemStack.EMPTY;
        List<Slot> slots = menu.slots;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return itemstack;

        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        int playerInvSize = 36;
        int containerSlots = slots.size() - playerInvSize;

        if (index < containerSlots) {
            // From container to player
            if (!mover.move(slotStack, containerSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player to container — only target slots that accept items
            // (skip phantom, display, and output-only slots)
            if (!moveItemStackToValid(slotStack, 0, containerSlots)) {
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

    /** Like moveItemStackTo but skips slots where mayPlace() returns false.
     *  This prevents shift-clicking items into phantom/display/output slots. */
    private boolean moveItemStackToValid(ItemStack stack, int startIndex, int endIndex) {
        boolean moved = false;
        List<Slot> slots = menu.slots;

        // First pass: try to merge with existing matching stacks
        for (int i = startIndex; i < endIndex && !stack.isEmpty(); i++) {
            Slot targetSlot = slots.get(i);
            if (!targetSlot.mayPlace(stack)) continue;

            ItemStack existing = targetSlot.getItem();
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(stack, existing)) {
                int maxSize = Math.min(targetSlot.getMaxStackSize(stack), stack.getMaxStackSize());
                int space = maxSize - existing.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, stack.getCount());
                    existing.grow(transfer);
                    stack.shrink(transfer);
                    targetSlot.set(existing);
                    moved = true;
                }
            }
        }

        // Second pass: try to place into empty slots
        for (int i = startIndex; i < endIndex && !stack.isEmpty(); i++) {
            Slot targetSlot = slots.get(i);
            if (!targetSlot.mayPlace(stack)) continue;

            if (targetSlot.getItem().isEmpty()) {
                int maxSize = Math.min(targetSlot.getMaxStackSize(stack), stack.getMaxStackSize());
                int transfer = Math.min(maxSize, stack.getCount());
                targetSlot.set(stack.split(transfer));
                moved = true;
            }
        }

        return moved;
    }

    /**
     * Phantom-slot click: if the clicked slot is a {@link SlotPhantom}, set/clear its filter from the
     * carried item (consuming nothing) and report {@code true} so the base skips vanilla's
     * {@code super.clicked}. Returns {@code false} for a normal slot, letting the base fall through.
     */
    public boolean handlePhantomClick(int slotId) {
        Slot slot = slotId < 0 ? null : menu.slots.get(slotId);
        if (slot instanceof SlotPhantom phantom) {
            ItemStack held = menu.getCarried();
            if (held.isEmpty()) {
                phantom.set(ItemStack.EMPTY);
            } else {
                ItemStack copy = held.copy();
                copy.setCount(1);
                phantom.set(copy);
            }
            return true;
        }
        return false;
    }
}
