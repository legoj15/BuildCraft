/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

//? if >=1.21.10 {
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
//?} else {
/*import me.shedaniel.rei.api.common.plugins.REIServerPlugin;*/
//?}

/**
 * The common-side REI plugin type BuildCraft's {@code @REIPluginCommon} classes implement (they register the item
 * comparators). REI 17+ names it {@code REICommonPlugin}; 1.21.1's REI 16 calls the same role {@code REIServerPlugin},
 * which its plugin detector binds {@code @REIPluginCommon} to and whose {@code REIPlugin} base carries
 * {@code registerItemComparators}.
 */
//? if >=1.21.10 {
public interface BCReiCommonPlugin extends REICommonPlugin {}
//?} else {
/*public interface BCReiCommonPlugin extends REIServerPlugin {}*/
//?}
