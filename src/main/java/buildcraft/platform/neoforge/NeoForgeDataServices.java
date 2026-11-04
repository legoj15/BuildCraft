/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.function.BooleanSupplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import buildcraft.platform.registry.DataServices;

/**
 * {@link DataServices} on NeoForge: a condition codec ({@link NeoForgeFlagCondition}) and a biome-modifier serializer
 * ({@link NeoForgeAllBiomesFeatureModifier}), each in a deferred register attached at once to the mod bus of the mod
 * being constructed. The biome modifier's feature comes from its JSON, so the {@code feature} argument is unused here.
 */
public final class NeoForgeDataServices implements DataServices {
    @Override
    public void registerCondition(Identifier id, BooleanSupplier test) {
        DeferredRegister<MapCodec<? extends ICondition>> register =
                DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, id.getNamespace());
        NeoForgeFlagCondition condition = new NeoForgeFlagCondition(id.getPath(), test);
        register.register(id.getPath(), () -> condition.codec);
        register.register(ModLoadingContext.get().getActiveContainer().getEventBus());
    }

    @Override
    public void registerAllBiomesFeature(Identifier id, GenerationStep.Decoration step,
            ResourceKey<PlacedFeature> feature) {
        DeferredRegister<MapCodec<? extends BiomeModifier>> register =
                DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, id.getNamespace());
        MapCodec<NeoForgeAllBiomesFeatureModifier> codec = NeoForgeAllBiomesFeatureModifier.codec(step);
        register.register(id.getPath(), () -> codec);
        register.register(ModLoadingContext.get().getActiveContainer().getEventBus());
    }
}
//?}
