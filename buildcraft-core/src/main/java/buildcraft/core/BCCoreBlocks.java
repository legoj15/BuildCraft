package buildcraft.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.core.block.BlockSpring;

public class BCCoreBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCCore.MODID);

    public static final DeferredBlock<BlockSpring> SPRING = BLOCKS.registerBlock("spring",
            BlockSpring::new, BlockBehaviour.Properties.of());

    public static final DeferredBlock<buildcraft.core.block.BlockDecoration> DECORATION = BLOCKS.registerBlock(
            "decoration",
            buildcraft.core.block.BlockDecoration::new, BlockBehaviour.Properties.of()
                    .lightLevel(state -> state.hasProperty(buildcraft.core.block.BlockDecoration.DECORATED_TYPE)
                            ? state.getValue(buildcraft.core.block.BlockDecoration.DECORATED_TYPE).lightValue
                            : 0));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
