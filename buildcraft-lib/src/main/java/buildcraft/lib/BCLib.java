/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib;

import java.util.function.Supplier;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.lib.fluid.FuelRegistry;
import buildcraft.lib.fluid.CoolantRegistry;
import buildcraft.lib.recipe.RefineryRecipeRegistry;

@Mod(BCLib.MODID)
public class BCLib {
    public static final String MODID = "buildcraftlib";

    // --- Data Components ---
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    /** Stores the guide book name (e.g. "buildcraftcore:main" or "buildcraftlib:config"). */
    public static final Supplier<DataComponentType<String>> GUIDE_BOOK_NAME = DATA_COMPONENTS
            .registerComponentType("guide_book_name", builder -> builder
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    public BCLib(IEventBus modEventBus, ModContainer modContainer) {
        // Wire fuel/coolant registries
        BuildcraftFuelRegistry.fuel = FuelRegistry.INSTANCE;
        BuildcraftFuelRegistry.coolant = CoolantRegistry.INSTANCE;

        // Wire refinery recipe registry
        BuildcraftRecipeRegistry.refineryRecipes = RefineryRecipeRegistry.INSTANCE;

        BCLibItems.ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);

        // Wire ModelHolderRegistry into NeoForge model baking lifecycle
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            modEventBus.addListener(
                net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted.class,
                event -> {
                    // onTextureStitchPre loads the JSON variable models from disk
                    java.util.HashSet<net.minecraft.resources.Identifier> sprites = new java.util.HashSet<>();
                    buildcraft.lib.client.model.ModelHolderRegistry.onTextureStitchPre(sprites);
                    // onModelBake tells model holders to finalize (NO-OP for variable models, but
                    // needed for static ModelHolder subclasses)
                    buildcraft.lib.client.model.ModelHolderRegistry.onModelBake();
                    buildcraft.lib.misc.data.ModelVariableData.onModelBake();
                }
            );
            // Register debug overlay for IDebuggable block entities on F3 screen
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(buildcraft.lib.client.BCDebugOverlay.class);
        }
    }
}
