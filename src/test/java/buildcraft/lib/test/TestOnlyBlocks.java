/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.function.Function;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

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

    /** A shearable block that is never ready for shears (NeoForge's {@code UnshearableBlock}). */
    public static final Identifier UNSHEARABLE_ID = Identifier.parse(NAMESPACE + ":unshearable");

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

    /** The registered unshearable block (valid once registries are populated; NeoForge game-test runs only). */
    public static Block unshearable() {
        return buildcraft.lib.misc.RegistryUtilBC.getValue(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, UNSHEARABLE_ID);
    }

    /**
     * Registers the loader-neutral test blocks through BuildCraft's registrar, during mod construction. Only a
     * game-test run calls it (the loader hook knows which run that is): the test source set is also on the NeoForge
     * dev client's and unit tests' classpath, where a block with no model or blockstate would only add noise (and a
     * registry entry for the sweeps to trip over) for nothing that uses it.
     */
    public static void register() {
        BCRegistrar.Blocks blocks = BCRegistrar.createBlocks(NAMESPACE);
        blocks.registerBlock(REFUSING_SOURCE_ID.getPath(), RefusingSourceBlock::new, TestOnlyBlocks::properties);
        blocks.registerBlock(DRY_FRAGILE_ID.getPath(), p -> new WaterSensitiveBlock(p, true), TestOnlyBlocks::properties);
        blocks.registerBlock(WET_FRAGILE_ID.getPath(), p -> new WaterSensitiveBlock(p, false), TestOnlyBlocks::properties);
        blocks.subscribe();
    }

    /**
     * Registers {@link #UNSHEARABLE_ID}, built by {@code factory}. That block implements NeoForge's
     * {@code IShearable}, so only the NeoForge hook (which may name it) calls this: Fabric has no shearable-block
     * extension to test (FabricShearServices).
     */
    public static void registerUnshearable(Function<BlockBehaviour.Properties, ? extends Block> factory) {
        BCRegistrar.Blocks blocks = BCRegistrar.createBlocks(NAMESPACE);
        blocks.registerBlock(UNSHEARABLE_ID.getPath(), factory, TestOnlyBlocks::properties);
        blocks.subscribe();
    }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().noOcclusion();
    }
}
