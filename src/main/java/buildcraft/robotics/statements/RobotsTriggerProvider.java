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
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.RobotUtils;

public enum RobotsTriggerProvider implements ITriggerProvider {
    INSTANCE;

    /** The four robot triggers, offered only to containers whose tile hosts a station (7.1.x verbatim:
     *  gated on {@code RobotUtils.getStations(...)} being non-empty). */
    @Override
    public void addInternalTriggers(Collection<ITriggerInternal> triggers, IStatementContainer container) {
        if (!RobotUtils.getStations(container.getTile()).isEmpty()) {
            triggers.add(BCRoboticsStatements.TRIGGER_ROBOT_SLEEP);
            triggers.add(BCRoboticsStatements.TRIGGER_ROBOT_IN_STATION);
            triggers.add(BCRoboticsStatements.TRIGGER_ROBOT_LINKED);
            triggers.add(BCRoboticsStatements.TRIGGER_ROBOT_RESERVED);
        }
    }

    @Override
    public void addInternalSidedTriggers(Collection<ITriggerInternalSided> triggers, IStatementContainer container, Direction side) {
    }

    @Override
    public void addExternalTriggers(Collection<ITriggerExternal> triggers, Direction side, BlockEntity tile) {
    }
}
