package buildcraft.builders;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.builders.tile.TileReplacer;

public class BCBuildersBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCBuilders.MODID);

    public static final Supplier<BlockEntityType<TileFiller>> FILLER = BLOCK_ENTITIES.register(
            "filler",
            () -> new BlockEntityType<>(TileFiller::new, BCBuildersBlocks.FILLER.get()));

    public static final Supplier<BlockEntityType<TileBuilder>> BUILDER = BLOCK_ENTITIES.register(
            "builder",
            () -> new BlockEntityType<>(TileBuilder::new, BCBuildersBlocks.BUILDER.get()));

    public static final Supplier<BlockEntityType<TileArchitectTable>> ARCHITECT = BLOCK_ENTITIES.register(
            "architect",
            () -> new BlockEntityType<>(TileArchitectTable::new, BCBuildersBlocks.ARCHITECT.get()));

    public static final Supplier<BlockEntityType<TileElectronicLibrary>> LIBRARY = BLOCK_ENTITIES.register(
            "library",
            () -> new BlockEntityType<>(TileElectronicLibrary::new, BCBuildersBlocks.LIBRARY.get()));

    public static final Supplier<BlockEntityType<TileReplacer>> REPLACER = BLOCK_ENTITIES.register(
            "replacer",
            () -> new BlockEntityType<>(TileReplacer::new, BCBuildersBlocks.REPLACER.get()));

    public static final Supplier<BlockEntityType<TileQuarry>> QUARRY = BLOCK_ENTITIES.register(
            "quarry",
            () -> new BlockEntityType<>(TileQuarry::new, BCBuildersBlocks.QUARRY.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
