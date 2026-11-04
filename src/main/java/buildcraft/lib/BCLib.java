/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib;


import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.lib.fluid.FuelRegistry;
import buildcraft.lib.fluid.CoolantRegistry;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.recipe.RefineryRecipeRegistry;
import buildcraft.platform.Platform;

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

    public static void init() {
        // Wire fuel/coolant registries
        BuildcraftFuelRegistry.fuel = FuelRegistry.INSTANCE;
        BuildcraftFuelRegistry.coolant = CoolantRegistry.INSTANCE;

        // Wire refinery recipe registry
        BuildcraftRecipeRegistry.refineryRecipes = RefineryRecipeRegistry.INSTANCE;

        // BCFluidStack (api, loader-neutral) names itself through the loader's fluid services
        BCFluidStack.setHoverNameProvider(
            stack -> Platform.fluids().hoverName(stack.getFluidHolder(), stack.getAmount(), stack.getComponentsPatch()));
        // NbtApiUtil (api, loader-neutral) resolves registries through lib's loader-aware lookup
        NbtApiUtil.setRegistryOpsSource(NBTUtilBC::registryAwareOps);

        BCLibItems.ITEMS.subscribe();

        //? if >=26.1 {
        // Retires a carried-over pre-26.1 saved-data file once its data is seen in the 26.1+ file.
        buildcraft.lib.misc.LegacySavedData.registerListeners();
        //?}

        // Register the chunk-loading ticket controller (used by the Quarry and other
        // IChunkLoadingTile machines). Must happen on the mod event bus.
        buildcraft.platform.Platform.chunkTickets().register();

        // Wire ModelHolderRegistry into NeoForge model baking lifecycle
        if (Platform.loader().isPhysicalClient()) {
            buildcraft.lib.client.BCLibClient.initClient();
        }
    }
}
