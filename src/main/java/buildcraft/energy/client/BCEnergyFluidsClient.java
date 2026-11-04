/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.client;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.platform.client.ClientRegistrationSinks;

/**
 * Client-side appearance of every BuildCraft energy fluid: its still/flow sprites (for buckets, fluid shards and, from
 * 26.1, its world model) and its camera-inside fog. Registered from BCEnergyClient.
 *
 * The {@code <fluid>_heat_<n>_still/_flow} sprites are recolored from one
 * grayscale heat base at atlas-stitch time by
 * {@link buildcraft.lib.client.sprite.FluidLerpSpriteSource} (the 1.12.2
 * AtlasSpriteFluid per-pixel lerp), which also explains why each heat tier
 * needs its own sprite. Colors are baked into the sprites, so no runtime tint
 * is needed.
 */
public class BCEnergyFluidsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BCEnergyFluidsClient.class);

    /** Registers every energy fluid's appearance. */
    public static void register(ClientRegistrationSinks sinks) {
        LOGGER.info("[BCEnergyFluidsClient] Registering the appearance of {} fluid entries.", BCEnergyFluids.ALL.size());
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            // Reference the stitch-time recolored sprites (FluidLerpSpriteSource):
            // "buildcraftunofficial:block/fluids/<baseName>_heat_<heat>_still"
            String spriteName = entry.baseName() + "_heat_" + entry.heat();
            Identifier stillId = Identifier.fromNamespaceAndPath("buildcraftunofficial", "block/fluids/" + spriteName + "_still");
            Identifier flowId = Identifier.fromNamespaceAndPath("buildcraftunofficial", "block/fluids/" + spriteName + "_flow");
            sinks.fluidAppearance(entry.source(), entry.flowing(), stillId, flowId, entry.tintColor(),
                    entry.gaseous() ? 5.0F : 3.0F);
        }
    }
}
