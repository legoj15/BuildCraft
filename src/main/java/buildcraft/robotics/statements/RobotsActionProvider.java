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

import buildcraft.api.statements.IActionExternal;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IActionInternalSided;
import buildcraft.api.statements.IActionProvider;
import buildcraft.api.statements.IStatementContainer;

public enum RobotsActionProvider implements IActionProvider {
    INSTANCE;

    /** Red-baseline degenerate: no actions are exposed yet. Ph6-green adds the fourteen robot/station
     *  actions (filters, goto/wakeup/work-area, station input/provide/request/forbid). */
    @Override
    public void addInternalActions(Collection<IActionInternal> actions, IStatementContainer container) {
    }

    @Override
    public void addInternalSidedActions(Collection<IActionInternalSided> actions, IStatementContainer container, Direction side) {
    }

    @Override
    public void addExternalActions(Collection<IActionExternal> actions, Direction side, BlockEntity tile) {
    }
}
