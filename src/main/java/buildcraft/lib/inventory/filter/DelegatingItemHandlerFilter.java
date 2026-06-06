/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.inventory.filter;

import javax.annotation.Nonnull;

import net.minecraft.world.item.ItemStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.item.ItemResource;
//?}

import buildcraft.api.core.IStackFilter;
import buildcraft.lib.tile.item.IBCItemHandler;

public class DelegatingItemHandlerFilter implements IStackFilter {
    private final ISingleStackFilter perStackFilter;
    private final IBCItemHandler handler;

    public DelegatingItemHandlerFilter(ISingleStackFilter perStackFilter, IBCItemHandler handler) {
        this.perStackFilter = perStackFilter;
        this.handler = handler;
    }

    @Override
    public boolean matches(@Nonnull ItemStack stack) {
        //? if >=1.21.10 {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource res = handler.getResource(slot);
            if (!res.isEmpty()) {
                ItemStack slotStack = res.toStack(handler.getAmountAsInt(slot));
                if (perStackFilter.matches(slotStack, stack)) {
                    return true;
                }
            }
        }
        //?} else {
        /*for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (!slotStack.isEmpty()) {
                if (perStackFilter.matches(slotStack, stack)) {
                    return true;
                }
            }
        }*/
        //?}
        return false;
    }
}
