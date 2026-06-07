package buildcraft.transport;

import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.transport.block.BlockFilteredBuffer;
import buildcraft.transport.block.BlockPipeHolder;

public class BCTransportBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCTransport.MODID);

    public static final DeferredBlock<BlockFilteredBuffer> FILTERED_BUFFER = RegistrationUtilBC.registerBlock(BLOCKS,
            "filtered_buffer",
            BlockFilteredBuffer::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    // Pipes are intentionally hand-breakable: bare-hand still drops the pipe + its cargo +
    // pluggables + wires. The mineable/pickaxe tag stays (so pickaxes still get the
    // break-speed bonus) but requiresCorrectToolForDrops is deliberately NOT set — that
    // would gate the loot table on tool, which is the wrong semantics for pipes.
    public static final DeferredBlock<BlockPipeHolder> PIPE_HOLDER = RegistrationUtilBC.registerBlock(BLOCKS,
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
