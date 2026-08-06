/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.IWorldProperty;

/** The "soft" world property — a cell a flying robot may occupy. Ported from 7.1.x {@code WorldPropertyIsSoft}:
 *  air, anything in {@link BuildCraftAPI#softBlocks} (fluids, plantables, snow/vine/fire), or replaceable.
 *  Registered from {@code BCCore.preInit}; without it {@link BuildCraftAPI#isSoftBlock} NPEs on the first
 *  robot pathfind. */
public class WorldPropertyIsSoft implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir()
                || BuildCraftAPI.softBlocks.contains(state.getBlock())
                || state.canBeReplaced();
    }

    @Override
    public void clear() {
    }
}
