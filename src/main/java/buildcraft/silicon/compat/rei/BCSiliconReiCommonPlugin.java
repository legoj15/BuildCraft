/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.forge.REIPluginCommon;

import buildcraft.lib.compat.rei.BCReiCommonPlugin;
import buildcraft.lib.compat.rei.ReiSubtypes;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.compat.viewer.SiliconSubtypes;

/**
 * REI item comparators for the silicon pluggables, with the keys JEI's subtype interpreters use
 * ({@link SiliconSubtypes}): without them every lens, gate and facade variant is one entry, and a lookup on any of
 * them shows the recipes of all.
 */
@REIPluginCommon
public class BCSiliconReiCommonPlugin implements BCReiCommonPlugin {

    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        ReiSubtypes.register(registry, BCSiliconItems.PLUG_LENS.get(), SiliconSubtypes::lensKey);
        ReiSubtypes.register(registry, BCSiliconItems.PLUG_GATE.get(), SiliconSubtypes::gateKey);
        ReiSubtypes.register(registry, BCSiliconItems.PLUG_FACADE.get(), SiliconSubtypes::facadeKey);
    }
}
