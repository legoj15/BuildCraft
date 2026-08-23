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
import buildcraft.robotics.RobotUtils;
import buildcraft.robotics.entity.EntityRobot;

public class TriggerRobotInStation extends BCStatement implements ITriggerInternal {

    public TriggerRobotInStation() {
        super("buildcraft:robot.in.station");
    }

    @Override
    public String getDescription() {
        // The dotted key is 7.1.x verbatim (and matches en_us.json) — not a typo for in_station.
        return LocaleUtil.localize("gate.trigger.robot.in.station");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.TRIGGER_ROBOT_IN_STATION;
    }

    /** True when a robot docked at one of the gate's pipe's stations matches (7.1.x verbatim): the
     *  docked robot must be actually linked to the station, and an optional board parameter must match. */
    @Override
    public boolean isTriggerActive(IStatementContainer source, IStatementParameter[] parameters) {
        for (DockingStation station : RobotUtils.getStations(source.getTile())) {
            if (station.robotTaking() instanceof EntityRobot robot && robot.getDockingStation() == station) {
                if (parameters.length > 0 && parameters[0] != null
                        && !parameters[0].getItemStack().isEmpty()
                        && !StatementParameterRobot.matches(parameters[0], robot)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }
}
