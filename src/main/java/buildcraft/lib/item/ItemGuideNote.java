/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A guide "note" item that stores a note ID and opens a writable guide page.
 * The GUI is deferred — right-clicking currently shows a placeholder chat message.
 */
public class ItemGuideNote extends Item {

    public ItemGuideNote(Item.Properties properties) {
        super(properties);
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
        if (!level.isClientSide()) {
            buildcraft.lib.misc.MessageUtil.sendSystemMessage(player,
                    Component.translatable("buildcraft.guide.not_available"));
        }
        return InteractionResult.SUCCESS;
    }
}
