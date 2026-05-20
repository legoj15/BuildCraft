package buildcraft.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.lib.BCLib;
import buildcraft.core.tile.TileMarkerPath;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.core.tile.TileEngineRedstone_BC8;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.core.tile.TilePowerConsumerTester;

public class BCCoreBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCCore.MODID);

    public static final Supplier<BlockEntityType<TileMarkerVolume>> MARKER_VOLUME = BLOCK_ENTITIES.register(
            "marker_volume",
            () -> new BlockEntityType<>(TileMarkerVolume::new, BCCoreBlocks.MARKER_VOLUME.get()));

    public static final Supplier<BlockEntityType<TileMarkerPath>> MARKER_PATH = BLOCK_ENTITIES.register(
            "marker_path",
            () -> new BlockEntityType<>(TileMarkerPath::new, BCCoreBlocks.MARKER_PATH.get()));

    public static final Supplier<BlockEntityType<TileEngineRedstone_BC8>> ENGINE_REDSTONE = BLOCK_ENTITIES.register(
            "engine_redstone",
            () -> new BlockEntityType<>(TileEngineRedstone_BC8::new, BCCoreBlocks.ENGINE_REDSTONE.get()));

    public static final Supplier<BlockEntityType<TileEngineCreative>> ENGINE_CREATIVE = BLOCK_ENTITIES.register(
            "engine_creative",
            () -> new BlockEntityType<>(TileEngineCreative::new, BCCoreBlocks.ENGINE_CREATIVE.get()));

    // Dev-only — mirrors BCCoreBlocks.POWER_TESTER. Null when -Dbuildcraft.dev is unset.
    public static final Supplier<BlockEntityType<TilePowerConsumerTester>> POWER_TESTER;

    static {
        POWER_TESTER = (BCLib.DEV && BCCoreBlocks.POWER_TESTER != null)
                ? BLOCK_ENTITIES.register("power_tester",
                        () -> new BlockEntityType<>(TilePowerConsumerTester::new, BCCoreBlocks.POWER_TESTER.get()))
                : null;
    }

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
