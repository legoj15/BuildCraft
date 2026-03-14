package buildcraft.silicon;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;

public class BCSiliconItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCSilicon.MODID);

    public static final DeferredItem<BlockItem> LASER =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.LASER);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
