package buildcraft.transport;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.transport.block.BlockFilteredBuffer;

public class BCTransportBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCTransport.MODID);

    public static final DeferredBlock<BlockFilteredBuffer> FILTERED_BUFFER = BLOCKS.registerBlock(
            "filtered_buffer",
            BlockFilteredBuffer::new,
            BlockBehaviour.Properties.of().strength(5.0f, 10.0f));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
