/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Locale;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;

import net.neoforged.neoforge.items.IItemHandler;

import buildcraft.api.items.IList;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerInventoryLevel extends BCStatement implements ITriggerExternal {
    public TriggerType type;

    public TriggerInventoryLevel(TriggerType type) {
        super("buildcraft:inventorylevel." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft.inventorylevel." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft.filteredBuffer." + type.name().toLowerCase(Locale.ROOT));
        this.type = type;
    }

    @Override
    public int maxParameters() {
        return 1;
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
    public boolean isTriggerActive(BlockEntity tile, Direction side, IStatementContainer container,
        IStatementParameter[] parameters) {
        if (!(tile instanceof IItemHandler itemHandler)) {
            return false;
        }

        StatementParameterItemStack param = getParam(0, parameters, new StatementParameterItemStack());
        ItemStack searchStack = param.getItemStack();

        int itemSpace = 0;
        int foundItems = 0;
        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty()) {
                if (searchStack.isEmpty()) {
                    itemSpace += itemHandler.getSlotLimit(slot);
                } else {
                    if (searchStack.getItem() instanceof IList) {
                        // Lists are too generic; skip without a filtered inventory
                    } else {
                        ItemStack stack = searchStack.copy();
                        int count = Math.min(itemHandler.getSlotLimit(slot), searchStack.getMaxStackSize());
                        stack.setCount(count);
                        ItemStack leftOver = itemHandler.insertItem(slot, stack, true);
                        if (leftOver.isEmpty()) {
                            itemSpace += count;
                        } else {
                            itemSpace += count - leftOver.getCount();
                        }
                    }
                }
            } else {
                if (searchStack.isEmpty() || matchesStackOrList(searchStack, stackInSlot)) {
                    itemSpace += Math.min(stackInSlot.getMaxStackSize(), itemHandler.getSlotLimit(slot));
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
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_INVENTORY_ALL;
    }

    public enum TriggerType {
        BELOW25(0.25F),
        BELOW50(0.5F),
        BELOW75(0.75F);

        TriggerType(float level) {
            this.level = level;
        }

        public static final TriggerType[] VALUES = values();

        public final float level;
    }
}
