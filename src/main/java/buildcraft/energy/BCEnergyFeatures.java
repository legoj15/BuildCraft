/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;


import buildcraft.core.BCCore;
import buildcraft.energy.generation.OilFeature;
import buildcraft.platform.Platform;
import buildcraft.platform.registry.BCFeatureRegistrar;

/**
 * Worldgen registrations for the energy subsystem: the oil {@code Feature} and the biome modifier
 * serializer that attaches it to every biome. The data-driven halves live under
 * {@code data/buildcraftunofficial/worldgen/} ({@code configured_feature/oil} below 26.3,
 * {@code feature/oil} from 26.3 where the configured-feature registry was merged into the feature
 * registry; {@code placed_feature/oil} on every node) and
 * {@code data/buildcraftunofficial/neoforge/biome_modifier/add_oil}. Each node's datapack loader
 * ignores the feature directory it has no registry for, so both ship in the same jar.
 */
public class BCEnergyFeatures {
    // The registry OIL lands in (feature types from 26.3, features below) is BCFeatureRegistrar's business.
    public static final BCFeatureRegistrar FEATURES = new BCFeatureRegistrar(BCCore.MODID);

    public static final Supplier<?> OIL = FEATURES.register("oil", OilFeature::new);

    /** The biome-modifier type that adds the oil feature to <b>every</b> biome, in every dimension, at
     *  {@code top_layer_modification} (after {@code vegetal_decoration}, so the surface pools' tree clearing sees
     *  the chunk's trees). No biome tag can say "all biomes including other mods'", and oil eligibility is
     *  per-chunk, so {@link OilFeature#place} applies the biome/dimension filters itself. */
    public static final Identifier ADD_OIL_MODIFIER = Identifier.fromNamespaceAndPath(BCCore.MODID, "add_oil");

    /** The placed feature {@code neoforge/biome_modifier/add_oil.json} names. */
    public static final ResourceKey<PlacedFeature> OIL_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(BCCore.MODID, "oil"));

    public static void init() {
        FEATURES.subscribe();
        Platform.data().registerAllBiomesFeature(ADD_OIL_MODIFIER, GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                OIL_PLACED);
    }
}
