package buildcraft.silicon;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.item.ItemGateCopier;

public class BCSiliconItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCSilicon.MODID);

    // Block items
    public static final DeferredItem<BlockItem> LASER =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.LASER);

    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.ASSEMBLY_TABLE);

    public static final DeferredItem<BlockItem> ADVANCED_CRAFTING_TABLE =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.ADVANCED_CRAFTING_TABLE);

    public static final DeferredItem<BlockItem> INTEGRATION_TABLE =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.INTEGRATION_TABLE);

    // Chipsets — each variant is a separate item (replacing 1.12.2 metadata sub-items)
    public static final DeferredItem<Item> REDSTONE_RED_CHIPSET =
            ITEMS.registerItem("redstone_red_chipset", Item::new);

    public static final DeferredItem<Item> REDSTONE_IRON_CHIPSET =
            ITEMS.registerItem("redstone_iron_chipset", Item::new);

    public static final DeferredItem<Item> REDSTONE_GOLD_CHIPSET =
            ITEMS.registerItem("redstone_gold_chipset", Item::new);

    public static final DeferredItem<Item> REDSTONE_QUARTZ_CHIPSET =
            ITEMS.registerItem("redstone_quartz_chipset", Item::new);

    public static final DeferredItem<Item> REDSTONE_DIAMOND_CHIPSET =
            ITEMS.registerItem("redstone_diamond_chipset", Item::new);

    // Gate Copier
    public static final DeferredItem<ItemGateCopier> GATE_COPIER =
            ITEMS.registerItem("gate_copier", ItemGateCopier::new);

    // Facade
    public static final DeferredItem<ItemPluggableFacade> PLUG_FACADE =
            ITEMS.registerItem("plug_facade", ItemPluggableFacade::new);

    // Gate
    public static final DeferredItem<ItemPluggableGate> PLUG_GATE =
            ITEMS.registerItem("plug_gate", ItemPluggableGate::new);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
