/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.slot;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;

import buildcraft.lib.tile.item.IItemHandlerAdv;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.platform.transfer.BCTransaction;

public class SlotBase extends Slot {
    public final int handlerIndex;
    public final IItemHandlerAdv itemHandler;

    public SlotBase(IItemHandlerAdv itemHandler, int slotIndex, int posX, int posY) {
        super(new DummyContainer(itemHandler), slotIndex, posX, posY);
        this.handlerIndex = slotIndex;
        this.itemHandler = itemHandler;
    }

    public boolean canShift() {
        return true;
    }

    @Override
    public boolean mayPlace(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return false;
        return itemHandler.canSet(handlerIndex, stack);
    }

    @Override
    @Nonnull
    public ItemStack getItem() {
        // A copy on 1.21.10+ (what the Transfer API's resource view always gave); 1.21.1 keeps handing out the live
        // stack, as its classic IItemHandler did. ItemHandlerSimple is the only IItemHandlerAdv.
        //? if >=1.21.10 {
        return itemHandler.getStack(handlerIndex);
        //?} else {
        /*return ((ItemHandlerSimple) itemHandler).getStackInSlot(handlerIndex);*/
        //?}
    }

    @Override
    public void set(@Nonnull ItemStack stack) {
        if (itemHandler instanceof ItemHandlerSimple) {
            ((ItemHandlerSimple) itemHandler).setStackInSlot(handlerIndex, stack);
        } else {
            try (BCTransaction tx = BCTransaction.openRoot()) {
                ItemStack current = itemHandler.getStack(handlerIndex);
                if (!current.isEmpty()) {
                    itemHandler.extract(handlerIndex, current, current.getCount(), tx);
                }
                if (!stack.isEmpty()) {
                    itemHandler.insert(handlerIndex, stack, stack.getCount(), tx);
                }
                tx.commit();
            }
        }
        this.setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }

    @Override
    public int getMaxStackSize() {
        //? if >=1.21.10 {
        return itemHandler.getCapacity(handlerIndex, ItemStack.EMPTY);
        //?} else {
        /*return ((ItemHandlerSimple) itemHandler).getSlotLimit(handlerIndex);*/
        //?}
    }

    @Override
    public int getMaxStackSize(@Nonnull ItemStack stack) {
        //? if >=1.21.10 {
        int slotLimit = itemHandler.getCapacity(handlerIndex, stack);
        //?} else {
        /*int slotLimit = ((ItemHandlerSimple) itemHandler).getSlotLimit(handlerIndex);*/
        //?}
        return Math.min(slotLimit, stack.getMaxStackSize());
    }

    @Override
    @Nonnull
    public ItemStack remove(int amount) {
        ItemStack current = getItem();
        if (current.isEmpty()) return ItemStack.EMPTY;
        int extracted = 0;
        try (BCTransaction tx = BCTransaction.openRoot()) {
            extracted = itemHandler.extract(handlerIndex, current, amount, tx);
            tx.commit();
        }
        return current.copyWithCount(extracted);
    }

    // Dummy Container implementation
    private static class DummyContainer implements Container {
        private final IItemHandlerAdv handler;
        public DummyContainer(IItemHandlerAdv handler) { this.handler = handler; }
        @Override public int getContainerSize() { return handler.size(); }
        //? if >=1.21.10 {
        @Override public ItemStack getItem(int index) { return handler.getStack(index); }
        //?} else {
        /*@Override public ItemStack getItem(int index) { return ((ItemHandlerSimple) handler).getStackInSlot(index); }*/
        //?}

        @Override public boolean isEmpty() { return false; }
        @Override public ItemStack removeItem(int index, int count) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItemNoUpdate(int index) { return ItemStack.EMPTY; }
        @Override public void setItem(int index, ItemStack stack) {}
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {}
    }
}
