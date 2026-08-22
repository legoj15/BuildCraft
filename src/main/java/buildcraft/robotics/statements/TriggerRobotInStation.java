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
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

public class TriggerRobotInStation extends BCStatement implements ITriggerInternal {

    public TriggerRobotInStation() {
        super("buildcraft:robot.in.station");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.trigger.robot.in_station");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.TRIGGER_ROBOT_IN_STATION;
    }

    /** Red-baseline degenerate: no robot is reported docked yet. Ph6-green checks the station's
     *  robot occupancy against the gate. */
    public static boolean isTriggerActive(DockingStation station) {
        return false;
    }

    @Override
    public boolean isTriggerActive(IStatementContainer source, IStatementParameter[] parameters) {
        return isTriggerActive(null);
    }
}
