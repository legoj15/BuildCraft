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
import buildcraft.core.item.ItemList_BC8;
import buildcraft.api.items.FluidItemDrops;
import buildcraft.lib.BCLib;

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

        public static final DeferredItem<ItemList_BC8> LIST = ITEMS.registerItem(
                        "list",
                        ItemList_BC8::new, props -> props.stacksTo(1));

        // Gear items
        public static final DeferredItem<Item> GEAR_WOOD = ITEMS.registerItem("gear_wood", Item::new, p -> p);
        public static final DeferredItem<Item> GEAR_STONE = ITEMS.registerItem("gear_stone", Item::new, p -> p);
        public static final DeferredItem<Item> GEAR_IRON = ITEMS.registerItem("gear_iron", Item::new, p -> p);
        public static final DeferredItem<Item> GEAR_GOLD = ITEMS.registerItem("gear_gold", Item::new, p -> p);
        public static final DeferredItem<Item> GEAR_DIAMOND = ITEMS.registerItem("gear_diamond", Item::new, p -> p);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> ENGINE_REDSTONE = ITEMS
                        .registerSimpleBlockItem("engine_redstone", BCCoreBlocks.ENGINE_REDSTONE);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> ENGINE_CREATIVE = ITEMS
                        .registerSimpleBlockItem("engine_creative", BCCoreBlocks.ENGINE_CREATIVE);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> MARKER_VOLUME = ITEMS
                        .registerSimpleBlockItem("marker_volume", BCCoreBlocks.MARKER_VOLUME);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> MARKER_PATH = ITEMS
                        .registerSimpleBlockItem("marker_path", BCCoreBlocks.MARKER_PATH);

        // Decorated block items
        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_DESTROY = ITEMS
                        .registerSimpleBlockItem("decorated_destroy", BCCoreBlocks.DECORATED_DESTROY);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_BLUEPRINT = ITEMS
                        .registerSimpleBlockItem("decorated_blueprint", BCCoreBlocks.DECORATED_BLUEPRINT);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_TEMPLATE = ITEMS
                        .registerSimpleBlockItem("decorated_template", BCCoreBlocks.DECORATED_TEMPLATE);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_PAPER = ITEMS
                        .registerSimpleBlockItem("decorated_paper", BCCoreBlocks.DECORATED_PAPER);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_LEATHER = ITEMS
                        .registerSimpleBlockItem("decorated_leather", BCCoreBlocks.DECORATED_LEATHER);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_LASER = ITEMS
                        .registerSimpleBlockItem("decorated_laser", BCCoreBlocks.DECORATED_LASER);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> SPRING_WATER = ITEMS
                        .registerSimpleBlockItem("spring_water", BCCoreBlocks.SPRING_WATER);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> SPRING_OIL = ITEMS
                        .registerSimpleBlockItem("spring_oil", BCCoreBlocks.SPRING_OIL);

        // Dev-only — mirrors BCCoreBlocks.POWER_TESTER. Null when -Dbuildcraft.dev is unset.
        public static final DeferredItem<net.minecraft.world.item.BlockItem> POWER_TESTER;

        static {
                POWER_TESTER = (BCLib.DEV && BCCoreBlocks.POWER_TESTER != null)
                                ? ITEMS.registerSimpleBlockItem("power_tester", BCCoreBlocks.POWER_TESTER)
                                : null;
        }

        public static void init(IEventBus modEventBus) {
                ITEMS.register(modEventBus);
        }

        public static void preInit() {
                FluidItemDrops.item = FRAGILE_FLUID_CONTAINER.get();
        }
}
