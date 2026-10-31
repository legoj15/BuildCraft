/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.inventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?} else {
/*import net.neoforged.neoforge.items.IItemHandler;*/
//?}

import buildcraft.api.core.IStackFilter;

import buildcraft.lib.misc.StackUtil;

/** An {@link buildcraft.api.inventory.IItemTransactor} over a block's or an entity's item capability, the inventory
 *  surface pipes use (vanilla containers through NeoForge's wrapper, modded storage and BuildCraft's own machines
 *  directly). {@link InventoryWrapper} covers only vanilla {@code Container}s, which most modded
 *  inventories are not. The handler decides what may leave or enter each slot, so insert-only and extract-only
 *  slots behave here exactly as they do for a pipe. */
public final class ItemHandlerTransactor extends AbstractInvItemTransactor {
    /** The transactor over {@code entity}'s item capability (the whole inventory, not an automation view), or null
     *  if the entity has none. NeoForge registers it for the vanilla chest and hopper minecarts and boats, and a
     *  modded entity that carries items offers them this way. */
    @Nullable
    public static ItemHandlerTransactor ofEntity(@Nonnull Entity entity) {
        //? if >=1.21.10 {
        ResourceHandler<ItemResource> handler = entity.getCapability(Capabilities.Item.ENTITY);
        //?} else {
        /*IItemHandler handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);*/
        //?}
        return handler == null ? null : new ItemHandlerTransactor(handler);
    }

    //? if >=1.21.10 {
    private final ResourceHandler<ItemResource> handler;

    public ItemHandlerTransactor(ResourceHandler<ItemResource> handler) {
        this.handler = handler;
    }
    //?} else {
    /*private final IItemHandler handler;

    public ItemHandlerTransactor(IItemHandler handler) {
        this.handler = handler;
    }*/
    //?}

    @Override
    @Nonnull
    protected ItemStack insert(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return StackUtil.EMPTY;
        }
        //? if >=1.21.10 {
        int inserted;
        try (Transaction tx = Transaction.openRoot()) {
            inserted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) {
                tx.commit();
            }
        }
        if (inserted >= stack.getCount()) {
            return StackUtil.EMPTY;
        }
        return stack.copyWithCount(stack.getCount() - inserted);
        //?} else {
        /*return handler.insertItem(slot, stack, simulate);*/
        //?}
    }

    @Override
    @Nonnull
    protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
        ItemStack current = slotContents(slot);
        if (current.isEmpty() || current.getCount() < min || !filter.matches(current.copy())) {
            return StackUtil.EMPTY;
        }
        int wanted = Math.min(current.getCount(), max);
        //? if >=1.21.10 {
        ItemResource resource = ItemResource.of(current);
        int extracted;
        try (Transaction tx = Transaction.openRoot()) {
            extracted = handler.extract(slot, resource, wanted, tx);
            if (extracted < min) {
                return StackUtil.EMPTY;
            }
            if (!simulate) {
                tx.commit();
            }
        }
        return resource.toStack(extracted);
        //?} else {
        /*ItemStack extracted = handler.extractItem(slot, wanted, true);
        if (extracted.getCount() < min) {
            return StackUtil.EMPTY;
        }
        return simulate ? extracted : handler.extractItem(slot, extracted.getCount(), false);*/
        //?}
    }

    @Override
    protected int size() {
        //? if >=1.21.10 {
        return handler.size();
        //?} else {
        /*return handler.getSlots();*/
        //?}
    }

    @Override
    protected boolean isEmpty(int slot) {
        return slotContents(slot).isEmpty();
    }

    @Override
    @Nonnull
    protected ItemStack slotContents(int slot) {
        //? if >=1.21.10 {
        ItemResource resource = handler.getResource(slot);
        int amount = handler.getAmountAsInt(slot);
        return resource.isEmpty() || amount <= 0 ? StackUtil.EMPTY : resource.toStack(amount);
        //?} else {
        /*return handler.getStackInSlot(slot);*/
        //?}
    }
}
