/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import java.util.function.Supplier;

import net.neoforged.bus.api.IEventBus;

import buildcraft.builders.gen.QuarryPitFeature;
import buildcraft.core.BCCore;
import buildcraft.platform.registry.BCFeatureRegistrar;

/**
 * Worldgen registrations for the builders subsystem: the abandoned quarry pit. Its data halves are
 * {@code worldgen/configured_feature/quarry_pit} (below 26.3) or {@code worldgen/feature/quarry_pit} (26.3+),
 * {@code worldgen/placed_feature/quarry_pit} (where its rarity lives) and {@code neoforge/biome_modifier/add_quarry_pit}.
 */
public class BCBuildersFeatures {
    // The registry QUARRY_PIT lands in (feature types from 26.3, features below) is BCFeatureRegistrar's business.
    public static final BCFeatureRegistrar FEATURES = new BCFeatureRegistrar(BCCore.MODID);

    public static final Supplier<?> QUARRY_PIT = FEATURES.register("quarry_pit", QuarryPitFeature::new);

    public static void init(IEventBus modEventBus) {
        FEATURES.subscribe();
    }
}
