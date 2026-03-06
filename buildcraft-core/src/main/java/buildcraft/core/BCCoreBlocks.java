package buildcraft.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.api.enums.EnumSpring;
import buildcraft.core.block.BlockMarkerPath;
import buildcraft.core.block.BlockSpring;
import buildcraft.core.block.BlockEngineRedstone_BC8;
import buildcraft.core.block.BlockEngineCreative;

public class BCCoreBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCCore.MODID);

    public static final DeferredBlock<BlockSpring> SPRING_WATER = BLOCKS.registerBlock("spring_water",
            props -> new BlockSpring(EnumSpring.WATER, props), BlockBehaviour.Properties.of());

    public static final DeferredBlock<BlockSpring> SPRING_OIL = BLOCKS.registerBlock("spring_oil",
            props -> new BlockSpring(EnumSpring.OIL, props), BlockBehaviour.Properties.of());

    // Decorated blocks — mirrors 1.12.2's buildcraftcore:decorated meta variants
    public static final DeferredBlock<Block> DECORATED_DESTROY = BLOCKS.registerSimpleBlock(
            "decorated_destroy", BlockBehaviour.Properties.of().strength(3.0f));

    public static final DeferredBlock<Block> DECORATED_BLUEPRINT = BLOCKS.registerSimpleBlock(
            "decorated_blueprint", BlockBehaviour.Properties.of().strength(3.0f).lightLevel(s -> 10));

    public static final DeferredBlock<Block> DECORATED_TEMPLATE = BLOCKS.registerSimpleBlock(
            "decorated_template", BlockBehaviour.Properties.of().strength(3.0f).lightLevel(s -> 10));

    public static final DeferredBlock<Block> DECORATED_PAPER = BLOCKS.registerSimpleBlock(
            "decorated_paper", BlockBehaviour.Properties.of().strength(3.0f).lightLevel(s -> 10));

    public static final DeferredBlock<Block> DECORATED_LEATHER = BLOCKS.registerSimpleBlock(
            "decorated_leather", BlockBehaviour.Properties.of().strength(3.0f).lightLevel(s -> 10));

    public static final DeferredBlock<Block> DECORATED_LASER = BLOCKS.registerSimpleBlock(
            "decorated_laser", BlockBehaviour.Properties.of().strength(3.0f));

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
