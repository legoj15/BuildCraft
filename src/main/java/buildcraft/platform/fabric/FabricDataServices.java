/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.BooleanSupplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import buildcraft.platform.registry.DataServices;

/^*
 * {@link DataServices} on Fabric: Fabric API resource conditions and {@link BiomeModifications}. Gap: the data JSON
 * still names its conditions under {@code neoforge:conditions} and its biome modifiers under
 * {@code neoforge/biome_modifier}; until the Phase 2 data transform rewrites them, Fabric reads neither, so the
 * condition type is registered but unused, and the feature is added here directly from {@code feature}.
 *^/
public final class FabricDataServices implements DataServices {
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
