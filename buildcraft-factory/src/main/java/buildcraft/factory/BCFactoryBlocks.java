package buildcraft.factory;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.factory.block.BlockAutoWorkbenchItems;
import buildcraft.factory.block.BlockMiningWell;
import buildcraft.factory.block.BlockPump;
import buildcraft.factory.block.BlockTube;

public class BCFactoryBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCFactory.MODID);

    public static final DeferredBlock<BlockAutoWorkbenchItems> AUTOWORKBENCH_ITEM = BLOCKS.registerBlock(
            "autoworkbench_item",
            BlockAutoWorkbenchItems::new,
            BlockBehaviour.Properties.of().strength(3.0f));

    public static final DeferredBlock<BlockMiningWell> MINING_WELL = BLOCKS.registerBlock(
            "mining_well",
            BlockMiningWell::new,
            BlockBehaviour.Properties.of().strength(5.0f, 10.0f));

    public static final DeferredBlock<BlockPump> PUMP = BLOCKS.registerBlock(
            "pump",
            BlockPump::new,
            BlockBehaviour.Properties.of().strength(5.0f, 10.0f));

    public static final DeferredBlock<BlockTube> TUBE = BLOCKS.registerBlock(
            "tube",
            BlockTube::new,
            BlockBehaviour.Properties.of().destroyTime(-1.0f).noOcclusion());

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
