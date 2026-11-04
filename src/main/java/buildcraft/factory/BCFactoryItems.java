/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package buildcraft.factory;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import buildcraft.factory.item.ItemWaterGel;
import buildcraft.lib.item.ItemEnchantableMachine;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCItemHolder;

public class BCFactoryItems {
    public static final BCRegistrar.Items ITEMS = BCRegistrar.createItems(BCFactory.MODID);

    public static final BCItemHolder<BlockItem> AUTOWORKBENCH_ITEM = ITEMS.registerSimpleBlockItem(
            BCFactoryBlocks.AUTOWORKBENCH_ITEM);

    public static final BCItemHolder<ItemEnchantableMachine> MINING_WELL = ITEMS.registerBlockItem(
            BCFactoryBlocks.MINING_WELL, ItemEnchantableMachine::new);

    public static final BCItemHolder<BlockItem> PUMP = ITEMS.registerSimpleBlockItem(
            BCFactoryBlocks.PUMP);

    public static final BCItemHolder<BlockItem> FLOOD_GATE = ITEMS.registerSimpleBlockItem(
            BCFactoryBlocks.FLOOD_GATE);

    public static final BCItemHolder<BlockItem> TANK = ITEMS.registerSimpleBlockItem(
            BCFactoryBlocks.TANK);

    public static final BCItemHolder<BlockItem> CHUTE = ITEMS.registerSimpleBlockItem(
            BCFactoryBlocks.CHUTE);

    public static final BCItemHolder<BlockItem> DISTILLER = ITEMS.registerSimpleBlockItem(
            BCFactoryBlocks.DISTILLER);

    public static final BCItemHolder<BlockItem> HEAT_EXCHANGE = ITEMS.registerSimpleBlockItem(
            BCFactoryBlocks.HEAT_EXCHANGE);

    public static final BCItemHolder<ItemWaterGel> WATER_GEL_SPAWN = ITEMS.registerItem(
            "water_gel_spawn",
            ItemWaterGel::new, () -> new Item.Properties().stacksTo(16));

    public static final BCItemHolder<Item> GELLED_WATER = ITEMS.registerSimpleItem("gelled_water");

    public static void init() {
        ITEMS.subscribe();
    }
}
