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

public class TriggerRobotLinked extends BCStatement implements ITriggerInternal {

    private final boolean reserved;

    public TriggerRobotLinked(boolean reserved) {
        super(reserved ? "buildcraft:robot.reserved" : "buildcraft:robot.linked");
        this.reserved = reserved;
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize(reserved ? "gate.trigger.robot.reserved" : "gate.trigger.robot.linked");
    }

    @Override
    public SpriteHolder getSprite() {
        return reserved ? BCRoboticsSprites.TRIGGER_ROBOT_RESERVED : BCRoboticsSprites.TRIGGER_ROBOT_LINKED;
    }

    /** True when a robot is docked at one of the gate's pipe's stations; the reserved variant fires only
     *  when the dock is the robot's MAIN station (7.1.x verbatim). */
    @Override
    public boolean isTriggerActive(IStatementContainer source, IStatementParameter[] parameters) {
        for (DockingStation station : RobotUtils.getStations(source.getTile())) {
            if (station.isTaken() && (reserved || station.isMainStation())) {
                return true;
            }
        }
        return false;
    }
}
