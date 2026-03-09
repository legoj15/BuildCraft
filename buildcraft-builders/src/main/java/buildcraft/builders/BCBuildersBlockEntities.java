package buildcraft.builders;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileFiller;

public class BCBuildersBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCBuilders.MODID);

    public static final Supplier<BlockEntityType<TileFiller>> FILLER = BLOCK_ENTITIES.register(
            "filler",
            () -> new BlockEntityType<>(TileFiller::new, BCBuildersBlocks.FILLER.get()));

    public static final Supplier<BlockEntityType<TileBuilder>> BUILDER = BLOCK_ENTITIES.register(
            "builder",
            () -> new BlockEntityType<>(TileBuilder::new, BCBuildersBlocks.BUILDER.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
