package buildcraft.silicon;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.silicon.block.BlockLaser;
import buildcraft.silicon.block.BlockLaserTable;
import buildcraft.silicon.container.ContainerAssemblyTable;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.container.ContainerIntegrationTable;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileAdvancedCraftingTable;
import buildcraft.silicon.tile.TileIntegrationTable;

public class BCSiliconBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCSilicon.MODID);

    public static final DeferredBlock<BlockLaser> LASER = BLOCKS.registerBlock(
            "laser",
            BlockLaser::new,
            BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion());

    public static final DeferredBlock<BlockLaserTable> ASSEMBLY_TABLE = BLOCKS.register(
            "assembly_table",
            () -> new BlockLaserTable(
                BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion(),
                BCSiliconBlockEntities.ASSEMBLY_TABLE,
                (id, inv, tile) -> new ContainerAssemblyTable(id, inv.player, (TileAssemblyTable) tile)));

    public static final DeferredBlock<BlockLaserTable> ADVANCED_CRAFTING_TABLE = BLOCKS.register(
            "advanced_crafting_table",
            () -> new BlockLaserTable(
                BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion(),
                BCSiliconBlockEntities.ADVANCED_CRAFTING_TABLE,
                (id, inv, tile) -> new ContainerAdvancedCraftingTable(id, inv.player, (TileAdvancedCraftingTable) tile)));

    public static final DeferredBlock<BlockLaserTable> INTEGRATION_TABLE = BLOCKS.register(
            "integration_table",
            () -> new BlockLaserTable(
                BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion(),
                BCSiliconBlockEntities.INTEGRATION_TABLE,
                (id, inv, tile) -> new ContainerIntegrationTable(id, inv.player, (TileIntegrationTable) tile)));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
