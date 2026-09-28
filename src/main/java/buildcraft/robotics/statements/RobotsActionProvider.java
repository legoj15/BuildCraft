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
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.IActionExternal;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IActionInternalSided;
import buildcraft.api.statements.IActionProvider;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.RobotUtils;

public enum RobotsActionProvider implements IActionProvider {
    INSTANCE;

    /** The robot/station actions, offered only to containers whose tile hosts a station (gated on
     *  {@code RobotUtils.getStations(...)} being non-empty, as in 7.1.x).
     *
     *  <p>The eight robot actions go on every station. The five cargo actions follow 7.1.x's gating, read off
     *  the station's own seams rather than a pipe-type enum so any station host answers the same way:
     *  Request/Accept Items where the station has an item output (7.1.x: an item pipe), Accept Fluids where it
     *  has a fluid output (a fluid pipe), and Provide Items/Fluids where it has an item/fluid input (a wooden
     *  pipe facing an inventory/tank).
     *
     *  <p>Request Needed Items is deliberately never offered: it was dead on arrival in 7.1.x and 8.0.x
     *  alike — an empty activate, no consumers, machines get supplied through {@code getRequestProvider()}'s
     *  neighbour scan without it. It stays registered so gates saved with it load. Only the menu is gated —
     *  an action already set on a gate is kept (the gate validates parameter counts, not this list). */
    @Override
    public void addInternalActions(Collection<IActionInternal> actions, IStatementContainer container) {
        List<DockingStation> stations = RobotUtils.getStations(container.getTile());
        if (stations.isEmpty()) {
            return;
        }
        actions.add(BCRoboticsStatements.ACTION_ROBOT_GOTO_STATION);
        actions.add(BCRoboticsStatements.ACTION_ROBOT_WORK_IN_AREA);
        actions.add(BCRoboticsStatements.ACTION_ROBOT_LOAD_UNLOAD_AREA);
        actions.add(BCRoboticsStatements.ACTION_ROBOT_WAKE_UP);
        actions.add(BCRoboticsStatements.ACTION_ROBOT_FILTER);
        actions.add(BCRoboticsStatements.ACTION_ROBOT_FILTER_TOOL);
        actions.add(BCRoboticsStatements.ACTION_STATION_FORBID_ROBOT);
        actions.add(BCRoboticsStatements.ACTION_STATION_FORCE_ROBOT);

        boolean itemOutput = false;
        boolean fluidOutput = false;
        boolean itemInput = false;
        boolean fluidInput = false;
        for (DockingStation station : stations) {
            itemOutput |= station.getItemOutput() != null;
            fluidOutput |= station.getFluidOutput() != null;
            itemInput |= station.getItemInput() != null;
            fluidInput |= station.getFluidInput() != null;
        }
        if (itemOutput) {
            actions.add(BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS);
            actions.add(BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS);
        }
        if (fluidOutput) {
            actions.add(BCRoboticsStatements.ACTION_STATION_ACCEPT_FLUIDS);
        }
        if (itemInput) {
            actions.add(BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS);
        }
        if (fluidInput) {
            actions.add(BCRoboticsStatements.ACTION_STATION_PROVIDE_FLUIDS);
        }
    }

    @Override
    public void addInternalSidedActions(Collection<IActionInternalSided> actions, IStatementContainer container, Direction side) {
    }

    @Override
    public void addExternalActions(Collection<IActionExternal> actions, Direction side, BlockEntity tile) {
    }
}
