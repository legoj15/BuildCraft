package buildcraft.silicon;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import buildcraft.silicon.item.ItemGateCopier;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.item.ItemPluggableLens;
import buildcraft.silicon.item.ItemPluggablePulsar;

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
            ITEMS.registerSimpleItem("redstone_red_chipset");

    public static final DeferredItem<Item> REDSTONE_IRON_CHIPSET =
            ITEMS.registerSimpleItem("redstone_iron_chipset");

    public static final DeferredItem<Item> REDSTONE_GOLD_CHIPSET =
            ITEMS.registerSimpleItem("redstone_gold_chipset");

    public static final DeferredItem<Item> REDSTONE_QUARTZ_CHIPSET =
            ITEMS.registerSimpleItem("redstone_quartz_chipset");

    public static final DeferredItem<Item> REDSTONE_DIAMOND_CHIPSET =
            ITEMS.registerSimpleItem("redstone_diamond_chipset");

    // Gate Copier
    public static final DeferredItem<ItemGateCopier> GATE_COPIER =
            ITEMS.registerItem("gate_copier", ItemGateCopier::new);

    // Facade
    public static final DeferredItem<ItemPluggableFacade> PLUG_FACADE =
            ITEMS.registerItem("plug_facade", ItemPluggableFacade::new);

    // Gate
    public static final DeferredItem<ItemPluggableGate> PLUG_GATE =
            ITEMS.registerItem("plug_gate", ItemPluggableGate::new);

    // Pulsar
    public static final DeferredItem<ItemPluggablePulsar> PLUG_PULSAR =
            ITEMS.registerItem("plug_pulsar", ItemPluggablePulsar::new);

    // Lens
    public static final DeferredItem<ItemPluggableLens> PLUG_LENS =
            ITEMS.registerItem("plug_lens", ItemPluggableLens::new);

    // Light Sensor
    public static final DeferredItem<Item> PLUG_LIGHT_SENSOR =
            ITEMS.registerSimpleItem("plug_light_sensor");

    // Timer
    public static final DeferredItem<Item> PLUG_TIMER =
            ITEMS.registerSimpleItem("plug_timer");

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
