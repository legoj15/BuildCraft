/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;

import buildcraft.platform.LootServices;

/** {@link LootServices} on NeoForge: its {@code queried_stack} loot parameter (26.3 and later only). */
public final class NeoForgeLootServices implements LootServices {
    @Override
    public LootParams.Builder withQueriedStack(LootParams.Builder builder, ItemStack stack) {
        //? if >=26.3 {
        return builder.withOptionalParameter(net.neoforged.neoforge.common.loot.NeoForgeLootContextParams.QUERIED_STACK,
            stack);
        //?} else {
        /*return builder;*/
        //?}
    }
}
//?}
