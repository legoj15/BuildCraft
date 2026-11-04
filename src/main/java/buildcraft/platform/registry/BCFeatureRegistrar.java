/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import java.util.function.Supplier;
//? if <26.3 {
/*import java.util.function.Function;

import com.mojang.serialization.Codec;
*///?}

//? if >=26.3 {
import com.mojang.serialization.MapCodec;
//?}

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
//? if <26.3 {
/*import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
*///?}

/**
 * Registers code-side worldgen features that take no configuration, across the 26.3 registry split. From 26.3 the
 * code-side registry holds feature <i>types</i> ({@code MapCodec}s, {@code Registries.FEATURE_TYPE}) and the
 * {@code Registries.FEATURE} registry is datapack-driven ({@code data/<ns>/worldgen/feature/*.json}); below 26.3 the
 * code-side {@code Registries.FEATURE} registry holds the {@code Feature} instances themselves.
 *
 * <p>The one call shape fits both: {@code register("spring", SpringFeature::new)} names the feature's no-argument
 * record constructor on 26.3+ (its codec is registered) and its {@code Codec<NoneFeatureConfiguration>} constructor
 * below (the feature, built with {@code NoneFeatureConfiguration.CODEC}, is registered).
 */
public final class BCFeatureRegistrar {
    //? if >=26.3 {
    private final BCRegistrar<MapCodec<? extends Feature>> registrar;
    //?} else {
    /*private final BCRegistrar<Feature<?>> registrar;
    *///?}

    public BCFeatureRegistrar(String namespace) {
        //? if >=26.3 {
        registrar = BCRegistrar.create(Registries.FEATURE_TYPE, namespace);
        //?} else {
        /*registrar = BCRegistrar.create(Registries.FEATURE, namespace);
        *///?}
    }

    //? if >=26.3 {
    /** Registers the codec of the feature {@code factory} builds. */
    public <F extends Feature> Supplier<?> register(String name, Supplier<F> factory) {
        return registrar.register(name, () -> factory.get().codec());
    }
    //?} else {
    /*/^* Registers the feature {@code factory} builds from {@code NoneFeatureConfiguration.CODEC}. *^/
    public <F extends Feature<NoneFeatureConfiguration>> Supplier<?> register(String name,
            Function<Codec<NoneFeatureConfiguration>, F> factory) {
        return registrar.register(name, () -> factory.apply(NoneFeatureConfiguration.CODEC));
    }
    *///?}

    /** See {@link BCRegistrar#subscribe()}. */
    public void subscribe() {
        registrar.subscribe();
    }
}
