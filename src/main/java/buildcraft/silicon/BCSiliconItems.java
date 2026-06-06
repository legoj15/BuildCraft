package buildcraft.silicon;

import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import buildcraft.lib.BCLib;
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

    // Dev-only — mirrors BCSiliconBlocks.INTEGRATION_TABLE. Null in public releases.
    public static final DeferredItem<BlockItem> INTEGRATION_TABLE;

    static {
        INTEGRATION_TABLE = (BCLib.DEV && BCSiliconBlocks.INTEGRATION_TABLE != null)
                ? ITEMS.registerSimpleBlockItem(BCSiliconBlocks.INTEGRATION_TABLE)
                : null;
    }

    // Chipsets — each variant is a separate item (replacing 1.12.2 metadata sub-items).
    // Naming: chipset_<material>, matching the pipe_<material>_<flow> role-prefix scheme.
    public static final DeferredItem<Item> CHIPSET_REDSTONE =
            ITEMS.registerSimpleItem("chipset_redstone");

    public static final DeferredItem<Item> CHIPSET_IRON =
            ITEMS.registerSimpleItem("chipset_iron");

    public static final DeferredItem<Item> CHIPSET_GOLD =
            ITEMS.registerSimpleItem("chipset_gold");

    public static final DeferredItem<Item> CHIPSET_QUARTZ =
            ITEMS.registerSimpleItem("chipset_quartz");

    public static final DeferredItem<Item> CHIPSET_DIAMOND =
            ITEMS.registerSimpleItem("chipset_diamond");

    // Gate Copier
    public static final DeferredItem<ItemGateCopier> GATE_COPIER =
            RegistrationUtilBC.registerItem(ITEMS,"gate_copier", ItemGateCopier::new);

    // Facade
    public static final DeferredItem<ItemPluggableFacade> PLUG_FACADE =
            RegistrationUtilBC.registerItem(ITEMS,"plug_facade", ItemPluggableFacade::new);

    // Gate
    public static final DeferredItem<ItemPluggableGate> PLUG_GATE =
            RegistrationUtilBC.registerItem(ITEMS,"plug_gate", ItemPluggableGate::new);

    // Pulsar
    public static final DeferredItem<ItemPluggablePulsar> PLUG_PULSAR =
            RegistrationUtilBC.registerItem(ITEMS,"plug_pulsar", ItemPluggablePulsar::new);

    // Lens
    public static final DeferredItem<ItemPluggableLens> PLUG_LENS =
            RegistrationUtilBC.registerItem(ITEMS,"plug_lens", ItemPluggableLens::new);

    // Light Sensor
    public static final DeferredItem<Item> PLUG_LIGHT_SENSOR =
            RegistrationUtilBC.registerItem(ITEMS,"plug_light_sensor", props -> new buildcraft.transport.item.ItemPluggableSimple(props, buildcraft.silicon.BCSiliconPlugs.lightSensor, null));

    // Timer
    public static final DeferredItem<Item> PLUG_TIMER =
            RegistrationUtilBC.registerItem(ITEMS,"plug_timer", props -> new buildcraft.transport.item.ItemPluggableSimple(props, buildcraft.silicon.BCSiliconPlugs.timer, null));


    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
