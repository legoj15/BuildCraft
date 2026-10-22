/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Blocks that exist only so tests can reach code paths no vanilla or BuildCraft block can. They live in the
 * {@code buildcrafttest} namespace, so every registry sweep that filters on {@code buildcraftunofficial}
 * (waterlogging, blockstate coverage) skips them, and they ship in no jar (the jar holds the main source set only).
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public final class TestOnlyBlocks {

    public static final String NAMESPACE = "buildcrafttest";

    /** A water source whose bucket pickup is always refused; see {@link RefusingSourceBlock}. */
    public static final Identifier REFUSING_SOURCE_ID = Identifier.parse(NAMESPACE + ":refusing_source");

    /** A waterloggable block that cannot survive dry; see {@link WaterSensitiveBlock}. */
    public static final Identifier DRY_FRAGILE_ID = Identifier.parse(NAMESPACE + ":dry_fragile");

    /** A waterloggable block that cannot survive waterlogged (vanilla's pickup destroys it); see
     *  {@link WaterSensitiveBlock}. */
    public static final Identifier WET_FRAGILE_ID = Identifier.parse(NAMESPACE + ":wet_fragile");

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

    /**
     * Registers the test blocks, but only in a game-test run ({@code neoforge.enableGameTest}, set by the
     * {@code gameTestServer} run in build.gradle.kts). The test source set is also on the dev client's and the unit
     * tests' classpath, where a block with no model or blockstate would only add noise (and a registry entry for
     * the sweeps to trip over) for nothing that uses it.
     */
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (Boolean.getBoolean("neoforge.enableGameTest") && event.getRegistryKey().equals(Registries.BLOCK)) {
            event.register(Registries.BLOCK, REFUSING_SOURCE_ID, () -> new RefusingSourceBlock(
                    //? if >=1.21.10 {
                    BlockBehaviour.Properties.of()
                            .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, REFUSING_SOURCE_ID))
                            .noOcclusion()));
                    //?} else {
                    /*BlockBehaviour.Properties.of().noOcclusion()));*/
                    //?}
            event.register(Registries.BLOCK, DRY_FRAGILE_ID, () -> new WaterSensitiveBlock(
                    //? if >=1.21.10 {
                    BlockBehaviour.Properties.of()
                            .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, DRY_FRAGILE_ID))
                            .noOcclusion(), true));
                    //?} else {
                    /*BlockBehaviour.Properties.of().noOcclusion(), true));*/
                    //?}
            event.register(Registries.BLOCK, WET_FRAGILE_ID, () -> new WaterSensitiveBlock(
                    //? if >=1.21.10 {
                    BlockBehaviour.Properties.of()
                            .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, WET_FRAGILE_ID))
                            .noOcclusion(), false));
                    //?} else {
                    /*BlockBehaviour.Properties.of().noOcclusion(), false));*/
                    //?}
        }
    }
}
