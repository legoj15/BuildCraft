/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.common.IShearable;

/**
 * A test-only {@link IShearable} block that is never ready for shears (a modded plant between harvests, say): the
 * interface says it can be sheared, and {@link #isShearable} always answers no. Every vanilla shearable block answers
 * yes, so this is the only way to a pair of shears meeting a shearable block that a stripes pipe's shears handler
 * then passes on, which is what lets the dispatch go on to the clicks after it. Registered by {@link TestOnlyBlocks}.
 */
public final class UnshearableBlock extends Block implements IShearable {

    public UnshearableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isShearable(Player player, ItemStack item, Level level, BlockPos pos) {
        return false;
    }
}
