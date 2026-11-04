/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** A registered block: a {@link BCHolder} that is also an {@link ItemLike} (the block's item), as NeoForge's
 *  {@code DeferredBlock} is. */
public class BCBlockHolder<B extends Block> extends BCHolder<Block, B> implements ItemLike {
    public BCBlockHolder(BCHolder<Block, ? extends B> holder) {
        super(holder);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}
