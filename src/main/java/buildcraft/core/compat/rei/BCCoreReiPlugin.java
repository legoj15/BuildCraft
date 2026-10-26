/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.forge.REIPluginCommon;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.core.compat.viewer.CoreSubtypes;
import buildcraft.lib.compat.rei.BCReiCommonPlugin;
import buildcraft.lib.compat.rei.ReiSubtypes;

/**
 * REI integration plugin for BuildCraft Core: the item comparators that tell apart stacks sharing an item id, with the
 * same keys JEI's subtype interpreters use ({@code BCCoreJeiPlugin}).
 */
@REIPluginCommon
public class BCCoreReiPlugin implements BCReiCommonPlugin {

    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        // Paintbrushes by colour alone: the uses left on a brush must not split it into its own entry.
        ReiSubtypes.register(registry, BCCoreItems.PAINTBRUSH.get(), stack -> stack.get(BCCore.BRUSH_COLOR.get()));
        // Fragile fluid shards by their stored fluid, ignoring the amount (see CoreSubtypes).
        ReiSubtypes.register(registry, BCCoreItems.FRAGILE_FLUID_CONTAINER.get(), CoreSubtypes::fragileFluidKey);
    }
}
