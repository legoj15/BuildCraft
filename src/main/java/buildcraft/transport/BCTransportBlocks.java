package buildcraft.transport;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.transport.block.BlockFilteredBuffer;
import buildcraft.transport.block.BlockPipeHolder;

public class BCTransportBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCTransport.MODID);

    public static final DeferredBlock<BlockFilteredBuffer> FILTERED_BUFFER = BLOCKS.registerBlock(
            "filtered_buffer",
            BlockFilteredBuffer::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL));

    public static final DeferredBlock<BlockPipeHolder> PIPE_HOLDER = BLOCKS.registerBlock(
            "pipe_holder",
            BlockPipeHolder::new, () -> BlockBehaviour.Properties.of()
                .strength(0.25f, 3.0f)
                .noOcclusion()
                .dynamicShape()
                .sound(SoundType.METAL));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
