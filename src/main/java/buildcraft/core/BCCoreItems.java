/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package buildcraft.core;

import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EquipmentSlot;
import buildcraft.core.item.ItemGoggles;
import buildcraft.core.item.ItemWrench_Neptune;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.core.item.ItemMarkerWrench;
import buildcraft.core.item.ItemVolumeWrench;
import buildcraft.core.item.ItemVolumeBox;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.core.item.ItemList_BC8;
import buildcraft.api.items.FluidItemDrops;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCItemHolder;

public class BCCoreItems {
        public static final BCRegistrar.Items ITEMS = BCRegistrar.createItems(BCCore.MODID);

        public static final BCItemHolder<ItemWrench_Neptune> WRENCH = ITEMS.registerItem("wrench",
                        ItemWrench_Neptune::new, props -> props.stacksTo(1));

        public static final BCItemHolder<ItemFragileFluidContainer> FRAGILE_FLUID_CONTAINER = ITEMS.registerItem(
                        "fragile_fluid_container",
                        ItemFragileFluidContainer::new, props -> props);

        public static final BCItemHolder<ItemMarkerWrench> MARKER_WRENCH = ITEMS.registerItem(
                        "marker_wrench",
                        ItemMarkerWrench::new, props -> props.stacksTo(1));

        public static final BCItemHolder<ItemVolumeWrench> VOLUME_WRENCH = ITEMS.registerItem(
                        "volume_wrench",
                        ItemVolumeWrench::new, props -> props.stacksTo(1));

        public static final BCItemHolder<ItemVolumeBox> VOLUME_BOX = ITEMS.registerItem(
                        "volume_box",
                        ItemVolumeBox::new, props -> props.stacksTo(16));

        public static final BCItemHolder<ItemPaintbrush_BC8> PAINTBRUSH = ITEMS.registerItem(
                        "paintbrush",
                        ItemPaintbrush_BC8::new, props -> props.stacksTo(1));

        public static final BCItemHolder<ItemList_BC8> LIST = ITEMS.registerItem(
                        "list",
                        ItemList_BC8::new, props -> props.stacksTo(1));

        // Records a world location/area/path/zone for the Zone Planner and (future) robotics
        // waypoints. Un-gated for survival now that the Zone Planner's interactive map viewport
        // makes ZONE maps authorable in-game; the SPOT/AREA/PATH branches drive its slot I/O.
        public static final BCItemHolder<ItemMapLocation> MAP_LOCATION = ITEMS.registerItem(
                        "map_location",
                        ItemMapLocation::new, props -> props.stacksTo(16));

        // Robot Goggles: a head-slot item with no armour value and no durability. >=1.21.10 gets the head slot from
        // the equippable component; 1.21.1 has none, so ItemGoggles implements Equipable there instead. The component
        // carries no equipment asset on purpose: without one the head layer draws the item through its own HEAD-view
        // item model, the 3D item/goggles_worn.
        //? if >=1.21.10 {
        public static final BCItemHolder<ItemGoggles> GOGGLES = ITEMS.registerItem(
                        "goggles",
                        ItemGoggles::new, props -> props.stacksTo(1).equippable(EquipmentSlot.HEAD));
        //?} else {
        /*public static final BCItemHolder<ItemGoggles> GOGGLES = ITEMS.registerItem(
                        "goggles",
                        ItemGoggles::new, props -> props.stacksTo(1));*/
        //?}

        // Gear items
        public static final BCItemHolder<Item> GEAR_WOOD = ITEMS.registerItem("gear_wood", Item::new, p -> p);
        public static final BCItemHolder<Item> GEAR_STONE = ITEMS.registerItem("gear_stone", Item::new, p -> p);
        public static final BCItemHolder<Item> GEAR_IRON = ITEMS.registerItem("gear_iron", Item::new, p -> p);
        public static final BCItemHolder<Item> GEAR_GOLD = ITEMS.registerItem("gear_gold", Item::new, p -> p);
        public static final BCItemHolder<Item> GEAR_DIAMOND = ITEMS.registerItem("gear_diamond", Item::new, p -> p);

        public static final BCItemHolder<net.minecraft.world.item.BlockItem> ENGINE_REDSTONE = ITEMS
                        .registerSimpleBlockItem(BCCoreBlocks.ENGINE_REDSTONE);

        public static final BCItemHolder<net.minecraft.world.item.BlockItem> ENGINE_CREATIVE = ITEMS
                        .registerSimpleBlockItem(BCCoreBlocks.ENGINE_CREATIVE);

        public static final BCItemHolder<net.minecraft.world.item.BlockItem> MARKER_VOLUME = ITEMS
                        .registerSimpleBlockItem(BCCoreBlocks.MARKER_VOLUME);

        public static final BCItemHolder<net.minecraft.world.item.BlockItem> MARKER_PATH = ITEMS
                        .registerSimpleBlockItem(BCCoreBlocks.MARKER_PATH);

        public static final BCItemHolder<net.minecraft.world.item.BlockItem> DECORATED_LASER = ITEMS
                        .registerSimpleBlockItem(BCCoreBlocks.DECORATED_LASER);

        public static final BCItemHolder<net.minecraft.world.item.BlockItem> SPRING_WATER = ITEMS
                        .registerSimpleBlockItem(BCCoreBlocks.SPRING_WATER);

        public static final BCItemHolder<net.minecraft.world.item.BlockItem> SPRING_OIL = ITEMS
                        .registerSimpleBlockItem(BCCoreBlocks.SPRING_OIL);

        // Debug block item — sinks MJ power; useful next to an engine in creative.
        public static final BCItemHolder<net.minecraft.world.item.BlockItem> POWER_TESTER = ITEMS
                        .registerSimpleBlockItem(BCCoreBlocks.POWER_TESTER);

        public static void init(IEventBus modEventBus) {
                ITEMS.subscribe();
        }

        public static void preInit() {
                FluidItemDrops.item = FRAGILE_FLUID_CONTAINER.get();
        }
}
