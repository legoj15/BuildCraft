package buildcraft.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.core.block.BlockMarkerPath;
import buildcraft.core.block.BlockSpring;
import buildcraft.core.block.BlockEngineRedstone_BC8;
import buildcraft.core.block.BlockEngineCreative;

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

    public static final DeferredBlock<buildcraft.core.block.BlockMarkerVolume> MARKER_VOLUME = BLOCKS.registerBlock(
            "marker_volume",
            buildcraft.core.block.BlockMarkerVolume::new, BlockBehaviour.Properties.of());

    public static final DeferredBlock<BlockMarkerPath> MARKER_PATH = BLOCKS.registerBlock(
            "marker_path",
            BlockMarkerPath::new, BlockBehaviour.Properties.of());

    public static final DeferredBlock<BlockEngineRedstone_BC8> ENGINE_REDSTONE = BLOCKS.registerBlock(
            "engine_redstone",
            BlockEngineRedstone_BC8::new, BlockBehaviour.Properties.of().strength(3.0f));

    public static final DeferredBlock<BlockEngineCreative> ENGINE_CREATIVE = BLOCKS.registerBlock(
            "engine_creative",
            BlockEngineCreative::new, BlockBehaviour.Properties.of().strength(3.0f));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
