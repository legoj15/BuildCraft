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
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.RobotUtils;

public enum RobotsActionProvider implements IActionProvider {
    INSTANCE;

    /** The fourteen robot/station actions, offered only to containers whose tile hosts a station
     *  (7.1.x verbatim: gated on {@code RobotUtils.getStations(...)} being non-empty). */
    @Override
    public void addInternalActions(Collection<IActionInternal> actions, IStatementContainer container) {
        if (!RobotUtils.getStations(container.getTile()).isEmpty()) {
            actions.add(BCRoboticsStatements.ACTION_ROBOT_GOTO_STATION);
            actions.add(BCRoboticsStatements.ACTION_ROBOT_WORK_IN_AREA);
            actions.add(BCRoboticsStatements.ACTION_ROBOT_LOAD_UNLOAD_AREA);
            actions.add(BCRoboticsStatements.ACTION_ROBOT_WAKE_UP);
            actions.add(BCRoboticsStatements.ACTION_ROBOT_FILTER);
            actions.add(BCRoboticsStatements.ACTION_ROBOT_FILTER_TOOL);
            actions.add(BCRoboticsStatements.ACTION_STATION_FORBID_ROBOT);
            actions.add(BCRoboticsStatements.ACTION_STATION_FORCE_ROBOT);
            actions.add(BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS);
            actions.add(BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS);
            actions.add(BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS);
            actions.add(BCRoboticsStatements.ACTION_STATION_PROVIDE_FLUIDS);
            actions.add(BCRoboticsStatements.ACTION_STATION_ACCEPT_FLUIDS);
            actions.add(BCRoboticsStatements.ACTION_STATION_MACHINE_REQUEST);
        }
    }

    @Override
    public void addInternalSidedActions(Collection<IActionInternalSided> actions, IStatementContainer container, Direction side) {
    }

    @Override
    public void addExternalActions(Collection<IActionExternal> actions, Direction side, BlockEntity tile) {
    }
}
