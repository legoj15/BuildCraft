/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;

import buildcraft.transport.tile.TilePipeHolder;

/**
 * Every pipe shares one block, so a placed pipe must sound like its own material (its definition's sound), not like
 * the shared block's default. Breaking, stepping, placing and hitting all read this position-aware lookup.
 */
public class PipeSoundTester {

    private static final BlockPos WOOD = new BlockPos(2, 2, 2);
    private static final BlockPos STONE = new BlockPos(3, 2, 2);

    public static void testPlacedPipeSoundsLikeItsMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SoundType wood = placedSound(helper, level, WOOD, BCTransportItems.PIPE_WOOD_ITEM.get());
        SoundType stone = placedSound(helper, level, STONE, BCTransportItems.PIPE_STONE_ITEM.get());

        helper.assertTrue(wood == BCTransportPipes.woodItem.soundType,
            "a wooden pipe must use the wooden pipe sound, got " + wood);
        helper.assertTrue(stone == BCTransportPipes.stoneItem.soundType,
            "a stone pipe must use the stone pipe sound, got " + stone);
        helper.assertTrue(wood != null && stone != null && wood != stone,
            "two pipe materials must not share one sound");
        helper.succeed();
    }

    private static SoundType placedSound(GameTestHelper helper, ServerLevel level, BlockPos rel, Item pipe) {
        helper.setBlock(rel, BCTransportBlocks.PIPE_HOLDER.get());
        BlockPos abs = helper.absolutePos(rel);
        TilePipeHolder tile = (TilePipeHolder) level.getBlockEntity(abs);
        helper.assertTrue(tile != null, "pipe holder must have a block entity");
        tile.onPlacedBy(null, new ItemStack(pipe));
        return level.getBlockState(abs).getSoundType(level, abs, null);
    }
}
