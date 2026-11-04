/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.minecraft.world.item.ItemStack;

import buildcraft.platform.transfer.BCTransactionContext;

public class WrappedItemHandlerExtract extends DelegateItemHandler {
    public WrappedItemHandlerExtract(IBCItemHandler delegate) {
        super(delegate);
    }

    @Override
    public int insert(int index, ItemStack resource, int amount, BCTransactionContext tx) {
        return 0;
    }
}
