/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
//? if <26.3 {
/*import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
*///?}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import buildcraft.core.BCCore;
import buildcraft.energy.generation.AddOilBiomeModifier;
import buildcraft.energy.generation.OilFeature;

/**
 * Worldgen registrations for the energy subsystem: the oil {@link Feature} and the biome modifier
 * serializer that attaches it to every biome. The data-driven halves live under
 * {@code data/buildcraftunofficial/worldgen/} ({@code configured_feature/oil} below 26.3,
 * {@code feature/oil} from 26.3 where the configured-feature registry was merged into the feature
 * registry; {@code placed_feature/oil} on every node) and
 * {@code data/buildcraftunofficial/neoforge/biome_modifier/add_oil}. Each node's datapack loader
 * ignores the feature directory it has no registry for, so both ship in the same jar.
 */
public class BCEnergyFeatures {
    //? if >=26.3 {
    // 26.3 split the old Feature registry: the code-side registry holds feature TYPES (MapCodecs); the
    // Feature registry (Registries.FEATURE) is now datapack-driven (data/<ns>/worldgen/feature/*.json).
    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURES =
            DeferredRegister.create(Registries.FEATURE_TYPE, BCCore.MODID);
    //?} else {
    /*public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, BCCore.MODID);
    *///?}

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, BCCore.MODID);

    //? if >=26.3 {
    public static final Supplier<MapCodec<OilFeature>> OIL = FEATURES.register(
            "oil", () -> OilFeature.CODEC);
    //?} else {
    /*public static final Supplier<OilFeature> OIL = FEATURES.register(
            "oil", () -> new OilFeature(NoneFeatureConfiguration.CODEC));
    *///?}

    public static final Supplier<MapCodec<AddOilBiomeModifier>> ADD_OIL_MODIFIER =
            BIOME_MODIFIER_SERIALIZERS.register("add_oil", () -> AddOilBiomeModifier.CODEC);

    public static void init(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
