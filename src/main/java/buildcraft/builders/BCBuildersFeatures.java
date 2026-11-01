/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import java.util.function.Supplier;

//? if >=26.3 {
import com.mojang.serialization.MapCodec;
//?}

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
//? if <26.3 {
/*import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
*///?}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.builders.gen.QuarryPitFeature;
import buildcraft.core.BCCore;

/**
 * Worldgen registrations for the builders subsystem: the abandoned quarry pit. Its data halves are
 * {@code worldgen/configured_feature/quarry_pit} (below 26.3) or {@code worldgen/feature/quarry_pit} (26.3+),
 * {@code worldgen/placed_feature/quarry_pit} (where its rarity lives) and {@code neoforge/biome_modifier/add_quarry_pit}.
 */
public class BCBuildersFeatures {
    //? if >=26.3 {
    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURES =
            DeferredRegister.create(Registries.FEATURE_TYPE, BCCore.MODID);

    public static final Supplier<MapCodec<QuarryPitFeature>> QUARRY_PIT = FEATURES.register(
            "quarry_pit", () -> QuarryPitFeature.CODEC);
    //?} else {
    /*public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, BCCore.MODID);

    public static final Supplier<QuarryPitFeature> QUARRY_PIT = FEATURES.register(
            "quarry_pit", () -> new QuarryPitFeature(NoneFeatureConfiguration.CODEC));
    *///?}

    public static void init(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
