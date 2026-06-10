/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
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
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
//? if >=1.21.10 {
import net.minecraft.world.level.block.entity.FuelValues;
//?}

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.ICoolant;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.ISolidCoolant;

import buildcraft.energy.BCEnergyItems;
import buildcraft.energy.client.gui.ScreenEngineIron;
import buildcraft.energy.client.gui.ScreenEngineStone;

/**
 * JEI integration plugin for BuildCraft Energy. Surfaces the engines as recipe holders:
 * combustion-engine liquid fuels (power rate, burn time, residue), combustion-engine coolants,
 * and Stirling-engine solid fuels (every vanilla furnace fuel). The engine items are registered
 * as catalysts, and the engine GUIs get progress-region click-through (matching the factory
 * machines).
 *
 * <p>Fuel/coolant entries come from {@code BuildcraftFuelRegistry} — populated in
 * {@code BCEnergyRecipes.ensureInitialized()}, the same place the distillation recipes the Distiller category
 * reads are registered, so availability matches that shipped category. Stirling fuels come from
 * the level's {@link FuelValues}, available once in-world (which is when JEI builds its runtime).
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
        registration.addRecipes(EngineFuelJeiTypes.COMBUSTION_FUEL, collectCombustionFuels());
        registration.addRecipes(EngineFuelJeiTypes.COMBUSTION_COOLANT, collectCoolants());
        registration.addRecipes(EngineFuelJeiTypes.STIRLING_FUEL, collectStirlingFuels());
    }

    /** Every registered combustion fuel with a real input fluid. */
    private static List<IFuel> collectCombustionFuels() {
        List<IFuel> fuels = new ArrayList<>();
        if (BuildcraftFuelRegistry.fuel == null) return fuels;
        for (IFuel fuel : BuildcraftFuelRegistry.fuel.getFuels()) {
            FluidStack fluid = fuel.getFluid();
            if (fluid != null && !fluid.isEmpty()) {
                fuels.add(fuel);
            }
        }
        return fuels;
    }

    /** Fluid coolants (water) and solid coolants (the ice family), unified into one display record. */
    private static List<CombustionCoolantJei> collectCoolants() {
        List<CombustionCoolantJei> out = new ArrayList<>();
        if (BuildcraftFuelRegistry.coolant == null) return out;
        for (ICoolant coolant : BuildcraftFuelRegistry.coolant.getCoolants()) {
            FluidStack rep = coolant.getRepresentativeFluid();
            if (rep == null || rep.isEmpty()) continue;
            out.add(new CombustionCoolantJei(ItemStack.EMPTY, rep, coolant.getDegreesCoolingPerMB(rep, 1f)));
        }
        for (ISolidCoolant solid : BuildcraftFuelRegistry.coolant.getSolidCoolants()) {
            ItemStack rep = solid.getRepresentativeStack();
            if (rep == null || rep.isEmpty()) continue;
            FluidStack produced = solid.getFluidFromSolidCoolant(rep);
            out.add(new CombustionCoolantJei(rep, produced == null ? FluidStack.EMPTY : produced, 0f));
        }
        return out;
    }

    /**
     * Every vanilla furnace fuel the Stirling engine can burn, enumerated from the level's
     * {@link FuelValues} the same way the engine itself queries burn time
     * ({@code stack.getBurnTime(null, fuelValues)}). Empty when no level is loaded — JEI builds
     * its runtime in-world, so in practice this is populated.
     */
    private static List<StirlingFuelJei> collectStirlingFuels() {
        List<StirlingFuelJei> out = new ArrayList<>();
        //? if >=1.21.10 {
        Level level = Minecraft.getInstance().level;
        if (level == null) return out;
        FuelValues fuelValues = level.fuelValues();
        for (Item item : fuelValues.fuelItems()) {
            ItemStack stack = new ItemStack(item);
            int burnTime = stack.getBurnTime(null, fuelValues);
            if (burnTime > 0) {
                out.add(new StirlingFuelJei(stack, burnTime));
            }
        }
        //?} else {
        /*for (Item item : net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel().keySet()) {
            ItemStack stack = new ItemStack(item);
            int burnTime = stack.getBurnTime(null);
            if (burnTime > 0) {
                out.add(new StirlingFuelJei(stack, burnTime));
            }
        }*/
        //?}
        return out;
    }

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
