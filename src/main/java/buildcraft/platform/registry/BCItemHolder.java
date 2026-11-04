/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** A registered item: a {@link BCHolder} that is also an {@link ItemLike}, as NeoForge's {@code DeferredItem} is. */
public class BCItemHolder<I extends Item> extends BCHolder<Item, I> implements ItemLike {
    public BCItemHolder(BCHolder<Item, ? extends I> holder) {
        super(holder);
    }

    @Override
    public Item asItem() {
        return get();
    }

    /** A stack of {@code count} of this item. */
    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }

    /** A stack of one of this item. */
    public ItemStack toStack() {
        return toStack(1);
    }
}
