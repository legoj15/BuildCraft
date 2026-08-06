/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
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
