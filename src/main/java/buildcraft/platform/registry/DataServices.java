/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import java.util.function.BooleanSupplier;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Data-driven hooks the loader owns: load conditions on datapack entries and biome modifiers. Each call is made
 * during mod construction. The data JSON stays in NeoForge's canonical shape ({@code neoforge:conditions},
 * {@code neoforge/biome_modifier}).
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 */
public interface DataServices {

    /**
     * Registers the no-data load condition type {@code id} (JSON {@code {"type": "<id>"}}), which holds while
     * {@code test} returns true. It is evaluated when datapacks load, after configs are loaded.
     */
    void registerCondition(Identifier id, BooleanSupplier test);

    /**
     * Registers the biome-modifier type {@code id} that adds a placed feature to <b>every</b> biome at {@code step}.
     * On NeoForge the feature is the one the modifier's JSON names in its {@code feature} field; {@code feature} is
     * the placed feature that JSON names, for a loader without NeoForge's biome-modifier JSON.
     */
    void registerAllBiomesFeature(Identifier id, GenerationStep.Decoration step, ResourceKey<PlacedFeature> feature);
}
