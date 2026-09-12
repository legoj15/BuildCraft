/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.container.ContainerRequester;

/** The Requester block entity, ported from 7.1.x {@code buildcraft.robotics.TileRequester}: a 20-slot
 *  request network endpoint. Each slot pairs a GHOST template ({@link #invRequests}, how much of what is
 *  wanted — set through the GUI's phantom slots, never a real item) with a REAL delivery slot
 *  ({@link #invDeliveries}, where a delivery robot drops the goods). Robots find this tile through
 *  {@code DockingStationPipe.getRequestProvider()}'s six-neighbour scan, then fulfil
 *  {@link #getRequest(int)} with {@link #offerItem(int, ItemStack)}.
 *
 * <p>7.1.x's {@code isItemValidForSlot} (insertion only valid where the template matches) lives on as the
 *  delivery handler's insertion checker, so pipes and players alike can only put a slot's requested item
 *  into it. The templates are an {@link ItemHandlerManager.EnumAccess#PHANTOM} handler: GUI-owned, never
 *  pipe-exposed, and excluded from break drops (a template is configuration, not an item — dropping it
 *  would mint items out of nothing). */
public class TileRequester extends TileBC_Neptune implements IBCMenuProvider, IRequestProvider {
    public static final int NB_ITEMS = 20;

    /** Where deliveries land. Free extraction (the goods are yours once delivered), checked insertion. */
    public final ItemHandlerSimple invDeliveries = itemManager.addInvHandler(
        "inv", NB_ITEMS, this::isItemValidForSlot,
        ItemHandlerManager.EnumAccess.BOTH, EnumPipePart.VALUES);

    /** The ghost templates. Phantom access: GUI-only, invisible to pipes, not dropped on break. */
    public final ItemHandlerSimple invRequests = itemManager.addInvHandler(
        "req", NB_ITEMS, ItemHandlerManager.EnumAccess.PHANTOM);

    public TileRequester(BlockPos pos, BlockState state) {
        super(BCRoboticsBlockEntities.REQUESTER.get(), pos, state);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.buildcraftunofficial.requester");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerRequester(containerId, playerInventory, this);
    }

    /** 7.1.x {@code setRequest} server half — the container's phantom click lands here. */
    public void setRequest(int index, ItemStack stack) {
        invRequests.setStackInSlot(index, stack);
        setChanged();
    }

    public ItemStack getRequestTemplate(int index) {
        return invRequests.getStackInSlot(index);
    }

    /** 7.1.x {@code isItemValidForSlot}: a slot takes an item only when a template exists for it and the
     *  item matches. */
    private boolean isItemValidForSlot(int slot, ItemStack stack) {
        ItemStack template = invRequests.getStackInSlot(slot);
        return !template.isEmpty() && StackUtil.isMatchingItemOrList(template, stack);
    }

    @Override
    protected boolean spillsContentsOnRemoval() {
        return true;
    }

    // ── IRequestProvider ─────────────────────────────────────────────────
    // Verbatim 7.1.x arithmetic against empty-not-null stacks.

    @Override
    public int getRequestsCount() {
        return NB_ITEMS;
    }

    public boolean isFulfilled(int i) {
        ItemStack template = invRequests.getStackInSlot(i);
        if (template.isEmpty()) {
            return true;
        }
        ItemStack existing = invDeliveries.getStackInSlot(i);
        if (existing.isEmpty()) {
            return false;
        }
        return StackUtil.isMatchingItemOrList(template, existing) && existing.getCount() >= template.getCount();
    }

    @Override
    public ItemStack getRequest(int i) {
        ItemStack template = invRequests.getStackInSlot(i);
        if (template.isEmpty() || isFulfilled(i)) {
            return ItemStack.EMPTY;
        }
        ItemStack request = template.copy();

        ItemStack existingStack = invDeliveries.getStackInSlot(i);
        if (existingStack.isEmpty()) {
            return request;
        }

        if (!StackUtil.isMatchingItemOrList(request, existingStack)) {
            return ItemStack.EMPTY;
        }

        request.setCount(request.getCount() - existingStack.getCount());
        if (request.getCount() <= 0) {
            return ItemStack.EMPTY;
        }

        return request;
    }

    @Override
    public ItemStack offerItem(int i, ItemStack stack) {
        ItemStack existingStack = invDeliveries.getStackInSlot(i);
        ItemStack template = invRequests.getStackInSlot(i);

        if (template.isEmpty()) {
            return stack;
        } else if (existingStack.isEmpty()) {
            if (!StackUtil.isMatchingItemOrList(stack, template)) {
                return stack;
            }

            int maxQty = template.getCount();

            if (stack.getCount() <= maxQty) {
                invDeliveries.setStackInSlot(i, stack.copy());
                return ItemStack.EMPTY;
            } else {
                ItemStack newStack = stack.copy();
                newStack.setCount(maxQty);
                stack.setCount(stack.getCount() - maxQty);

                invDeliveries.setStackInSlot(i, newStack);

                return stack;
            }
        } else if (!StackUtil.isMatchingItemOrList(stack, existingStack)) {
            return stack;
        } else if (StackUtil.isMatchingItemOrList(stack, template)) {
            int maxQty = template.getCount();

            if (existingStack.getCount() + stack.getCount() <= maxQty) {
                existingStack.grow(stack.getCount());
                invDeliveries.setStackInSlot(i, existingStack);
                return ItemStack.EMPTY;
            } else {
                stack.setCount(stack.getCount() - (maxQty - existingStack.getCount()));
                existingStack.setCount(maxQty);
                invDeliveries.setStackInSlot(i, existingStack);
                return stack;
            }
        } else {
            return stack;
        }
    }
}
