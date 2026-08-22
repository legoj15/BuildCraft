/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import buildcraft.api.statements.StatementManager;
import buildcraft.robotics.statements.ActionRobotFilter;
import buildcraft.robotics.statements.ActionRobotFilterTool;
import buildcraft.robotics.statements.ActionRobotGotoStation;
import buildcraft.robotics.statements.ActionRobotWakeUp;
import buildcraft.robotics.statements.ActionRobotWorkInArea;
import buildcraft.robotics.statements.ActionRobotWorkInArea.AreaType;
import buildcraft.robotics.statements.ActionStationAcceptFluids;
import buildcraft.robotics.statements.ActionStationAcceptItems;
import buildcraft.robotics.statements.ActionStationForbidRobot;
import buildcraft.robotics.statements.ActionStationProvideFluids;
import buildcraft.robotics.statements.ActionStationProvideItems;
import buildcraft.robotics.statements.ActionStationRequestItems;
import buildcraft.robotics.statements.ActionStationRequestItemsMachine;
import buildcraft.robotics.statements.RobotsActionProvider;
import buildcraft.robotics.statements.RobotsTriggerProvider;
import buildcraft.robotics.statements.StatementParameterItemStackExact;
import buildcraft.robotics.statements.StatementParameterMapLocation;
import buildcraft.robotics.statements.StatementParameterRobot;
import buildcraft.robotics.statements.TriggerRobotInStation;
import buildcraft.robotics.statements.TriggerRobotLinked;
import buildcraft.robotics.statements.TriggerRobotSleep;

/** Robotics statement catalog — the 18 robot/station triggers and actions, plus the three statement
 *  parameters they use. Mirrors {@code buildcraft.core.BCCoreStatements}: every statement is a static
 *  instance (the {@code BCStatement} constructor self-registers into {@code StatementManager}), the
 *  parameter readers are registered in a static block, and {@link #preInit()} hooks the providers. */
public class BCRoboticsStatements {

    public static final ActionRobotFilter ACTION_ROBOT_FILTER = new ActionRobotFilter();
    public static final ActionRobotFilterTool ACTION_ROBOT_FILTER_TOOL = new ActionRobotFilterTool();
    public static final ActionRobotGotoStation ACTION_ROBOT_GOTO_STATION = new ActionRobotGotoStation();
    public static final ActionRobotWakeUp ACTION_ROBOT_WAKE_UP = new ActionRobotWakeUp();
    public static final ActionRobotWorkInArea ACTION_ROBOT_WORK_IN_AREA =
            new ActionRobotWorkInArea(AreaType.WORK);
    public static final ActionRobotWorkInArea ACTION_ROBOT_LOAD_UNLOAD_AREA =
            new ActionRobotWorkInArea(AreaType.LOAD_UNLOAD);

    public static final ActionStationForbidRobot ACTION_STATION_FORBID_ROBOT = new ActionStationForbidRobot(false);
    public static final ActionStationForbidRobot ACTION_STATION_FORCE_ROBOT = new ActionStationForbidRobot(true);
    public static final ActionStationRequestItems ACTION_STATION_REQUEST_ITEMS = new ActionStationRequestItems();
    public static final ActionStationAcceptItems ACTION_STATION_ACCEPT_ITEMS = new ActionStationAcceptItems();
    public static final ActionStationProvideItems ACTION_STATION_PROVIDE_ITEMS = new ActionStationProvideItems();
    public static final ActionStationProvideFluids ACTION_STATION_PROVIDE_FLUIDS = new ActionStationProvideFluids();
    public static final ActionStationAcceptFluids ACTION_STATION_ACCEPT_FLUIDS = new ActionStationAcceptFluids();
    public static final ActionStationRequestItemsMachine ACTION_STATION_MACHINE_REQUEST = new ActionStationRequestItemsMachine();

    public static final TriggerRobotSleep TRIGGER_ROBOT_SLEEP = new TriggerRobotSleep();
    public static final TriggerRobotInStation TRIGGER_ROBOT_IN_STATION = new TriggerRobotInStation();
    public static final TriggerRobotLinked TRIGGER_ROBOT_LINKED = new TriggerRobotLinked(false);
    public static final TriggerRobotLinked TRIGGER_ROBOT_RESERVED = new TriggerRobotLinked(true);

    static {
        // Statement parameters key by the tag their static readFromNbt returns — same mechanism as
        // BCCoreStatements' own param registrations.
        StatementManager.registerParameter(StatementParameterRobot::readFromNbt);
        StatementManager.registerParameter(StatementParameterMapLocation::readFromNbt);
        StatementManager.registerParameter(StatementParameterItemStackExact::readFromNbt);
    }

    private BCRoboticsStatements() {
    }

    /** Registers the robotics trigger/action providers with the statement system. Called from
     *  {@code BCRobotics.init}. */
    public static void preInit() {
        StatementManager.registerTriggerProvider(RobotsTriggerProvider.INSTANCE);
        StatementManager.registerActionProvider(RobotsActionProvider.INSTANCE);
    }
}
