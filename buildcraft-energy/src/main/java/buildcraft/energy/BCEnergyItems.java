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

    // Glob of Oil — registered under a separate registry because its assets are in buildcraftenergy
    private static final DeferredRegister.Items ENERGY_ITEMS = DeferredRegister.createItems("buildcraftenergy");

    public static final DeferredItem<Item> GLOB_OF_OIL =
            ENERGY_ITEMS.registerItem("glob_of_oil", Item::new);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENERGY_ITEMS.register(modEventBus);
    }
}
