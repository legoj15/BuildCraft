/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.crops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.crops.CropManager;
import buildcraft.api.crops.ICropHandler;

/** Sugar cane, which the default {@link CropHandlerPlantable} cannot see: the cane block extends {@code Block}
 *  directly (never the plant umbrella), so its item is no "seed" there. Registered alongside the default
 *  handler at boot, exactly as 1.12.2's {@code BCLibRegistries} and 7.1.x's {@code BuildCraftCore} did.
 *
 *  <p>The soil rule is 1.12.2's {@code canSustainPlant(reeds) && block != reeds && isAirBlock(up)}. The
 *  first clause is spelled as the cane block's own {@code canSurvive} one cell up — the modern home of the
 *  sand/dirt-beside-water rule, including NeoForge's soil and hydration hooks — which on its own would also
 *  accept cane standing on cane; the second clause keeps the Planter from stacking it. Harvesting stays with
 *  the default handler's stacked-on-its-own-kind maturity rule. */
public enum CropHandlerReeds implements ICropHandler {
    INSTANCE;

    @Override
    public boolean isSeed(ItemStack stack) {
        return stack.is(Items.SUGAR_CANE);
    }

    @Override
    public boolean canSustainPlant(Level world, ItemStack seed, BlockPos pos) {
        BlockPos placePos = pos.above();
        return !world.getBlockState(pos).is(Blocks.SUGAR_CANE)
                && world.isEmptyBlock(placePos)
                && Blocks.SUGAR_CANE.defaultBlockState().canSurvive(world, placePos);
    }

    @Override
    public boolean plantCrop(Level world, Player player, ItemStack seed, BlockPos pos) {
        return CropManager.getDefaultHandler().plantCrop(world, player, seed, pos);
    }

    @Override
    public boolean isMature(BlockGetter access, BlockState state, BlockPos pos) {
        return false;
    }

    @Override
    public boolean harvestCrop(Level world, BlockPos pos, NonNullList<ItemStack> drops) {
        return false;
    }
}
