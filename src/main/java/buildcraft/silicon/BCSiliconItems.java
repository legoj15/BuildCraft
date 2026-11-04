/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import buildcraft.silicon.item.ItemGateCopier;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.item.ItemPluggableLens;
import buildcraft.silicon.item.ItemPluggablePulsar;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCItemHolder;

public class BCSiliconItems {
    public static final BCRegistrar.Items ITEMS = BCRegistrar.createItems(BCSilicon.MODID);

    // Block items
    public static final BCItemHolder<BlockItem> LASER =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.LASER);

    public static final BCItemHolder<BlockItem> ASSEMBLY_TABLE =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.ASSEMBLY_TABLE);

    public static final BCItemHolder<BlockItem> ADVANCED_CRAFTING_TABLE =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.ADVANCED_CRAFTING_TABLE);

    public static final BCItemHolder<BlockItem> PROGRAMMING_TABLE =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.PROGRAMMING_TABLE);

    // Shipped alongside the Programming Table since Ph7 registered the robot integration recipe.
    public static final BCItemHolder<BlockItem> INTEGRATION_TABLE =
            ITEMS.registerSimpleBlockItem(BCSiliconBlocks.INTEGRATION_TABLE);

    // Chipsets — each variant is a separate item (replacing 1.12.2 metadata sub-items).
    // Naming: chipset_<material>, matching the pipe_<material>_<flow> role-prefix scheme.
    public static final BCItemHolder<Item> CHIPSET_REDSTONE =
            ITEMS.registerSimpleItem("chipset_redstone");

    public static final BCItemHolder<Item> CHIPSET_IRON =
            ITEMS.registerSimpleItem("chipset_iron");

    public static final BCItemHolder<Item> CHIPSET_GOLD =
            ITEMS.registerSimpleItem("chipset_gold");

    public static final BCItemHolder<Item> CHIPSET_QUARTZ =
            ITEMS.registerSimpleItem("chipset_quartz");

    public static final BCItemHolder<Item> CHIPSET_DIAMOND =
            ITEMS.registerSimpleItem("chipset_diamond");

    // Redstone Crystal — 7.1.x's robot-recipe core (BuildCraftSilicon.redstoneCrystal), restored at
    // an 8.0-scaled Assembly Table cost; 1.12.2 dropped it and left only a lang remnant.
    public static final BCItemHolder<Item> REDSTONE_CRYSTAL =
            ITEMS.registerSimpleItem("redstone_crystal");

    // Gate Copier
    public static final BCItemHolder<ItemGateCopier> GATE_COPIER =
            ITEMS.registerItem("gate_copier", ItemGateCopier::new);

    // Facade
    public static final BCItemHolder<ItemPluggableFacade> PLUG_FACADE =
            ITEMS.registerItem("plug_facade", ItemPluggableFacade::new);

    // Gate
    public static final BCItemHolder<ItemPluggableGate> PLUG_GATE =
            ITEMS.registerItem("plug_gate", ItemPluggableGate::new);

    // Pulsar
    public static final BCItemHolder<ItemPluggablePulsar> PLUG_PULSAR =
            ITEMS.registerItem("plug_pulsar", ItemPluggablePulsar::new);

    // Lens
    public static final BCItemHolder<ItemPluggableLens> PLUG_LENS =
            ITEMS.registerItem("plug_lens", ItemPluggableLens::new);

    // Light Sensor
    public static final BCItemHolder<Item> PLUG_LIGHT_SENSOR =
            ITEMS.registerItem("plug_light_sensor", props -> new buildcraft.transport.item.ItemPluggableSimple(props, buildcraft.silicon.BCSiliconPlugs.lightSensor, null));

    // Timer
    public static final BCItemHolder<Item> PLUG_TIMER =
            ITEMS.registerItem("plug_timer", props -> new buildcraft.transport.item.ItemPluggableSimple(props, buildcraft.silicon.BCSiliconPlugs.timer, null));


    public static void init(IEventBus modEventBus) {
        ITEMS.subscribe();
    }
}
