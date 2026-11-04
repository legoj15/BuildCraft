/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.BooleanSupplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import buildcraft.platform.registry.DataServices;

/^*
 * {@link DataServices} on Fabric: Fabric API resource conditions and {@link BiomeModifications}. The data JSON is
 * NeoForge-shaped; this node's build rewrites its copy ({@code buildcraft.gradle.DataFabric} in buildSrc), so a
 * {@code neoforge:conditions} entry naming a registered condition arrives here as {@code fabric:load_conditions}.
 * NeoForge's biome-modifier JSON is dropped from the Fabric resources: the all-biomes type is added from
 * {@link #registerAllBiomesFeature}'s {@code feature}, and the stock {@code neoforge:add_features} modifiers by
 * {@link #addTaggedBiomeFeatures}.
 *^/
public final class FabricDataServices implements DataServices {
    private static final TagKey<Biome> IS_OVERWORLD =
            TagKey.create(Registries.BIOME, Identifier.parse("minecraft:is_overworld"));

    /^*
     * The Fabric twins of the {@code neoforge:add_features} biome modifiers under
     * {@code data/buildcraftunofficial/neoforge/biome_modifier} (quarry pit, water spring), which this node's
     * resources drop. Called once from the Fabric entrypoint. DataFabric fails the build if a dropped modifier
     * differs from these or names a feature this file does not add.
     *^/
    public static void addTaggedBiomeFeatures() {
        addFeature(IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_DECORATION, "buildcraftunofficial:quarry_pit");
        addFeature(IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_DECORATION, "buildcraftunofficial:water_spring");
    }

    private static void addFeature(TagKey<Biome> biomes, GenerationStep.Decoration step, String placedFeature) {
        BiomeModifications.addFeature(BiomeSelectors.tag(biomes), step,
                ResourceKey.create(Registries.PLACED_FEATURE, Identifier.parse(placedFeature)));
    }

    @Override
    public void registerCondition(Identifier id, BooleanSupplier test) {
        FlagCondition condition = new FlagCondition(test);
        condition.type = ResourceConditionType.create(id, MapCodec.unit(condition));
        ResourceConditions.register(condition.type);
    }

    @Override
    public void registerAllBiomesFeature(Identifier id, GenerationStep.Decoration step,
            ResourceKey<PlacedFeature> feature) {
        BiomeModifications.addFeature(BiomeSelectors.all(), step, feature);
    }

    private static final class FlagCondition implements ResourceCondition {
        private final BooleanSupplier test;
        private ResourceConditionType<FlagCondition> type;

        FlagCondition(BooleanSupplier test) {
            this.test = test;
        }

        @Override
        public ResourceConditionType<?> getType() {
            return type;
        }

        @Override
        public boolean test(RegistryOps.RegistryInfoLookup registryInfo) {
            return test.getAsBoolean();
        }
    }
}
*///?}
