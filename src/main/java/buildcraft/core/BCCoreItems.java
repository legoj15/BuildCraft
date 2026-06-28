package buildcraft.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;
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

        public static final DeferredItem<ItemWrench_Neptune> WRENCH = RegistrationUtilBC.registerItem(ITEMS,"wrench",
                        ItemWrench_Neptune::new, props -> props.stacksTo(1));

        public static final DeferredItem<ItemFragileFluidContainer> FRAGILE_FLUID_CONTAINER = RegistrationUtilBC.registerItem(ITEMS,
                        "fragile_fluid_container",
                        ItemFragileFluidContainer::new, props -> props);

        public static final DeferredItem<ItemMarkerConnector> MARKER_CONNECTOR = RegistrationUtilBC.registerItem(ITEMS,
                        "marker_connector",
                        ItemMarkerConnector::new, props -> props.stacksTo(1));

        public static final DeferredItem<ItemVolumeBox> VOLUME_BOX = RegistrationUtilBC.registerItem(ITEMS,
                        "volume_box",
                        ItemVolumeBox::new, props -> props.stacksTo(16));

        public static final DeferredItem<ItemPaintbrush_BC8> PAINTBRUSH = RegistrationUtilBC.registerItem(ITEMS,
                        "paintbrush",
                        ItemPaintbrush_BC8::new, props -> props.stacksTo(1));

        public static final DeferredItem<ItemList_BC8> LIST = RegistrationUtilBC.registerItem(ITEMS,
                        "list",
                        ItemList_BC8::new, props -> props.stacksTo(1));

        // Records a world location/area/path/zone for the Zone Planner and (future) robotics
        // waypoints. Un-gated for survival now that the Zone Planner's interactive map viewport
        // makes ZONE maps authorable in-game; the SPOT/AREA/PATH branches drive its slot I/O.
        public static final DeferredItem<ItemMapLocation> MAP_LOCATION = RegistrationUtilBC.registerItem(ITEMS,
                        "map_location",
                        ItemMapLocation::new, props -> props.stacksTo(16));

        // Gear items
        public static final DeferredItem<Item> GEAR_WOOD = RegistrationUtilBC.registerItem(ITEMS,"gear_wood", Item::new, p -> p);
        public static final DeferredItem<Item> GEAR_STONE = RegistrationUtilBC.registerItem(ITEMS,"gear_stone", Item::new, p -> p);
        public static final DeferredItem<Item> GEAR_IRON = RegistrationUtilBC.registerItem(ITEMS,"gear_iron", Item::new, p -> p);
        public static final DeferredItem<Item> GEAR_GOLD = RegistrationUtilBC.registerItem(ITEMS,"gear_gold", Item::new, p -> p);
        public static final DeferredItem<Item> GEAR_DIAMOND = RegistrationUtilBC.registerItem(ITEMS,"gear_diamond", Item::new, p -> p);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> ENGINE_REDSTONE = ITEMS
                        .registerSimpleBlockItem("engine_redstone", BCCoreBlocks.ENGINE_REDSTONE);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> ENGINE_CREATIVE = ITEMS
                        .registerSimpleBlockItem("engine_creative", BCCoreBlocks.ENGINE_CREATIVE);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> MARKER_VOLUME = ITEMS
                        .registerSimpleBlockItem("marker_volume", BCCoreBlocks.MARKER_VOLUME);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> MARKER_PATH = ITEMS
                        .registerSimpleBlockItem("marker_path", BCCoreBlocks.MARKER_PATH);

        // Decorated block items — only LASER ships in public builds; the other five are
        // dev-gated mirrors of BCCoreBlocks.DECORATED_* and are wired up in the static {}
        // initializer below.
        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_LASER = ITEMS
                        .registerSimpleBlockItem("decorated_laser", BCCoreBlocks.DECORATED_LASER);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_DESTROY;
        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_BLUEPRINT;
        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_TEMPLATE;
        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_PAPER;
        public static final DeferredItem<net.minecraft.world.item.BlockItem> DECORATED_LEATHER;

        public static final DeferredItem<net.minecraft.world.item.BlockItem> SPRING_WATER = ITEMS
                        .registerSimpleBlockItem("spring_water", BCCoreBlocks.SPRING_WATER);

        public static final DeferredItem<net.minecraft.world.item.BlockItem> SPRING_OIL = ITEMS
                        .registerSimpleBlockItem("spring_oil", BCCoreBlocks.SPRING_OIL);

        // ─── Dev-only items — gated behind -Dbuildcraft.dev=true ─────────────
        // Registered only in a dev workspace so unfinished/experimental content isn't lost,
        // but kept out of public releases. BCCoreClient tags them with a red "Dev only"
        // tooltip via BCTooltips.markDevOnly.

        // The 1.12.2 goggles headpiece was never finished.
        public static final DeferredItem<ItemGoggles> GOGGLES;

        // Debug block that sinks MJ power — mirrors BCCoreBlocks.POWER_TESTER.
        public static final DeferredItem<net.minecraft.world.item.BlockItem> POWER_TESTER;

        static {
                //? if >=1.21.10 {
                GOGGLES = BCLib.DEV
                                ? RegistrationUtilBC.registerItem(ITEMS,"goggles", ItemGoggles::new,
                                                props -> props.stacksTo(1).durability(0).equippable(EquipmentSlot.HEAD))
                                : null;
                //?} else {
                /*GOGGLES = BCLib.DEV
                                ? RegistrationUtilBC.registerItem(ITEMS,"goggles", ItemGoggles::new,
                                                props -> props.stacksTo(1).durability(0))
                                : null;*/
                //?}
                POWER_TESTER = (BCLib.DEV && BCCoreBlocks.POWER_TESTER != null)
                                ? ITEMS.registerSimpleBlockItem("power_tester", BCCoreBlocks.POWER_TESTER)
                                : null;
                DECORATED_DESTROY = (BCLib.DEV && BCCoreBlocks.DECORATED_DESTROY != null)
                                ? ITEMS.registerSimpleBlockItem("decorated_destroy", BCCoreBlocks.DECORATED_DESTROY)
                                : null;
                DECORATED_BLUEPRINT = (BCLib.DEV && BCCoreBlocks.DECORATED_BLUEPRINT != null)
                                ? ITEMS.registerSimpleBlockItem("decorated_blueprint", BCCoreBlocks.DECORATED_BLUEPRINT)
                                : null;
                DECORATED_TEMPLATE = (BCLib.DEV && BCCoreBlocks.DECORATED_TEMPLATE != null)
                                ? ITEMS.registerSimpleBlockItem("decorated_template", BCCoreBlocks.DECORATED_TEMPLATE)
                                : null;
                DECORATED_PAPER = (BCLib.DEV && BCCoreBlocks.DECORATED_PAPER != null)
                                ? ITEMS.registerSimpleBlockItem("decorated_paper", BCCoreBlocks.DECORATED_PAPER)
                                : null;
                DECORATED_LEATHER = (BCLib.DEV && BCCoreBlocks.DECORATED_LEATHER != null)
                                ? ITEMS.registerSimpleBlockItem("decorated_leather", BCCoreBlocks.DECORATED_LEATHER)
                                : null;
        }

        public static void init(IEventBus modEventBus) {
                ITEMS.register(modEventBus);
        }

        public static void preInit() {
                FluidItemDrops.item = FRAGILE_FLUID_CONTAINER.get();
        }
}
