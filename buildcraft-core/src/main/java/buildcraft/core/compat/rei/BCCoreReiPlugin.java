/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;

import buildcraft.core.BCCoreItems;

/**
 * REI integration plugin for BuildCraft Core.
 * Registers component-based item comparators so REI can distinguish
 * items that share the same item ID but differ by component
 * (e.g. coloured paintbrushes).
 */
public class BCCoreReiPlugin implements REICommonPlugin {

    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        // Tell REI to differentiate paintbrush stacks by their DataComponent values.
        // This covers brush_color, brush_uses, and custom_model_data automatically.
        registry.registerComponents(BCCoreItems.PAINTBRUSH.get());
    }
}
