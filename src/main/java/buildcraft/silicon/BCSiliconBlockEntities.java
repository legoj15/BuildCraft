package buildcraft.silicon;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.silicon.tile.TileAdvancedCraftingTable;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileLaser;

public class BCSiliconBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCSilicon.MODID);

    public static final Supplier<BlockEntityType<TileLaser>> LASER =
            BLOCK_ENTITIES.register("laser",
                    () -> new BlockEntityType<>(TileLaser::new,
                            BCSiliconBlocks.LASER.get()));

    public static final Supplier<BlockEntityType<TileAssemblyTable>> ASSEMBLY_TABLE =
            BLOCK_ENTITIES.register("assembly_table",
                    () -> new BlockEntityType<>(TileAssemblyTable::new,
                            BCSiliconBlocks.ASSEMBLY_TABLE.get()));

    public static final Supplier<BlockEntityType<TileAdvancedCraftingTable>> ADVANCED_CRAFTING_TABLE =
            BLOCK_ENTITIES.register("advanced_crafting_table",
                    () -> new BlockEntityType<>(TileAdvancedCraftingTable::new,
                            BCSiliconBlocks.ADVANCED_CRAFTING_TABLE.get()));

    public static final Supplier<BlockEntityType<TileIntegrationTable>> INTEGRATION_TABLE =
            BLOCK_ENTITIES.register("integration_table",
                    () -> new BlockEntityType<>(TileIntegrationTable::new,
                            BCSiliconBlocks.INTEGRATION_TABLE.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
