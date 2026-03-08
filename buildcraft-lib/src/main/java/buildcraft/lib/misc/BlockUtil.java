/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class BlockUtil {

    /** Returns the fluid associated with a block if it is a fluid block, or null otherwise. */
    @Nullable
    public static Fluid getFluidWithFlowing(Block block) {
        if (block instanceof LiquidBlock liquidBlock) {
            Fluid fluid = liquidBlock.fluid;
            if (fluid != null && fluid != Fluids.EMPTY) {
                return fluid;
            }
        }
        return null;
    }
}
