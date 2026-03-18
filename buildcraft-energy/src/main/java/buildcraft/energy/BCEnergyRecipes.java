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
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.lib.misc.MathUtil;

/**
 * Registers all fuel, coolant, and distillation recipe definitions.
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

        // --- Distillation recipes ---
        if (BuildcraftRecipeRegistry.refineryRecipes != null) {
            FluidStack[] gas_light_dense_residue = createFluidStacks("oil", _oil);
            FluidStack[] gas_light_dense = createFluidStacks("oil_distilled", _gas_light_dense);
            FluidStack[] gas_light = createFluidStacks("fuel_mixed_light", _gas_light);
            FluidStack[] gas = createFluidStacks("fuel_gaseous", _gas);
            FluidStack[] light_dense_residue = createFluidStacks("oil_heavy", _light_dense_residue);
            FluidStack[] light_dense = createFluidStacks("fuel_mixed_heavy", _light_dense);
            FluidStack[] light = createFluidStacks("fuel_light", _light);
            FluidStack[] dense_residue = createFluidStacks("oil_dense", _dense_residue);
            FluidStack[] dense = createFluidStacks("fuel_dense", _dense);
            FluidStack[] residue = createFluidStacks("oil_residue", _residue);

            addDistillation(gas_light_dense_residue, gas, light_dense_residue, 0, 32 * MjAPI.MJ);
            addDistillation(gas_light_dense_residue, gas_light, dense_residue, 1, 16 * MjAPI.MJ);
            addDistillation(gas_light_dense_residue, gas_light_dense, residue, 2, 12 * MjAPI.MJ);

            addDistillation(gas_light_dense, gas, light_dense, 0, 24 * MjAPI.MJ);
            addDistillation(gas_light_dense, gas_light, dense, 1, 16 * MjAPI.MJ);

            addDistillation(gas_light, gas, light, 0, 24 * MjAPI.MJ);

            addDistillation(light_dense_residue, light, dense_residue, 1, 16 * MjAPI.MJ);
            addDistillation(light_dense_residue, light_dense, residue, 2, 12 * MjAPI.MJ);

            addDistillation(light_dense, light, dense, 1, 16 * MjAPI.MJ);

            addDistillation(dense_residue, dense, residue, 2, 12 * MjAPI.MJ);

            // --- Heat exchange recipes (for TileHeatExchange) ---
            addHeatExchange("oil");
            addHeatExchange("oil_residue");
            addHeatExchange("oil_heavy");
            addHeatExchange("oil_dense");
            addHeatExchange("oil_distilled");
            addHeatExchange("fuel_dense");
            addHeatExchange("fuel_mixed_heavy");
            addHeatExchange("fuel_light");
            addHeatExchange("fuel_mixed_light");
            addHeatExchange("fuel_gaseous");

            // Water heating: consumed as heat source, produces nothing
            FluidStack water = new FluidStack(Fluids.WATER, 10);
            BuildcraftRecipeRegistry.refineryRecipes.addHeatableRecipe(water, null, 0, 1);

            // Lava cooling: consumed as coolant, produces nothing
            FluidStack lava = new FluidStack(Fluids.LAVA, 5);
            BuildcraftRecipeRegistry.refineryRecipes.addCoolableRecipe(lava, null, 4, 2);
        }
    }

    /**
     * Find the fluid source for a given base name at a specific heat level.
     * Names: "oil" → heat 0 = "oil", heat 1 = "oil_heat_1", heat 2 = "oil_heat_2".
     */
    private static Fluid findFluidByHeat(String baseName, int heat) {
        String regName = baseName + (heat == 0 ? "" : "_heat_" + heat);
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            if (entry.name().equals(regName)) {
                return entry.source().get();
            }
        }
        return null;
    }

    /**
     * Find the cool (heat=0) variant of a named fluid from BCEnergyFluids.
     */
    private static Fluid findFluid(String baseName) {
        return findFluidByHeat(baseName, 0);
    }

    /**
     * Creates a FluidStack array with one entry per heat level (0, 1, 2),
     * matching the 1.12.2 pattern where each fluid has 3 temperature variants.
     */
    private static FluidStack[] createFluidStacks(String baseName, int amount) {
        FluidStack[] arr = new FluidStack[3];
        for (int heat = 0; heat < 3; heat++) {
            Fluid fluid = findFluidByHeat(baseName, heat);
            arr[heat] = fluid != null ? new FluidStack(fluid, amount) : FluidStack.EMPTY;
        }
        return arr;
    }

    private static void addDistillation(FluidStack[] in, FluidStack[] outGas, FluidStack[] outLiquid,
            int heat, long mjCost) {
        FluidStack _in = in[heat];
        FluidStack _outGas = outGas[heat];
        FluidStack _outLiquid = outLiquid[heat];
        if (_in.isEmpty() || _outGas.isEmpty() || _outLiquid.isEmpty()) {
            return; // fluid was disabled
        }
        IDistillationRecipe existing =
            BuildcraftRecipeRegistry.refineryRecipes.getDistillationRegistry().getRecipeForInput(_in);
        if (existing != null) {
            return; // already registered
        }
        // Simplify amounts by dividing by highest common factor
        int hcf = MathUtil.findHighestCommonFactor(_in.getAmount(), _outGas.getAmount());
        hcf = MathUtil.findHighestCommonFactor(hcf, _outLiquid.getAmount());
        if (hcf > 1) {
            _in = _in.copyWithAmount(_in.getAmount() / hcf);
            _outGas = _outGas.copyWithAmount(_outGas.getAmount() / hcf);
            _outLiquid = _outLiquid.copyWithAmount(_outLiquid.getAmount() / hcf);
            mjCost /= hcf;
        }
        BuildcraftRecipeRegistry.refineryRecipes.addDistillationRecipe(_in, _outGas, _outLiquid, mjCost);
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

    /**
     * Registers heatable and coolable recipes between adjacent heat levels
     * for the given fluid base name. Each oil fluid has 3 heat levels (0, 1, 2),
     * so this creates transitions 0→1 and 1→2 (heatable) and 1→0 and 2→1 (coolable),
     * each at 10 mB per operation. Directly mirrors the 1.12.2 addHeatExchange().
     */
    private static void addHeatExchange(String baseName) {
        for (int heat = 0; heat < 2; heat++) {
            Fluid cool = findFluidByHeat(baseName, heat);
            Fluid hot = findFluidByHeat(baseName, heat + 1);
            if (cool == null || hot == null) continue; // fluid variant disabled
            FluidStack coolStack = new FluidStack(cool, 10);
            FluidStack hotStack = new FluidStack(hot, 10);
            BuildcraftRecipeRegistry.refineryRecipes.addHeatableRecipe(coolStack, hotStack, heat, heat + 1);
            BuildcraftRecipeRegistry.refineryRecipes.addCoolableRecipe(hotStack, coolStack, heat + 1, heat);
        }
    }
}

