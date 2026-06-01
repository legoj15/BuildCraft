package buildcraft.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.api.enums.EnumSpring;
import buildcraft.lib.BCLib;
import buildcraft.core.block.BlockMarkerPath;
import buildcraft.core.block.BlockPowerConsumerTester;
import buildcraft.core.block.BlockSpring;
import buildcraft.core.block.BlockEngineRedstone_BC8;
import buildcraft.core.block.BlockEngineCreative;

public class BCCoreBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCCore.MODID);

    public static final DeferredBlock<BlockSpring> SPRING_WATER = BLOCKS.registerBlock("spring_water",
            props -> new BlockSpring(EnumSpring.WATER, props), () -> BlockBehaviour.Properties.of().sound(SoundType.STONE));

    public static final DeferredBlock<BlockSpring> SPRING_OIL = BLOCKS.registerBlock("spring_oil",
            props -> new BlockSpring(EnumSpring.OIL, props), () -> BlockBehaviour.Properties.of().sound(SoundType.STONE));

    // Decorated blocks — mirrors 1.12.2's buildcraftcore:decorated meta variants.
    // 1.12.2 Material.IRON → pickaxe required for drops (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    //
    // Only the LASER variant ever had a survival recipe in 1.12.2 (8 obsidian + redstone
    // block → 16). The other five were creative-tab only; they remain dev-gated until a
    // distinct, intentional recipe/use is designed for each — and so carry .noLootTable()
    // (no shipped loot JSON) since a survival drop only makes sense once a survival path
    // exists. A production build (no -Dbuildcraft.dev=true) never registers them, so shipping
    // their loot tables would just orphan the item references and spam the server log; when a
    // variant is promoted, give it a loot table + recipe and drop the .noLootTable() like LASER.
    public static final DeferredBlock<Block> DECORATED_LASER = BLOCKS.registerSimpleBlock(
            "decorated_laser", () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DECORATED_DESTROY;
    public static final DeferredBlock<Block> DECORATED_BLUEPRINT;
    public static final DeferredBlock<Block> DECORATED_TEMPLATE;
    public static final DeferredBlock<Block> DECORATED_PAPER;
    public static final DeferredBlock<Block> DECORATED_LEATHER;

    // Markers were Material.CIRCUITS in 1.12.2 → hand-breakable (no tool gate).
    public static final DeferredBlock<buildcraft.core.block.BlockMarkerVolume> MARKER_VOLUME = BLOCKS.registerBlock(
            "marker_volume",
            buildcraft.core.block.BlockMarkerVolume::new, () -> BlockBehaviour.Properties.of().sound(SoundType.METAL));

    public static final DeferredBlock<BlockMarkerPath> MARKER_PATH = BLOCKS.registerBlock(
            "marker_path",
            BlockMarkerPath::new, () -> BlockBehaviour.Properties.of().sound(SoundType.METAL));

    public static final DeferredBlock<BlockEngineRedstone_BC8> ENGINE_REDSTONE = BLOCKS.registerBlock(
            "engine_redstone",
            BlockEngineRedstone_BC8::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockEngineCreative> ENGINE_CREATIVE = BLOCKS.registerBlock(
            "engine_creative",
            BlockEngineCreative::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    // Dev-only debug block — registered only when launched with -Dbuildcraft.dev=true.
    // 1.12.2 Material.IRON → pickaxe-required, metal sounds.
    public static final DeferredBlock<BlockPowerConsumerTester> POWER_TESTER;

    static {
        POWER_TESTER = BCLib.DEV
                ? BLOCKS.registerBlock("power_tester",
                        BlockPowerConsumerTester::new,
                        () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops())
                : null;

        DECORATED_DESTROY = BCLib.DEV
                ? BLOCKS.registerSimpleBlock("decorated_destroy",
                        () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noLootTable())
                : null;
        DECORATED_BLUEPRINT = BCLib.DEV
                ? BLOCKS.registerSimpleBlock("decorated_blueprint",
                        () -> BlockBehaviour.Properties.of().strength(3.0f).lightLevel(s -> 10).sound(SoundType.METAL).requiresCorrectToolForDrops().noLootTable())
                : null;
        DECORATED_TEMPLATE = BCLib.DEV
                ? BLOCKS.registerSimpleBlock("decorated_template",
                        () -> BlockBehaviour.Properties.of().strength(3.0f).lightLevel(s -> 10).sound(SoundType.METAL).requiresCorrectToolForDrops().noLootTable())
                : null;
        DECORATED_PAPER = BCLib.DEV
                ? BLOCKS.registerSimpleBlock("decorated_paper",
                        () -> BlockBehaviour.Properties.of().strength(3.0f).lightLevel(s -> 10).sound(SoundType.METAL).requiresCorrectToolForDrops().noLootTable())
                : null;
        DECORATED_LEATHER = BCLib.DEV
                ? BLOCKS.registerSimpleBlock("decorated_leather",
                        () -> BlockBehaviour.Properties.of().strength(3.0f).lightLevel(s -> 10).sound(SoundType.METAL).requiresCorrectToolForDrops().noLootTable())
                : null;
    }

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
