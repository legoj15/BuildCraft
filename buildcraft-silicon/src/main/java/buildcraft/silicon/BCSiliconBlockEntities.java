package buildcraft.silicon;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.silicon.tile.TileLaser;

public class BCSiliconBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCSilicon.MODID);

    public static final Supplier<BlockEntityType<TileLaser>> LASER =
            BLOCK_ENTITIES.register("laser",
                    () -> new BlockEntityType<>(TileLaser::new,
                            BCSiliconBlocks.LASER.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
