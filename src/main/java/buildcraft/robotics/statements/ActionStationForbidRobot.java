/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

public class ActionStationForbidRobot extends BCStatement implements IActionInternal {

    private final boolean invert;

    public ActionStationForbidRobot(boolean invert) {
        super(invert ? "buildcraft:station.force_robot" : "buildcraft:station.forbid_robot");
        this.invert = invert;
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize(invert ? "gate.action.station.force_robot" : "gate.action.station.forbid_robot");
    }

    @Override
    public SpriteHolder getSprite() {
        return invert ? BCRoboticsSprites.ACTION_STATION_FORCE_ROBOT : BCRoboticsSprites.ACTION_STATION_FORBID_ROBOT;
    }

    @Override
    public int minParameters() {
        return 1;
    }

    @Override
    public int maxParameters() {
        return 3;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterRobot();
    }

    /** Red-baseline degenerate: no robot is forbidden yet. Ph6-green scans the active forbid/force
     *  actions and matches their board parameters against {@code robot.getBoard().getNBTHandler().getID()}. */
    public static boolean isForbidden(IRobotAccess robot, DockingStation station) {
        return false;
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
