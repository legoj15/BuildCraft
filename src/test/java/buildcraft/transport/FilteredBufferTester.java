package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.tile.TileFilteredBuffer;

public class FilteredBufferTester {

    public static void testFilteredBufferDrops(GameTestHelper helper) {
        BlockPos bufferPos = new BlockPos(1, 2, 1);

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

        // Simulate a survival-mode player breaking the block with a pickaxe. `helper.destroyBlock`
        // defaults to `dropBlock=false` which skips the loot-table path entirely, and the
        // Filtered Buffer has `requiresCorrectToolForDrops()` so we need a real tool in hand for
        // drops to fire. Running the full playerWillDestroy → removeBlock → playerDestroy flow
        // is the only setup that actually exercises what real gameplay does.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(bufferPos);
        BlockState state = level.getBlockState(absPos);
        BlockEntity be = level.getBlockEntity(absPos);
        state.getBlock().playerWillDestroy(level, absPos, state, player);
        level.removeBlock(absPos, false);
        state.getBlock().playerDestroy(level, player, absPos, state, be, player.getMainHandItem());

        // Assert the block is fully destroyed
        helper.assertBlockPresent(Blocks.AIR, bufferPos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(Items.DIAMOND, bufferPos, 2.0);
            helper.assertItemEntityPresent(Items.IRON_INGOT, bufferPos, 2.0);
            helper.succeed();
        });
    }
}
