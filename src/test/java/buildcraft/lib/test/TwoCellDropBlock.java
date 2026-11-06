/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

/**
 * A two-cell block (a double plant, placed by a {@code DoubleHighBlockItem}) whose halves both drop a stick when
 * they break. Every vanilla two-cell block drops from one half only, and from the half that a placement's put-back
 * never breaks: a door or tall plant drops from its lower half and the put-back clears the lower half first, so the
 * upper one breaks with nothing to drop. With a drop on both halves, the half the put-back breaks has something to
 * drop, which is what shows whether drops are suppressed while a refused placement is put back. Registered by
 * {@link TestOnlyBlocks}.
 */
public final class TwoCellDropBlock extends DoublePlantBlock {

    public TwoCellDropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(Items.STICK));
    }
}
