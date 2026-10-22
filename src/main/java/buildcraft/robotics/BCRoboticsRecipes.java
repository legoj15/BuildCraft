/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.lib.recipe.IntegrationRecipeRegistry;
import buildcraft.lib.recipe.ProgrammingRecipeRegistry;

/** Ph7 recipe registrations, mirroring {@code BCSiliconRecipes}: the two robotics table recipes are code-defined
 *  objects (no vanilla {@code RecipeType} — that is what keeps them clear of the CustomRecipe cross-node serializer
 *  cliff), and they build {@code Ingredient}s in their constructors, so they must register after items bind.
 *  Servers call this from {@code ServerAboutToStartEvent}; a multiplayer client (where that event never fires) calls
 *  it from {@code LoggingIn} when its GUI needs the option grid — both routed through the same once-per-JVM guard. */
public class BCRoboticsRecipes {

    private static boolean initialized = false;

    public static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        ProgrammingRecipeRegistry.INSTANCE.addRecipe(new BoardProgrammingRecipe());
        IntegrationRecipeRegistry.INSTANCE.addRecipe(new RobotIntegrationRecipe());
    }
}
