/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.jei;

import mezz.jei.api.recipe.RecipeType;

import buildcraft.api.fuels.IFuel;

/**
 * Holds the JEI {@link RecipeType}s for the energy module's engine fuel/coolant
 * recipe-holder categories.
 *
 * <ul>
 * <li>{@link #COMBUSTION_FUEL} — one entry per registered {@link IFuel} (the combustion
 *     engine's liquid fuels); the category reads power/burn-time straight off the interface
 *     and an extra residue output for {@code IFuelManager.IDirtyFuel}.</li>
 * <li>{@link #COMBUSTION_COOLANT} — water + the three ices that cool the combustion engine.</li>
 * <li>{@link #STIRLING_FUEL} — every vanilla furnace fuel (the Stirling engine burns solid
 *     fuel via {@code FuelValues}).</li>
 * </ul>
 */
public final class EngineFuelJeiTypes {
    public static final RecipeType<IFuel> COMBUSTION_FUEL = RecipeType.create(
            "buildcraftunofficial", "combustion_engine_fuel", IFuel.class);

    public static final RecipeType<CombustionCoolantJei> COMBUSTION_COOLANT = RecipeType.create(
            "buildcraftunofficial", "combustion_engine_coolant", CombustionCoolantJei.class);

    public static final RecipeType<StirlingFuelJei> STIRLING_FUEL = RecipeType.create(
            "buildcraftunofficial", "stirling_engine_fuel", StirlingFuelJei.class);

    private EngineFuelJeiTypes() {}
}
