/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib;

import net.neoforged.bus.api.IEventBus;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.lib.fluid.FuelRegistry;
import buildcraft.lib.fluid.CoolantRegistry;
import buildcraft.lib.recipe.RefineryRecipeRegistry;

/**
 * BuildCraft Lib initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCLib {

    /**
     * Developer-mode toggle. When true, developer-facing affordances are enabled
     * (e.g. the {@code /bcsoundtest} command, optional fallback rules in
     * {@code RulesLoader}). When false (default), those features are inert as if
     * they didn't exist.
     *
     * <p>Enable by launching with {@code -Dbuildcraft.dev=true}. Intentionally a
     * {@code static final} so the JIT can elide gated code paths in production.
     */
    public static final boolean DEV = Boolean.getBoolean("buildcraft.dev");

    public static void init(IEventBus modEventBus) {
        // Wire fuel/coolant registries
        BuildcraftFuelRegistry.fuel = FuelRegistry.INSTANCE;
        BuildcraftFuelRegistry.coolant = CoolantRegistry.INSTANCE;

        // Wire refinery recipe registry
        BuildcraftRecipeRegistry.refineryRecipes = RefineryRecipeRegistry.INSTANCE;

        BCLibItems.ITEMS.register(modEventBus);

        // Register the chunk-loading ticket controller (used by the Quarry and other
        // IChunkLoadingTile machines). Must happen on the mod event bus.
        buildcraft.lib.chunkload.ChunkLoaderManager.registerTicketController(modEventBus);

        // Wire ModelHolderRegistry into NeoForge model baking lifecycle
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            buildcraft.lib.client.BCLibClient.initClient(modEventBus);
        }
    }
}
