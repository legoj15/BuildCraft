/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.platform.forge;

//? if forge {
/*import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

/^*
 * The Forge twin of {@code NeoForgeAllBiomesFeatureModifier}: a biome modifier that adds the given placed feature to
 * <b>every</b> biome, in every dimension, at one generation step. Its JSON is {@code {"type": <id>, "feature": <placed
 * feature>}}; the step belongs to the type.
 *
 * <p>Forge's stock {@code forge:add_features} could say "every biome" with a {@code forge:any} holder set, but its
 * {@code features} field is a list and BuildCraft's is a single {@code feature}, so the three BuildCraft JSON files
 * would need rewriting; a modifier of our own keeps them as they are. The feature filters by biome and dimension itself,
 * per chunk.
 *
 * <p><b>Not NeoForge's 26.3 shape.</b> NeoForge 26.3 gave {@code BiomeModifier#modify} a leading {@code RegistryAccess};
 * Forge 26.3's is still {@code modify(Holder<Biome>, Phase, BiomeInfo.Builder)}, so no Stonecutter branch belongs
 * here. The builder is a record, so the generation settings come from {@code generationSettings()}, not a getter (on
 * Forge 1.21.1 a class, with {@code getGenerationSettings()}).
 *^/
final class ForgeAllBiomesFeatureModifier implements BiomeModifier {
    private final Holder<PlacedFeature> feature;
    private final GenerationStep.Decoration step;
    private final MapCodec<ForgeAllBiomesFeatureModifier> codec;

    private ForgeAllBiomesFeatureModifier(Holder<PlacedFeature> feature, GenerationStep.Decoration step,
            MapCodec<ForgeAllBiomesFeatureModifier> codec) {
        this.feature = feature;
        this.step = step;
        this.codec = codec;
    }

    /^* The codec of the modifier type that adds its feature at {@code step}. ^/
    @SuppressWarnings("unchecked")
    static MapCodec<ForgeAllBiomesFeatureModifier> codec(GenerationStep.Decoration step) {
        MapCodec<ForgeAllBiomesFeatureModifier>[] self = new MapCodec[1];
        self[0] = RecordCodecBuilder.mapCodec(instance -> instance.group(
                PlacedFeature.CODEC.fieldOf("feature").forGetter((ForgeAllBiomesFeatureModifier m) -> m.feature)
        ).apply(instance, f -> new ForgeAllBiomesFeatureModifier(f, step, self[0])));
        return self[0];
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD) {
            //? if >=26.3 {
            builder.generationSettings().addFeature(step, feature);
            //?} else {
            /^builder.getGenerationSettings().addFeature(step, feature);
            ^///?}
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return codec;
    }
}
*///?}
