/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import buildcraft.platform.ShearServices;

/^*
 * {@link ShearServices} on Fabric. Gap: Fabric has no shearable-block extension, so no block takes part in the protocol
 * and the stripes pipe's shears handler leaves every block alone.
 *^/
public final class FabricShearServices implements ShearServices {
    @Override
    public boolean isShearableBlock(Block block) {
        return false;
    }

    @Override
    public boolean isShearableAt(Player player, ItemStack tool, Level level, BlockPos pos) {
        return false;
    }

    @Override
    public List<ItemStack> shear(Player player, ItemStack tool, Level level, BlockPos pos) {
        return List.of();
    }
}
*///?}
