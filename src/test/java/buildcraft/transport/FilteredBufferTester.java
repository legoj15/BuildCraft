package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.tile.TileFilteredBuffer;

public class FilteredBufferTester {

    public static void testFilteredBufferDrops(GameTestHelper helper) {
        BlockPos bufferPos = new BlockPos(1, 2, 1);
        
        // Place Filtered Buffer
        helper.setBlock(bufferPos, BCTransportBlocks.FILTERED_BUFFER.get());
        
        TileFilteredBuffer buffer = helper.getBlockEntity(bufferPos, TileFilteredBuffer.class);
        
        // Add a mock item into the main inventory (Slot 0)
        buffer.invMain.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
        buffer.invMain.setStackInSlot(4, new ItemStack(Items.IRON_INGOT, 12));
        
        // Ensure state is registered properly before breaking
        helper.assertBlockPresent(BCTransportBlocks.FILTERED_BUFFER.get(), bufferPos);
        
        // Systematically break the block (this should trigger onRemove logic, causing the items to dump into the world)
        helper.destroyBlock(bufferPos);
        
        // Assert the block is fully destroyed
        helper.assertBlockPresent(Blocks.AIR, bufferPos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(Items.DIAMOND, bufferPos, 2.0);
            helper.assertItemEntityPresent(Items.IRON_INGOT, bufferPos, 2.0);
            helper.succeed();
        });
    }
}
