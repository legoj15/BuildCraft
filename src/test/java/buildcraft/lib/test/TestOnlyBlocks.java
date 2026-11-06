/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;

import buildcraft.lib.misc.BlockEntityTypeUtilBC;
import buildcraft.platform.registry.BCBlockHolder;
import buildcraft.platform.registry.BCRegistrar;

/**
 * Blocks that exist only so tests can reach code paths no vanilla or BuildCraft block can. They live in the
 * {@code buildcrafttest} namespace, so every registry sweep that filters on {@code buildcraftunofficial}
 * (waterlogging, blockstate coverage) skips them, and they ship in no jar (the jar holds the main source set only).
 */
public final class TestOnlyBlocks {

    public static final String NAMESPACE = "buildcrafttest";

    /** A water source whose bucket pickup is always refused; see {@link RefusingSourceBlock}. */
    public static final Identifier REFUSING_SOURCE_ID = Identifier.parse(NAMESPACE + ":refusing_source");

    /** A waterloggable block that cannot survive dry; see {@link WaterSensitiveBlock}. */
    public static final Identifier DRY_FRAGILE_ID = Identifier.parse(NAMESPACE + ":dry_fragile");

    /** A waterloggable block that cannot survive waterlogged (vanilla's pickup destroys it); see
     *  {@link WaterSensitiveBlock}. */
    public static final Identifier WET_FRAGILE_ID = Identifier.parse(NAMESPACE + ":wet_fragile");

    /** A shearable block that is never ready for shears; see {@link UnshearableBlock}. */
    public static final Identifier UNSHEARABLE_ID = Identifier.parse(NAMESPACE + ":unshearable");

    /** A two-cell block that drops from both halves, and the item that places it; see {@link TwoCellDropBlock}. */
    public static final Identifier TWO_CELL_DROP_ID = Identifier.parse(NAMESPACE + ":two_cell_drop");

    /** A block entity's join-order probe; see {@link JoinOrderProbeBlock} and {@link JoinOrderProbeBlockEntity}. */
    public static final Identifier JOIN_ORDER_PROBE_ID = Identifier.parse(NAMESPACE + ":join_order_probe");

    private static Supplier<BlockEntityType<JoinOrderProbeBlockEntity>> joinOrderProbeType;

    /** A vanilla fence gate, the class BuildCraft's wrench rotation handlers are scanned for; see
     *  {@code LateRegisteredBlockTester}. */
    public static final Identifier SCANNED_FENCE_GATE_ID = Identifier.parse(NAMESPACE + ":scanned_fence_gate");

    /** A plant, the class BuildCraft's setup lists as a soft block; see {@link ScannedPlantBlock}. */
    public static final Identifier SCANNED_PLANT_ID = Identifier.parse(NAMESPACE + ":scanned_plant");

    private TestOnlyBlocks() {}

    /** The registered {@link RefusingSourceBlock} (valid once registries are populated). */
    public static Block refusingSource() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, REFUSING_SOURCE_ID);
    }

    /** The registered {@code dry_fragile} {@link WaterSensitiveBlock} (valid once registries are populated). */
    public static Block dryFragile() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, DRY_FRAGILE_ID);
    }

    /** The registered {@code wet_fragile} {@link WaterSensitiveBlock} (valid once registries are populated). */
    public static Block wetFragile() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, WET_FRAGILE_ID);
    }

    /** The registered {@link UnshearableBlock} (valid once registries are populated). */
    public static Block unshearable() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, UNSHEARABLE_ID);
    }

    /** The registered {@link TwoCellDropBlock} (valid once registries are populated). */
    public static Block twoCellDrop() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, TWO_CELL_DROP_ID);
    }

    /** The {@code DoubleHighBlockItem} that places {@link #twoCellDrop()} (valid once registries are populated). */
    public static Item twoCellDropItem() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.ITEM, TWO_CELL_DROP_ID);
    }

    /** The registered {@link JoinOrderProbeBlock} (valid once registries are populated). */
    public static Block joinOrderProbe() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, JOIN_ORDER_PROBE_ID);
    }

    /** The {@link JoinOrderProbeBlockEntity}'s type (valid once registries are populated). */
    public static BlockEntityType<JoinOrderProbeBlockEntity> joinOrderProbeType() {
        return joinOrderProbeType.get();
    }

    /** The registered fence gate (valid once registries are populated). */
    public static Block scannedFenceGate() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, SCANNED_FENCE_GATE_ID);
    }

    /** The registered {@link ScannedPlantBlock} (valid once registries are populated). */
    public static Block scannedPlant() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, SCANNED_PLANT_ID);
    }

    /**
     * Registers the loader-neutral test blocks (with the one item and block entity type they need) through BuildCraft's registrar, during mod construction. Only a
     * game-test run calls it (the loader hook knows which run that is): the test source set is also on the NeoForge
     * dev client's and unit tests' classpath, where a block with no model or blockstate would only add noise (and a
     * registry entry for the sweeps to trip over) for nothing that uses it.
     */
    public static void register() {
        BCRegistrar.Blocks blocks = BCRegistrar.createBlocks(NAMESPACE);
        blocks.registerBlock(REFUSING_SOURCE_ID.getPath(), RefusingSourceBlock::new, TestOnlyBlocks::properties);
        blocks.registerBlock(DRY_FRAGILE_ID.getPath(), p -> new WaterSensitiveBlock(p, true), TestOnlyBlocks::properties);
        blocks.registerBlock(WET_FRAGILE_ID.getPath(), p -> new WaterSensitiveBlock(p, false), TestOnlyBlocks::properties);
        blocks.registerBlock(UNSHEARABLE_ID.getPath(), UnshearableBlock::new, TestOnlyBlocks::properties);
        BCBlockHolder<TwoCellDropBlock> twoCellDrop =
                blocks.registerBlock(TWO_CELL_DROP_ID.getPath(), TwoCellDropBlock::new, TestOnlyBlocks::properties);
        BCBlockHolder<JoinOrderProbeBlock> joinOrderProbe =
                blocks.registerBlock(JOIN_ORDER_PROBE_ID.getPath(), JoinOrderProbeBlock::new, TestOnlyBlocks::properties);
        blocks.registerBlock(SCANNED_FENCE_GATE_ID.getPath(), p -> new FenceGateBlock(WoodType.OAK, p),
                TestOnlyBlocks::properties);
        blocks.registerBlock(SCANNED_PLANT_ID.getPath(), ScannedPlantBlock::new, TestOnlyBlocks::properties);
        blocks.subscribe();
        BCRegistrar.Items items = BCRegistrar.createItems(NAMESPACE);
        items.registerBlockItem(twoCellDrop, DoubleHighBlockItem::new);
        items.subscribe();
        BCRegistrar<BlockEntityType<?>> blockEntities = BCRegistrar.create(Registries.BLOCK_ENTITY_TYPE, NAMESPACE);
        joinOrderProbeType = blockEntities.register(JOIN_ORDER_PROBE_ID.getPath(),
                () -> BlockEntityTypeUtilBC.create(JoinOrderProbeBlockEntity::new, joinOrderProbe.get()));
        blockEntities.subscribe();
    }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().noOcclusion();
    }
}
