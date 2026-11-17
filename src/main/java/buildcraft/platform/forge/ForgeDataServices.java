/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.BooleanSupplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import buildcraft.platform.registry.DataServices;

/^*
 * {@link DataServices} on MinecraftForge: a condition codec ({@link ForgeFlagCondition}) and a biome-modifier serializer
 * ({@link ForgeAllBiomesFeatureModifier}), each in a deferred register attached at once to BuildCraft's mod bus
 * ({@link ForgeModBus}), in Forge's own codec registries ({@code forge:condition_codecs},
 * {@code forge:biome_modifier_serializers}). The biome modifier's feature comes from its JSON, as on NeoForge, so the
 * {@code feature} argument is unused here.
 *
 * <p>This registers the types only. Forge reads the data under its own names (a top-level {@code forge:condition} object
 * on any datum, recipes included; biome modifiers under the {@code forge} folder of a data pack), so the JSON
 * BuildCraft ships in NeoForge's shape ({@code neoforge:conditions}, {@code neoforge/biome_modifier}) reaches these
 * types only once the Forge build rewrites it ({@code DataForge}), which is the data pipeline's job and not part of this seam.
 *^/
public final class ForgeDataServices implements DataServices {
    @Override
    public void registerCondition(Identifier id, BooleanSupplier test) {
        DeferredRegister<MapCodec<? extends ICondition>> register =
                DeferredRegister.create(ForgeRegistries.Keys.CONDITION_SERIALIZERS, id.getNamespace());
        ForgeFlagCondition condition = new ForgeFlagCondition(id.getPath(), test);
        register.register(id.getPath(), () -> condition.codec);
        ForgeBus.register(register);
    }

    @Override
    public void registerAllBiomesFeature(Identifier id, GenerationStep.Decoration step,
            ResourceKey<PlacedFeature> feature) {
        DeferredRegister<MapCodec<? extends BiomeModifier>> register =
                DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, id.getNamespace());
        MapCodec<ForgeAllBiomesFeatureModifier> codec = ForgeAllBiomesFeatureModifier.codec(step);
        register.register(id.getPath(), () -> codec);
        ForgeBus.register(register);
    }
}
*///?}
