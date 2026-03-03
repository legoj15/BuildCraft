package buildcraft.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.core.tile.TileMarkerPath;
import buildcraft.core.tile.TileMarkerVolume;

public class BCCoreBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCCore.MODID);

    public static final Supplier<BlockEntityType<TileMarkerVolume>> MARKER_VOLUME = BLOCK_ENTITIES.register(
            "marker_volume",
            () -> new BlockEntityType<>(TileMarkerVolume::new, BCCoreBlocks.MARKER_VOLUME.get()));

    public static final Supplier<BlockEntityType<TileMarkerPath>> MARKER_PATH = BLOCK_ENTITIES.register(
            "marker_path",
            () -> new BlockEntityType<>(TileMarkerPath::new, BCCoreBlocks.MARKER_PATH.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
