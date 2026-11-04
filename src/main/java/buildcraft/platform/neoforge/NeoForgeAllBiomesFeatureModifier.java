/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.platform.neoforge;

//? if neoforge {
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
//? if >=26.3 {
import net.minecraft.core.RegistryAccess;
//?}
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Biome modifier (formerly BuildCraft's {@code AddOilBiomeModifier}) that adds the given placed feature to
 * <b>every</b> biome, in every dimension, at one generation step. Its JSON is {@code {"type": <id>, "feature": <placed
 * feature>}}; the step belongs to the type ({@code buildcraftunofficial:add_oil} uses {@code top_layer_modification}).
 *
 * <p>Why not the stock {@code neoforge:add_features}: that modifier needs an explicit biome list or tag, and no tag
 * can express "all biomes including other mods'". The oil feature filters by biome and dimension itself, per chunk.
 */
final class NeoForgeAllBiomesFeatureModifier implements BiomeModifier {
    private final Holder<PlacedFeature> feature;
    private final GenerationStep.Decoration step;
    private final MapCodec<NeoForgeAllBiomesFeatureModifier> codec;

    private NeoForgeAllBiomesFeatureModifier(Holder<PlacedFeature> feature, GenerationStep.Decoration step,
            MapCodec<NeoForgeAllBiomesFeatureModifier> codec) {
        this.feature = feature;
        this.step = step;
        this.codec = codec;
    }

    /** The codec of the modifier type that adds its feature at {@code step}. */
    @SuppressWarnings("unchecked")
    static MapCodec<NeoForgeAllBiomesFeatureModifier> codec(GenerationStep.Decoration step) {
        MapCodec<NeoForgeAllBiomesFeatureModifier>[] self = new MapCodec[1];
        self[0] = RecordCodecBuilder.mapCodec(instance -> instance.group(
                PlacedFeature.CODEC.fieldOf("feature").forGetter((NeoForgeAllBiomesFeatureModifier m) -> m.feature)
        ).apply(instance, f -> new NeoForgeAllBiomesFeatureModifier(f, step, self[0])));
        return self[0];
    }

    //? if >=26.3 {
    // 26.3 added a leading RegistryAccess to BiomeModifier#modify; this modifier doesn't need it.
    @Override
    public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase,
            ModifiableBiomeInfo.BiomeInfo.Builder builder) {
    //?} else {
    /*@Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
    *///?}
        if (phase == Phase.ADD) {
            builder.getGenerationSettings().addFeature(step, feature);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return codec;
    }
}
//?}
