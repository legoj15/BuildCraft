/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.guide;

import buildcraft.lib.client.guide.parts.recipe.RecipeLookupHelper;

/** Hands the guide book the two robotics table lookups. Robotics registers them itself rather than lib's
 *  {@link RecipeLookupHelper} naming them, so lib keeps no dependency on robotics. Called from client init; the guide
 *  only runs on the client. */
public final class RoboticsGuideRecipes {
    private RoboticsGuideRecipes() {}

    public static void register() {
        RecipeLookupHelper.register("programming", GuideProgrammingRecipes.INSTANCE);
        RecipeLookupHelper.register("integration", GuideIntegrationRecipes.INSTANCE);
    }
}
