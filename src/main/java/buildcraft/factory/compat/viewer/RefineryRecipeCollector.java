/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.compat.viewer;

import java.util.ArrayList;
import java.util.List;

import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager.ICoolableRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IHeatableRecipe;

/** The Heat Exchanger and Distiller entries the JEI and REI categories show, read off the refinery recipe registry. */
public final class RefineryRecipeCollector {
    private RefineryRecipeCollector() {}

    /**
     * Cross every heatable with every coolable hotter than it. The runtime
     * pairing rule in {@code TileHeatExchange.craft()} is exactly
     * {@code coolable.heatFrom() > heatable.heatFrom()}, so each pair we
     * yield is a real operation the machine can run.
     */
    public static List<HeatExchangerRecipePair> heatExchangerPairs() {
        List<HeatExchangerRecipePair> pairs = new ArrayList<>();
        for (IHeatableRecipe h : BuildcraftRecipeRegistry.refineryRecipes.getHeatableRegistry().getAllRecipes()) {
            for (ICoolableRecipe c : BuildcraftRecipeRegistry.refineryRecipes.getCoolableRegistry().getAllRecipes()) {
                if (c.heatFrom() > h.heatFrom()) {
                    pairs.add(new HeatExchangerRecipePair(h, c));
                }
            }
        }
        return pairs;
    }

    /**
     * Every registered distillation recipe with at least one valid input and
     * one valid output. The defensive filter guards against config-disabled
     * fluids slipping into the registry — the runtime would refuse to run
     * such a recipe anyway, so showing it in a recipe viewer would only mislead.
     */
    public static List<IDistillationRecipe> distillationRecipes() {
        List<IDistillationRecipe> recipes = new ArrayList<>();
        for (IDistillationRecipe r : BuildcraftRecipeRegistry.refineryRecipes.getDistillationRegistry().getAllRecipes()) {
            if (r.in() == null || r.in().isEmpty()) continue;
            boolean hasGas = r.outGas() != null && !r.outGas().isEmpty();
            boolean hasLiquid = r.outLiquid() != null && !r.outLiquid().isEmpty();
            if (!hasGas && !hasLiquid) continue;
            recipes.add(r);
        }
        return recipes;
    }
}
