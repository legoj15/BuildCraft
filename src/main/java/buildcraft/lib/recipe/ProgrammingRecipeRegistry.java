/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package buildcraft.lib.recipe;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

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

    /** The Programming Table's option grid: what the GUI shows, what JEI lists and what the JEI transfer can select.
     *  A recipe offering more options than {@link #GRID_CELLS} has the surplus unreachable (paging is not built). */
    public static final int GRID_COLS = 6;
    public static final int GRID_ROWS = 4;
    public static final int GRID_CELLS = GRID_COLS * GRID_ROWS;

    private final Map<String, IProgrammingRecipe> recipes = new TreeMap<>();

    /** Ids already warned about, so the registration-time check and the all-recipes sweep say it once. */
    private final Set<String> warnedOversized = new HashSet<>();

    @Override
    public void addRecipe(IProgrammingRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return;
        }

        if (recipes.containsKey(recipe.getId())) {
            BCLog.logger.warn("Programming Table Recipe '" + recipe.getId() + "' seems to be duplicated! This is a bug!");
        } else {
            recipes.put(recipe.getId(), recipe);
            warnIfOversized(recipe);
        }
    }

    @Override
    public void removeRecipe(String id) {
        recipes.remove(id);
        warnedOversized.remove(id);
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

    /** What to tell the log about {@code recipe}, or null when its program list fits the grid: the table's GUI, the
     *  JEI category and the JEI transfer all stop at {@link #GRID_CELLS} options, so the 25th program (an addon's
     *  boards on top of BuildCraft's own, say) can be crafted by nobody. Asks the recipe for its options, so it only
     *  works once the recipe can build its stacks (items bound). */
    @Nullable
    public static String unreachableOptionsWarning(IProgrammingRecipe recipe) {
        List<ItemStack> options = recipe.getOptions(GRID_COLS, GRID_ROWS);
        int count = options == null ? 0 : options.size();
        if (count <= GRID_CELLS) {
            return null;
        }
        return "Programming Table recipe '" + recipe.getId() + "' offers " + count + " programs, but the table's grid "
                + "shows only " + GRID_CELLS + " (" + GRID_COLS + "x" + GRID_ROWS + "): the last " + (count - GRID_CELLS)
                + " cannot be selected in the GUI, JEI or the transfer";
    }

    /** Re-checks every registered recipe, for the ones whose options could not be built when they were registered
     *  (an addon registering at construction time, before items are bound) or that have grown since (boards
     *  registered afterwards). Each recipe is reported at most once. */
    public void warnAboutUnreachableOptions() {
        for (IProgrammingRecipe recipe : recipes.values()) {
            warnIfOversized(recipe);
        }
    }

    private void warnIfOversized(IProgrammingRecipe recipe) {
        if (warnedOversized.contains(recipe.getId())) {
            return;
        }
        String warning;
        try {
            warning = unreachableOptionsWarning(recipe);
        } catch (RuntimeException e) {
            // The recipe cannot build its option stacks yet (registered before items are bound); the sweep after
            // everything has loaded asks again.
            return;
        }
        if (warning != null) {
            warnedOversized.add(recipe.getId());
            BCLog.logger.warn(warning);
        }
    }
}
