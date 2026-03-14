package buildcraft.transport;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;

public class BCTransportItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCTransport.MODID);

    public static final DeferredItem<BlockItem> FILTERED_BUFFER = ITEMS.registerSimpleBlockItem(
            BCTransportBlocks.FILTERED_BUFFER);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
