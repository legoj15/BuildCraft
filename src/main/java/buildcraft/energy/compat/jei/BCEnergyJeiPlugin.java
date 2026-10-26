/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.jei;

import java.util.ArrayList;
import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
//? if >=26.3 {
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.api.runtime.IJeiRuntime;
//?}
import net.minecraft.resources.Identifier;
//? if >=26.3 {
import net.minecraft.world.item.ItemStack;
//?}

import buildcraft.energy.BCEnergyItems;
import buildcraft.energy.client.gui.ScreenEngineIron;
import buildcraft.energy.client.gui.ScreenEngineStone;
import buildcraft.energy.compat.viewer.EngineFuelCollector;
import buildcraft.energy.compat.viewer.StirlingFuel;

/**
 * JEI integration plugin for BuildCraft Energy. Surfaces the engines as recipe holders:
 * combustion-engine liquid fuels (power rate, burn time, residue), combustion-engine coolants,
 * and Stirling-engine solid fuels (every vanilla furnace fuel). The engine items are registered
 * as catalysts, and the engine GUIs get progress-region click-through (matching the factory
 * machines).
 *
 * <p>The entries come from the viewer-neutral {@link EngineFuelCollector} (REI's plugin reads the same lists).
 * On 26.3 burn times are datapack providers the client cannot resolve, so the Stirling list is
 * copied from JEI's own furnace-fuel category once the runtime is up (see
 * {@code onRuntimeAvailable}); like JEI's, those values are vanilla defaults, not server datapacks.
 */
@JeiPlugin
public class BCEnergyJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:energy_jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CombustionFuelCategory(guiHelper));
        registration.addRecipeCategories(new CombustionCoolantCategory(guiHelper));
        registration.addRecipeCategories(new StirlingFuelCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(EngineFuelJeiTypes.COMBUSTION_FUEL, EngineFuelCollector.combustionFuels());
        registration.addRecipes(EngineFuelJeiTypes.COMBUSTION_COOLANT, EngineFuelCollector.coolants());
        registration.addRecipes(EngineFuelJeiTypes.STIRLING_FUEL, collectStirlingFuels());
    }

    /** The Stirling list: the shared collector everywhere but 26.3, where it comes from JEI's own furnace-fuel
     *  category instead, in {@code onRuntimeAvailable}. */
    private static List<StirlingFuel> collectStirlingFuels() {
        //? if >=26.3 {
        return List.of();
        //?} else {
        /*return EngineFuelCollector.stirlingFuels();
        *///?}
    }

    //? if >=26.3 {
    // 26.3 removed FuelValues: a fuel's burn time is the COOKING_FUEL component's datapack provider,
    // resolvable only with a ServerLevel loot context, and those providers are not synced to clients.
    // JEI already solves this for its furnace-fuel category by evaluating the vanilla definitions
    // client-side, so reuse its result rather than duplicate that evaluation (which needs access to
    // LootContext's private constructor). The engine is no smoker or blast furnace, so the plain
    // furnace values are the ones it gets on a vanilla-data server.
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        List<StirlingFuel> out = new ArrayList<>();
        jeiRuntime.getRecipeManager().createRecipeLookup(RecipeTypes.SMELTING_FUEL).get()
                .forEach((IJeiFuelingRecipe fuel) -> {
                    for (ItemStack stack : fuel.getInputs()) {
                        out.add(new StirlingFuel(stack, fuel.getBurnTime()));
                    }
                });
        if (!out.isEmpty()) {
            jeiRuntime.getRecipeManager().addRecipes(EngineFuelJeiTypes.STIRLING_FUEL, out);
        }
    }
    //?}

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Pressing U on the combustion engine shows its fuels and coolants; on the Stirling
        // engine, its solid fuels.
        registration.addCraftingStation(EngineFuelJeiTypes.COMBUSTION_FUEL, BCEnergyItems.ENGINE_IRON.get());
        registration.addCraftingStation(EngineFuelJeiTypes.COMBUSTION_COOLANT, BCEnergyItems.ENGINE_IRON.get());
        registration.addCraftingStation(EngineFuelJeiTypes.STIRLING_FUEL, BCEnergyItems.ENGINE_STONE.get());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Click the Stirling engine's flame indicator to view its fuels.
        registration.addRecipeClickArea(
                ScreenEngineStone.class,
                81, 25, 14, 14,
                EngineFuelJeiTypes.STIRLING_FUEL
        );
        // Click the centre of the combustion engine panel (the strip between the fuel and coolant
        // tanks, clear of all three tank gauges and of the engine's own tank-click handler) to view
        // its fuels and coolants.
        registration.addRecipeClickArea(
                ScreenEngineIron.class,
                44, 22, 34, 52,
                EngineFuelJeiTypes.COMBUSTION_FUEL,
                EngineFuelJeiTypes.COMBUSTION_COOLANT
        );
    }
}
