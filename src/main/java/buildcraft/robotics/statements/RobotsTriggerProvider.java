/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import java.util.Collection;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.ITriggerInternalSided;
import buildcraft.api.statements.ITriggerProvider;
import buildcraft.api.statements.IStatementContainer;

public enum RobotsTriggerProvider implements ITriggerProvider {
    INSTANCE;

    /** Red-baseline degenerate: no triggers are exposed yet. Ph6-green adds the four robot triggers
     *  (sleep / in-station / linked / reserved). */
    @Override
    public void addInternalTriggers(Collection<ITriggerInternal> triggers, IStatementContainer container) {
    }

    @Override
    public void addInternalSidedTriggers(Collection<ITriggerInternalSided> triggers, IStatementContainer container, Direction side) {
    }

    @Override
    public void addExternalTriggers(Collection<ITriggerExternal> triggers, Direction side, BlockEntity tile) {
    }
}
