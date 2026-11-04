/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.neoforged.neoforge.common.Tags;

import buildcraft.lib.misc.CommonTags;

/** {@link CommonTags} must name exactly the tags NeoForge's own constants do, on every node. */
public class CommonTagsTester {
    @Test
    public void hiddenFromRecipeViewersMatchesNeoForge() {
        assertEquals(Tags.Items.HIDDEN_FROM_RECIPE_VIEWERS, CommonTags.HIDDEN_FROM_RECIPE_VIEWERS_ITEM);
    }

    @Test
    public void wrenchToolsMatchesNeoForge() {
        assertEquals(Tags.Items.TOOLS_WRENCH, CommonTags.TOOLS_WRENCH);
    }
}
