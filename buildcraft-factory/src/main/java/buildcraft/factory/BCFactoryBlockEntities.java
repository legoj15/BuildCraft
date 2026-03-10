package buildcraft.factory;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.factory.tile.TileAutoWorkbenchItems;

public class BCFactoryBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCFactory.MODID);

    public static final Supplier<BlockEntityType<TileAutoWorkbenchItems>> AUTO_WORKBENCH_ITEMS =
            BLOCK_ENTITIES.register("autoworkbench_item",
                    () -> new BlockEntityType<>(TileAutoWorkbenchItems::new,
                            BCFactoryBlocks.AUTOWORKBENCH_ITEM.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
