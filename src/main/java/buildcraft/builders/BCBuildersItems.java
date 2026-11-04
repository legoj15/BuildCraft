/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.neoforged.bus.api.IEventBus;
import buildcraft.lib.item.ItemEnchantableMachine;

import buildcraft.api.enums.EnumSnapshotType;
import net.minecraft.world.item.BlockItem;

import buildcraft.builders.item.ItemSchematicSingle;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCItemHolder;

public class BCBuildersItems {
    public static final BCRegistrar.Items ITEMS = BCRegistrar.createItems(BCBuilders.MODID);

    public static final BCItemHolder<BlockItem> FRAME = ITEMS
            .registerSimpleBlockItem(BCBuildersBlocks.FRAME);

    public static final BCItemHolder<BlockItem> FILLER = ITEMS
            .registerSimpleBlockItem(BCBuildersBlocks.FILLER);

    public static final BCItemHolder<BlockItem> BUILDER = ITEMS
            .registerSimpleBlockItem(BCBuildersBlocks.BUILDER);

    public static final BCItemHolder<BlockItem> ARCHITECT = ITEMS
            .registerSimpleBlockItem(BCBuildersBlocks.ARCHITECT);

    public static final BCItemHolder<BlockItem> LIBRARY = ITEMS
            .registerSimpleBlockItem(BCBuildersBlocks.LIBRARY);

    public static final BCItemHolder<BlockItem> REPLACER = ITEMS
            .registerSimpleBlockItem(BCBuildersBlocks.REPLACER);

    public static final BCItemHolder<ItemEnchantableMachine> QUARRY = ITEMS.registerBlockItem(
            BCBuildersBlocks.QUARRY, ItemEnchantableMachine::new);

    public static final BCItemHolder<ItemSnapshot> BLUEPRINT_CLEAN = ITEMS.registerItem(
            "blueprint_clean",
            props -> new ItemSnapshot(props, EnumSnapshotType.BLUEPRINT, false),
            props -> props.stacksTo(16));

    public static final BCItemHolder<ItemSnapshot> BLUEPRINT_USED = ITEMS.registerItem(
            "blueprint_used",
            props -> new ItemSnapshot(props, EnumSnapshotType.BLUEPRINT, true),
            props -> props.stacksTo(1));

    public static final BCItemHolder<ItemSnapshot> TEMPLATE_CLEAN = ITEMS.registerItem(
            "template_clean",
            props -> new ItemSnapshot(props, EnumSnapshotType.TEMPLATE, false),
            props -> props.stacksTo(16));

    public static final BCItemHolder<ItemSnapshot> TEMPLATE_USED = ITEMS.registerItem(
            "template_used",
            props -> new ItemSnapshot(props, EnumSnapshotType.TEMPLATE, true),
            props -> props.stacksTo(1));

    public static final BCItemHolder<ItemSchematicSingle> SCHEMATIC_SINGLE_CLEAN = ITEMS.registerItem(
            "schematic_single_clean",
            props -> new ItemSchematicSingle(props, false),
            props -> props.stacksTo(16));

    public static final BCItemHolder<ItemSchematicSingle> SCHEMATIC_SINGLE_USED = ITEMS.registerItem(
            "schematic_single_used",
            props -> new ItemSchematicSingle(props, true),
            props -> props.stacksTo(1));

    public static void init(IEventBus modEventBus) {
        ITEMS.subscribe();
    }
}
