/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.lib.misc.AdvancementUtil;

/**
 * The BuildCraft guide book item. Each registration binds a fixed book name
 * (e.g. "buildcraftunofficial:main" or "buildcraftunofficial:config"); the
 * inventory display name is resolved via vanilla descriptionId from the item
 * registry id.
 */
public class ItemGuide extends Item {
    private static final Identifier ADVANCEMENT = Identifier.parse("buildcraftunofficial:guide");

    private final String bookName;

    public ItemGuide(Item.Properties properties, String bookName) {
        super(properties);
        this.bookName = bookName;
    }

    @Override
    //? if >=1.21.10 {
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return useImpl(level, player, hand);
    }
    //?} else {
    /*public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new net.minecraft.world.InteractionResultHolder<>(useImpl(level, player, hand), player.getItemInHand(hand));
    }*/
    //?}

    private InteractionResult useImpl(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            buildcraft.lib.client.BCLibClient.openGuideScreen(bookName);
        } else {
            AdvancementUtil.unlockAdvancement(player, ADVANCEMENT);
        }
        return InteractionResult.SUCCESS;
    }
}
