/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core;

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

import buildcraft.core.gen.SpringFeature;

public class BCCoreFeatures {
    //? if >=26.3 {
    // 26.3 split the old Feature registry: the code-side registry holds feature TYPES (MapCodecs); the
    // Feature registry (Registries.FEATURE) is now datapack-driven (data/<ns>/worldgen/feature/*.json).
    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURES =
            DeferredRegister.create(Registries.FEATURE_TYPE, BCCore.MODID);

    public static final Supplier<MapCodec<SpringFeature>> SPRING = FEATURES.register(
            "spring", () -> SpringFeature.CODEC);
    //?} else {
    /*public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, BCCore.MODID);

    public static final Supplier<SpringFeature> SPRING = FEATURES.register(
            "spring", () -> new SpringFeature(NoneFeatureConfiguration.CODEC));
    *///?}

    public static void init(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
