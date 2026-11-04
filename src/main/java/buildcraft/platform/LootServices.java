/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;

/** Loot context parameters the loader adds. */
public interface LootServices {
    /** Adds the loader's "queried stack" parameter (what a fuel provider is being asked about), if it has one. */
    LootParams.Builder withQueriedStack(LootParams.Builder builder, ItemStack stack);
}
