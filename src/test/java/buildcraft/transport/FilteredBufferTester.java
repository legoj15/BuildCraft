/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.lib.test.DropArenaUtil;
import buildcraft.lib.test.DropArenaUtil.Drops;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.tile.TileFilteredBuffer;

public class FilteredBufferTester {

    public static void testFilteredBufferDrops(GameTestHelper helper) {
        BlockPos bufferPos = new BlockPos(1, 2, 1);

        DropArenaUtil.awaitDrops(helper, () -> {
            // Place Filtered Buffer
            helper.setBlock(bufferPos, BCTransportBlocks.FILTERED_BUFFER.get());

            //? if >=1.21.10 {
            TileFilteredBuffer buffer = helper.getBlockEntity(bufferPos, TileFilteredBuffer.class);
            //?} else {
            /*TileFilteredBuffer buffer = helper.getBlockEntity(bufferPos);*/
            //?}

            // Add a mock item into the main inventory (Slot 0)
            buffer.invMain.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
            buffer.invMain.setStackInSlot(4, new ItemStack(Items.IRON_INGOT, 12));

            // Ensure state is registered properly before breaking
            helper.assertBlockPresent(BCTransportBlocks.FILTERED_BUFFER.get(), bufferPos);

            // Simulate a survival-mode player breaking the block with a pickaxe (and check the block is gone).
            // `helper.destroyBlock` defaults to `dropBlock=false` which skips the loot-table path entirely, and the
            // Filtered Buffer has `requiresCorrectToolForDrops()` so we need a real tool in hand for
            // drops to fire. Running the full playerWillDestroy → removeBlock → playerDestroy flow
            // is the only setup that actually exercises what real gameplay does.
            DropArenaUtil.breakBlock(helper, bufferPos, new ItemStack(Items.WOODEN_PICKAXE));
        }, Drops.near(bufferPos, 2.0).present(Items.DIAMOND, Items.IRON_INGOT));
    }
}
