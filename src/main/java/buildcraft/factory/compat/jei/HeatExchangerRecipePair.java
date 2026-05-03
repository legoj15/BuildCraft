/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import buildcraft.api.recipes.IRefineryRecipeManager.ICoolableRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IHeatableRecipe;

/**
 * One real heat-exchanger operation: a heatable on the cold side paired with
 * a coolable on the hot side. The runtime machine pairs them dynamically
 * whenever {@code coolable.heatFrom() > heatable.heatFrom()}; for JEI we
 * enumerate every valid pair at plugin-init time and surface each as its own
 * recipe entry, so the rendered view is a faithful snapshot of the GUI's
 * four-tank exchange rather than a half-recipe with cycling examples.
 */
public record HeatExchangerRecipePair(IHeatableRecipe heatable, ICoolableRecipe coolable) {}
