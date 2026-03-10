package buildcraft.factory;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;

public class BCFactoryItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCFactory.MODID);

    public static final DeferredItem<BlockItem> AUTOWORKBENCH_ITEM = ITEMS.registerSimpleBlockItem(
            BCFactoryBlocks.AUTOWORKBENCH_ITEM);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
