/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.statements;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ITriggerExternal extends ITrigger {

    /** @param pos the neighbouring block the gate looks at, always loaded.
     * @param target its block entity, or null for a block that has none (a vanilla cauldron or composter exposes its
     *            contents through a block capability alone).
     * @param side the side of the gate's pipe that faces {@code pos}. */
    boolean isTriggerActive(Level level, BlockPos pos, @Nullable BlockEntity target, Direction side,
        IStatementContainer source, IStatementParameter[] parameters);

}

