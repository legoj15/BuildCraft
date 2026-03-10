package buildcraft.factory;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.factory.block.BlockAutoWorkbenchItems;

public class BCFactoryBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCFactory.MODID);

    public static final DeferredBlock<BlockAutoWorkbenchItems> AUTOWORKBENCH_ITEM = BLOCKS.registerBlock(
            "autoworkbench_item",
            BlockAutoWorkbenchItems::new,
            BlockBehaviour.Properties.of().strength(3.0f));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
