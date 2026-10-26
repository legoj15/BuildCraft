/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.forge.REIPluginCommon;

import buildcraft.lib.compat.rei.BCReiCommonPlugin;
import buildcraft.lib.compat.rei.ReiSubtypes;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.compat.viewer.RoboticsSubtypes;

/**
 * REI item comparators for boards and robots, with the keys JEI's subtype interpreters use
 * ({@link RoboticsSubtypes}): by program, and robots also by charged vs drained. Without them every board is one
 * entry (and every robot another), because the program rides in {@code CUSTOM_DATA}.
 */
@REIPluginCommon
public class BCRoboticsReiCommonPlugin implements BCReiCommonPlugin {

    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        ReiSubtypes.register(registry, BCRoboticsItems.REDSTONE_BOARD.get(), RoboticsSubtypes::boardKey);
        ReiSubtypes.register(registry, BCRoboticsItems.ROBOT.get(), RoboticsSubtypes::robotKey);
    }
}
