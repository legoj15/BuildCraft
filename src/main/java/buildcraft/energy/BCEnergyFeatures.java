package buildcraft.energy;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
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
 * {@code data/buildcraftunofficial/worldgen/} ({@code configured_feature/oil},
 * {@code placed_feature/oil}) and {@code data/buildcraftunofficial/neoforge/biome_modifier/add_oil}.
 */
public class BCEnergyFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, BCCore.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, BCCore.MODID);

    public static final Supplier<OilFeature> OIL = FEATURES.register(
            "oil", () -> new OilFeature(NoneFeatureConfiguration.CODEC));

    public static final Supplier<MapCodec<AddOilBiomeModifier>> ADD_OIL_MODIFIER =
            BIOME_MODIFIER_SERIALIZERS.register("add_oil", () -> AddOilBiomeModifier.CODEC);

    public static void init(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
