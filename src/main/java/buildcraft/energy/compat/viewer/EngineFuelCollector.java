/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.compat.viewer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
//? if <26.3 {
/*import net.minecraft.world.item.Item;
*///?}
//? if >=1.21.10 && <26.3 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FuelValues;
*///?}

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.ICoolant;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.ISolidCoolant;

/**
 * The engine entries the JEI and REI categories show. Fuel and coolant entries come from {@code BuildcraftFuelRegistry}
 * — populated in {@code BCEnergyRecipes.ensureInitialized()}, the same place the distillation recipes are registered.
 * Stirling fuels come from the client's view of the furnace fuel table, available once in-world (which is when both
 * viewers build their recipe lists); see {@link #stirlingFuels()} for 26.3, where the server sends them.
 */
public final class EngineFuelCollector {
    private EngineFuelCollector() {}

    /** Every registered combustion fuel with a real input fluid. */
    public static List<IFuel> combustionFuels() {
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
    public static List<CombustionCoolant> coolants() {
        List<CombustionCoolant> out = new ArrayList<>();
        if (BuildcraftFuelRegistry.coolant == null) return out;
        for (ICoolant coolant : BuildcraftFuelRegistry.coolant.getCoolants()) {
            FluidStack rep = coolant.getRepresentativeFluid();
            if (rep == null || rep.isEmpty()) continue;
            out.add(new CombustionCoolant(ItemStack.EMPTY, rep, coolant.getDegreesCoolingPerMB(rep, 1f)));
        }
        for (ISolidCoolant solid : BuildcraftFuelRegistry.coolant.getSolidCoolants()) {
            ItemStack rep = solid.getRepresentativeStack();
            if (rep == null || rep.isEmpty()) continue;
            FluidStack produced = solid.getFluidFromSolidCoolant(rep);
            out.add(new CombustionCoolant(rep, produced == null ? FluidStack.EMPTY : produced, 0f));
        }
        return out;
    }

    /**
     * Every furnace fuel the Stirling engine can burn, with the burn time the engine itself would get
     * ({@code TileEngineStone_BC8#getBurnTime}). Below 26.3 that is the level's fuel table, so the list is empty only
     * when no level is loaded.
     *
     * <p>26.3 removed that table, so a client cannot work the times out: the server computes them and sends them
     * ({@link StirlingFuelSync}), and this answers its latest list, empty until the first datapack sync. (JEI fills its
     * 26.3 list from its own furnace-fuel category instead and does not call this there.)
     */
    // getFuel() is 1.21.1-only and Neo-deprecated (its javadoc points at the per-item getBurnTime),
    // but it is the ONLY fuel-map enumeration on that line — FuelValues/fuelItems() arrive at 1.21.10
    // and the >=1.21.10 branch below already uses them. Behavior preserved exactly; warning suppressed.
    @SuppressWarnings("deprecation")
    public static List<StirlingFuel> stirlingFuels() {
        //? if >=26.3 {
        return StirlingFuelSync.received();
        //?} elif >=1.21.10 {
        /*List<StirlingFuel> out = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return out;
        FuelValues fuelValues = level.fuelValues();
        for (Item item : fuelValues.fuelItems()) {
            ItemStack stack = new ItemStack(item);
            int burnTime = stack.getBurnTime(null, fuelValues);
            if (burnTime > 0) {
                out.add(new StirlingFuel(stack, burnTime));
            }
        }
        return out;
        *///?} else {
        /*List<StirlingFuel> out = new ArrayList<>();
        for (Item item : net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel().keySet()) {
            ItemStack stack = new ItemStack(item);
            int burnTime = stack.getBurnTime(null);
            if (burnTime > 0) {
                out.add(new StirlingFuel(stack, burnTime));
            }
        }
        return out;*/
        //?}
    }
}
