/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.mj.MjAPI;

/**
 * Registers all fuel and coolant definitions for the combustion engine.
 * Ported from 1.12.2 BCEnergyRecipes.
 */
public class BCEnergyRecipes {
    private static final int TIME_BASE = 240_000; // multiple of 3, 5, 16, 1000

    public static void init() {
        // --- Coolants ---
        BuildcraftFuelRegistry.coolant.addCoolant(Fluids.WATER, 0.0023f);
        BuildcraftFuelRegistry.coolant.addSolidCoolant(
            new ItemStack(Blocks.ICE),
            new FluidStack(Fluids.WATER, 1000), 1.5f);
        BuildcraftFuelRegistry.coolant.addSolidCoolant(
            new ItemStack(Blocks.PACKED_ICE),
            new FluidStack(Fluids.WATER, 1000), 2f);

        // --- Fuels (oil-derived) ---
        // Relative amounts from 1.12 refinery chain
        final int _oil = 8;
        final int _gas = 16;
        final int _light = 4;
        final int _dense = 2;
        final int _residue = 1;

        // doubles
        final int _gas_light = 10;
        final int _light_dense = 5;
        final int _dense_residue = 2;

        // triples
        final int _gas_light_dense = 8;
        final int _light_dense_residue = 3;

        // Clean fuels
        addFuel("fuel_gaseous", _gas, 8, 4);
        addFuel("fuel_light", _light, 6, 6);
        addFuel("fuel_dense", _dense, 4, 12);
        addFuel("fuel_mixed_light", _gas_light, 3, 5);
        addFuel("fuel_mixed_heavy", _light_dense, 5, 8);

        // Dirty fuels (produce residue)
        addDirtyFuel("oil_dense", _dense_residue, 4, 4);
        addDirtyFuel("oil_heavy", _light_dense_residue, 2, 4);
        addDirtyFuel("oil", _oil, 3, 4);

        // Oil distilled (clean)
        addFuel("oil_distilled", _gas_light_dense, 1, 5);
    }

    /**
     * Find the cool (heat=0) variant of a named fluid from BCEnergyFluids.
     */
    private static Fluid findFluid(String baseName) {
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            if (entry.name().equals(baseName)) {
                return entry.source().get();
            }
        }
        return null;
    }

    private static void addFuel(String baseName, int amountDiff, int multiplier, int boostOver4) {
        Fluid fuel = findFluid(baseName);
        if (fuel == null) return; // disabled
        long powerPerCycle = multiplier * MjAPI.MJ;
        int totalTime = TIME_BASE * boostOver4 / 4 / multiplier / amountDiff;
        BuildcraftFuelRegistry.fuel.addFuel(fuel, powerPerCycle, totalTime);
    }

    private static void addDirtyFuel(String baseName, int amountDiff, int multiplier, int boostOver4) {
        Fluid fuel = findFluid(baseName);
        if (fuel == null) return; // disabled
        long powerPerCycle = multiplier * MjAPI.MJ;
        int totalTime = TIME_BASE * boostOver4 / 4 / multiplier / amountDiff;
        Fluid residue = findFluid("oil_residue");
        if (residue == null) {
            // residue disabled — register as clean fuel
            BuildcraftFuelRegistry.fuel.addFuel(fuel, powerPerCycle, totalTime);
        } else {
            BuildcraftFuelRegistry.fuel.addDirtyFuel(fuel, powerPerCycle, totalTime,
                new FluidStack(residue, 1000 / amountDiff));
        }
    }
}
