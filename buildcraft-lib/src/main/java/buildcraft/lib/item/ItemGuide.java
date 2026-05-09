/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.lib.BCLib;
import buildcraft.lib.misc.AdvancementUtil;

/**
 * The BuildCraft guide book item. In 1.12 this supported multiple "books" via
 * an NBT tag; now it uses a {@link net.minecraft.core.component.DataComponentType}
 * to store the book name. The guide book GUI is deferred — right-clicking
 * currently shows a placeholder chat message.
 */
public class ItemGuide extends Item {
    private static final ResourceLocation ADVANCEMENT = ResourceLocation.parse("buildcraftcore:guide");
    public static final String DEFAULT_BOOK = "buildcraftcore:main";

    public ItemGuide(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            String bookName = getBookName(stack);
            buildcraft.lib.client.BCLibClient.openGuideScreen(bookName);
        } else {
            AdvancementUtil.unlockAdvancement(player, ADVANCEMENT);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        String bookName = getBookName(stack);
        // Map known book names to their translation keys
        String key = switch (bookName) {
            case "buildcraftcore:main" -> "buildcraft.guide.book.buildcraftcore_main";
            case "buildcraftlib:config" -> "buildcraft.guide.book.buildcraftlib_config";
            default -> "item.buildcraftlib.guide";
        };
        return Component.translatable(key);
    }

    // --- Book name helpers ---

    public static String getBookName(ItemStack stack) {
        String name = stack.get(BCLib.GUIDE_BOOK_NAME.get());
        return name != null ? name : DEFAULT_BOOK;
    }

    public static void setBookName(ItemStack stack, String bookName) {
        stack.set(BCLib.GUIDE_BOOK_NAME.get(), bookName);
    }

    /** Create a stack for a specific book. If bookName equals DEFAULT_BOOK, no component is set. */
    public static ItemStack createForBook(Item item, String bookName) {
        ItemStack stack = new ItemStack(item);
        if (!DEFAULT_BOOK.equals(bookName)) {
            setBookName(stack, bookName);
        }
        return stack;
    }
}
