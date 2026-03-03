package buildcraft.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EquipmentSlot;
import buildcraft.core.item.ItemGoggles;
import buildcraft.core.item.ItemWrench_Neptune;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.core.item.ItemMarkerConnector;
import buildcraft.core.item.ItemVolumeBox;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.core.item.ItemPaintbrush_BC8;

public class BCCoreItems {
        public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCCore.MODID);

        public static final DeferredItem<ItemWrench_Neptune> WRENCH = ITEMS.registerItem("wrench",
                        ItemWrench_Neptune::new, props -> props.stacksTo(1));

        public static final DeferredItem<ItemGoggles> GOGGLES = ITEMS.registerItem("goggles",
                        ItemGoggles::new, props -> props.stacksTo(1).durability(0).equippable(EquipmentSlot.HEAD));

        public static final DeferredItem<ItemFragileFluidContainer> FRAGILE_FLUID_CONTAINER = ITEMS.registerItem(
                        "fragile_fluid_container",
                        ItemFragileFluidContainer::new, props -> props);

        public static final DeferredItem<ItemMarkerConnector> MARKER_CONNECTOR = ITEMS.registerItem(
                        "marker_connector",
                        ItemMarkerConnector::new, props -> props.stacksTo(1));

        public static final DeferredItem<ItemVolumeBox> VOLUME_BOX = ITEMS.registerItem(
                        "volume_box",
                        ItemVolumeBox::new, props -> props.stacksTo(16));

        public static final DeferredItem<ItemMapLocation> MAP_LOCATION = ITEMS.registerItem(
                        "map_location",
                        ItemMapLocation::new, props -> props.stacksTo(16));

        public static final DeferredItem<ItemPaintbrush_BC8> PAINTBRUSH = ITEMS.registerItem(
                        "paintbrush",
                        ItemPaintbrush_BC8::new, props -> props.stacksTo(1));

        public static final DeferredItem<net.minecraft.world.item.BlockItem> ENGINE_REDSTONE = ITEMS
                        .registerSimpleBlockItem("engine_redstone", BCCoreBlocks.ENGINE_REDSTONE);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> ENGINE_CREATIVE = ITEMS
                        .registerSimpleBlockItem("engine_creative", BCCoreBlocks.ENGINE_CREATIVE);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> SPRING = ITEMS
                        .registerSimpleBlockItem("spring", BCCoreBlocks.SPRING);

        public static void init(IEventBus modEventBus) {
                ITEMS.register(modEventBus);
        }

        public static void preInit() {
        }
}
