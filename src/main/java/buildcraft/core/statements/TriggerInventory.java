/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;


import buildcraft.api.items.IList;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.platform.transfer.TransferAccess;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerInventory extends AbstractContainerStateTrigger {

    public TriggerInventory(State state) {
        super(state,
            "buildcraft:inventory." + state.name().toLowerCase(Locale.ROOT),
            "buildcraft.inventory." + state.name().toLowerCase(Locale.ROOT)
        );
    }

    @Override
    public SpriteHolder getSprite() {
        return BCCoreSprites.TRIGGER_INVENTORY.get(state);
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.trigger.inventory." + state.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isTriggerActive(Level level, BlockPos pos, @Nullable BlockEntity tile, Direction side,
        IStatementContainer container, IStatementParameter[] parameters) {
        var handler = TransferAccess.findItem(level, pos, side != null ? side.getOpposite() : null);
        if (handler == null) {
            return false;
        }

        ItemStack searchedStack = ItemStack.EMPTY;
        if (parameters != null && parameters.length >= 1 && parameters[0] != null) {
            searchedStack = parameters[0].getItemStack();
        }

        boolean hasSlots = false;
        boolean foundItems = false;
        boolean foundSpace = false;

        boolean isList = !searchedStack.isEmpty() && searchedStack.getItem() instanceof IList;
        IList listFilter = isList ? (IList) searchedStack.getItem() : null;

        int slotCount = handler.size();
        for (int i = 0; i < slotCount; i++) {
            hasSlots = true;
            ItemStack stack = handler.getStack(i);

            boolean stackMatchesSearch;
            if (searchedStack.isEmpty()) {
                stackMatchesSearch = true;
            } else if (isList) {
                stackMatchesSearch = !stack.isEmpty() && listFilter.matches(searchedStack, stack);
            } else {
                stackMatchesSearch = canStacksMerge(stack, searchedStack);
            }

            foundItems |= !stack.isEmpty() && stackMatchesSearch;

            // check space exactly
            boolean hasSpace = false;
            if (stack.isEmpty()) {
                hasSpace = true;
            } else if (searchedStack.isEmpty()) {
                hasSpace = stack.getCount() < stack.getMaxStackSize();
            } else if (isList) {
                hasSpace = stackMatchesSearch && stack.getCount() < stack.getMaxStackSize();
            } else if (canStacksMerge(stack, searchedStack) && stack.getCount() < stack.getMaxStackSize()) {
                int amount = Math.min(searchedStack.getCount(), stack.getMaxStackSize() - stack.getCount());
                int slotCap = handler.getCapacity(i, searchedStack);
                if (amount > 0 && slotCap >= stack.getCount() + amount) {
                    hasSpace = true;
                }
            }

            foundSpace |= hasSpace;
        }

        if (!hasSlots) {
            return false;
        }

        switch (state) {
            case EMPTY:
                return !foundItems;
            case CONTAINS:
                return foundItems;
            case SPACE:
                return foundSpace;
            default:
                return !foundSpace;
        }
    }

    private static boolean canStacksMerge(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(a, b);
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_INVENTORY_ALL;
    }
}
