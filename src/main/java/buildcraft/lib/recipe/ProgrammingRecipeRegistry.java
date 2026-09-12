/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package buildcraft.lib.recipe;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import buildcraft.api.core.BCLog;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.api.recipes.IProgrammingRecipeManager;

/** Ported from 7.1.x {@code buildcraft.core.recipes.ProgrammingRecipeManager}. Duplicate ids warn and are ignored
 *  (7.1.x behaviour) rather than throwing, unlike {@link IntegrationRecipeRegistry}. */
public class ProgrammingRecipeRegistry implements IProgrammingRecipeManager {
    public static final ProgrammingRecipeRegistry INSTANCE = new ProgrammingRecipeRegistry();

    private final Map<String, IProgrammingRecipe> recipes = new HashMap<>();

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

        recipes.remove(recipe.getId());
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
