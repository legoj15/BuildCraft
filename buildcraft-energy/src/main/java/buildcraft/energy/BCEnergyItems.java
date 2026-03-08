package buildcraft.energy;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class BCEnergyItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("buildcraftcore");

    public static final DeferredItem<BlockItem> ENGINE_STONE = ITEMS.registerSimpleBlockItem(
            BCEnergyBlocks.ENGINE_STONE);

    public static final DeferredItem<BlockItem> ENGINE_IRON = ITEMS.registerSimpleBlockItem(
            BCEnergyBlocks.ENGINE_IRON);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
