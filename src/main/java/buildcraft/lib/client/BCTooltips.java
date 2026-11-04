/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class BCTooltips {

    private static final Map<net.minecraft.world.item.Item, String> TOOLTIPS = new IdentityHashMap<>();

    public static void addTooltip(ItemLike item, String translationKey) {
        TOOLTIPS.put(item.asItem(), translationKey);
    }

    /** Appends an item's registered static tooltip (a ClientHooks item-tooltip listener). */
    public static void onItemTooltip(ItemStack stack, Player player, java.util.List<Component> tooltip) {
        String key = TOOLTIPS.get(stack.getItem());
        if (key != null) {
            // Prefer an RF-naming sibling (key + ".rf") if the user has the toggle on
            // AND that variant exists in the lang file. Falls back to the base key otherwise.
            String resolved = buildcraft.energy.BCEnergyConfig.rfNamedKey(key);
            if (net.minecraft.locale.Language.getInstance().has(resolved)) {
                String translated = net.minecraft.client.resources.language.I18n.get(resolved);
                for (String line : translated.split("\n")) {
                    tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                }
            } else {
                tooltip.add(Component.translatable(resolved).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
