/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.list;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.items.IList;

public enum ListTooltipHandler {
    INSTANCE;

    /** Shows "Matches" on a stack the open List GUI's list matches (a ClientHooks item-tooltip listener). */
    public void itemTooltip(ItemStack stack, Player player, List<Component> tooltip) {
        if (!stack.isEmpty() && player != null && player.containerMenu instanceof ContainerList containerList) {
            ItemStack list = containerList.getListItemStack();
            if (!list.isEmpty() && list.getItem() instanceof IList listItem) {
                if (listItem.matches(list, stack)) {
                    tooltip.add(
                        Component.translatable("tip.list.matches").withStyle(ChatFormatting.GREEN)
                    );
                }
            }
        }
    }
}
