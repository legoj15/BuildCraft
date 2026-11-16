/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;

import buildcraft.platform.LootServices;

/^*
 * {@link LootServices} on MinecraftForge. Gap: Forge 26.3 has no queried-stack loot parameter (its loot additions are
 * a queried loot table id, an attacking entity on two parameter sets and the tool condition), so the builder is
 * returned unchanged, as on Fabric.
 *^/
public final class ForgeLootServices implements LootServices {
    @Override
    public LootParams.Builder withQueriedStack(LootParams.Builder builder, ItemStack stack) {
        return builder;
    }
}
*///?}
