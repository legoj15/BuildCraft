/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package buildcraft.lib.recipe;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import buildcraft.api.core.BCLog;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.api.recipes.IProgrammingRecipeManager;

/** Ported from 7.1.x {@code buildcraft.core.recipes.ProgrammingRecipeManager}. Duplicate ids warn and are ignored
 *  (7.1.x behaviour) rather than throwing, unlike {@link IntegrationRecipeRegistry}.
 *
 *  <p>Recipes are kept in <b>id order</b>, not registration order and certainly not hash order (7.1.x's
 *  {@code HashMap}): when two recipes accept the same input, the table, the JEI transfer and the JEI collector must
 *  all pick the same one, and the same one on the client as on the server, whatever order mods happened to register
 *  in. The lowest id wins. */
public class ProgrammingRecipeRegistry implements IProgrammingRecipeManager {
    public static final ProgrammingRecipeRegistry INSTANCE = new ProgrammingRecipeRegistry();

    /** The Programming Table's option grid: the cells the GUI shows at a time, and what a recipe is asked for in
     *  {@code getOptions}. A recipe offering more options than the grid has cells is paged through in the GUI and
     *  the JEI category ({@code ProgrammingTableLayout}), so none are unreachable. */
    public static final int GRID_COLS = 6;
    public static final int GRID_ROWS = 4;

    private final Map<String, IProgrammingRecipe> recipes = new TreeMap<>();

    @Override
    public void addRecipe(IProgrammingRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return;
        }

        if (recipes.containsKey(recipe.getId())) {
            BCLog.logger.warn("Programming Table Recipe '" + recipe.getId() + "' seems to be duplicated! This is a bug!");
        } else {
            recipes.put(recipe.getId(), recipe);
        }
    }

    @Override
    public void removeRecipe(String id) {
        recipes.remove(id);
    }

    @Override
    public void removeRecipe(IProgrammingRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return;
        }

        removeRecipe(recipe.getId());
    }

    @Override
    public Collection<IProgrammingRecipe> getRecipes() {
        return Collections.unmodifiableCollection(recipes.values());
    }

    @Override
    public IProgrammingRecipe getRecipe(String id) {
        return recipes.get(id);
    }
}
