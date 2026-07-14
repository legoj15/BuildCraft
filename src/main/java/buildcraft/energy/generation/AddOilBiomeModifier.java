package buildcraft.energy.generation;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import buildcraft.energy.BCEnergyFeatures;

/**
 * Biome modifier that adds the given placed feature (the oil feature) to <b>every</b> biome, in
 * every dimension, at {@code top_layer_modification}.
 *
 * <p>Why not the stock {@code neoforge:add_features}: that modifier needs an explicit biome list or
 * tag, and no tag can express "all biomes including other mods'". Oil eligibility genuinely is
 * per-chunk, not per-biome — a chunk in an excluded biome can still receive the slice of a
 * structure whose origin chunk rolled in an eligible biome — so the biome/dimension config filters
 * must run inside {@link OilFeature#place} anyway. This modifier therefore casts the widest
 * possible net and lets the feature decide (exactly what the retired {@code ChunkEvent.Load}
 * handler did, which also ran for every chunk everywhere).
 *
 * <p>Step choice: {@code top_layer_modification} runs after {@code vegetal_decoration}, so the
 * surface pools' tree clearing sees the generating chunk's trees. The underground sphere/spout/
 * spring don't care about step — they replace whatever is there.
 */
public record AddOilBiomeModifier(Holder<PlacedFeature> feature) implements BiomeModifier {

    public static final MapCodec<AddOilBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PlacedFeature.CODEC.fieldOf("feature").forGetter(AddOilBiomeModifier::feature)
    ).apply(instance, AddOilBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD) {
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, feature);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return BCEnergyFeatures.ADD_OIL_MODIFIER.get();
    }
}
