/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.inventory.IItemHandlerFiltered;
import buildcraft.platform.transfer.BCTransactionContext;

public class DelegateItemHandler implements IBCItemHandler, IItemHandlerFiltered {
    protected final IBCItemHandler delegate;

    public DelegateItemHandler(IBCItemHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public ItemStack getStack(int index) {
        return delegate.getStack(index);
    }

    @Override
    public int getCapacity(int index, ItemStack resource) {
        return delegate.getCapacity(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemStack resource) {
        return delegate.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemStack resource, int amount, BCTransactionContext tx) {
        return delegate.insert(index, resource, amount, tx);
    }

    @Override
    public int extract(int index, ItemStack resource, int amount, BCTransactionContext tx) {
        return delegate.extract(index, resource, amount, tx);
    }

    @Override
    public ItemStack getFilter(int slot) {
        if (delegate instanceof IItemHandlerFiltered) {
            return ((IItemHandlerFiltered) delegate).getFilter(slot);
        }
        return delegate.getStack(slot);
    }
}
