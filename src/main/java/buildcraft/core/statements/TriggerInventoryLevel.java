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
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.platform.transfer.TransferAccess;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerInventoryLevel extends AbstractContainerLevelTrigger {

    public TriggerInventoryLevel(TriggerType type) {
        super(type,
            "buildcraft:inventorylevel." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft.inventorylevel." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft.filteredBuffer." + type.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public SpriteHolder getSprite() {
        return BCCoreSprites.TRIGGER_INVENTORY_LEVEL.get(type);
    }

    @Override
    public String getDescription() {
        return String.format(LocaleUtil.localize("gate.trigger.inventorylevel.below"), (int) (type.level * 100));
    }

    @Override
    public boolean isTriggerActive(Level level, BlockPos pos, @Nullable BlockEntity tile, Direction side,
        IStatementContainer container, IStatementParameter[] parameters) {
        var itemHandler = TransferAccess.findItem(level, pos, side != null ? side.getOpposite() : null);
        if (itemHandler == null) {
            return false;
        }

        StatementParameterItemStack param = getParam(0, parameters, new StatementParameterItemStack());
        ItemStack searchStack = param.getItemStack();

        int itemSpace = 0;
        int foundItems = 0;
        int slotCount = itemHandler.size();
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stackInSlot = itemHandler.getStack(slot);
            if (stackInSlot.isEmpty()) {
                if (searchStack.isEmpty()) {
                    itemSpace += itemHandler.getCapacity(slot, ItemStack.EMPTY);
                } else {
                    if (searchStack.getItem() instanceof IList) {
                        // Lists are too generic; skip without a filtered inventory
                    } else {
                        int count = Math.min(itemHandler.getCapacity(slot, searchStack), searchStack.getMaxStackSize());
                        itemSpace += count;
                    }
                }
            } else {
                if (searchStack.isEmpty() || matchesStackOrList(searchStack, stackInSlot)) {
                    itemSpace += Math.min(stackInSlot.getMaxStackSize(), itemHandler.getCapacity(slot, stackInSlot));

                    foundItems += stackInSlot.getCount();
                }
            }
        }

        if (itemSpace > 0) {
            float percentage = foundItems / (float) itemSpace;
            return percentage < type.level;
        }

        return false;
    }

    private static boolean matchesStackOrList(ItemStack filter, ItemStack target) {
        if (filter.isEmpty() || target.isEmpty()) return false;
        if (filter.getItem() instanceof IList list) {
            return list.matches(filter, target);
        }
        return ItemStack.isSameItemSameComponents(filter, target);
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_INVENTORY_ALL;
    }
}
