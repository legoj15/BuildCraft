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

public class TriggerRobotSleep extends BCStatement implements ITriggerInternal {

    public TriggerRobotSleep() {
        super("buildcraft:robot.sleep");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.trigger.robot.sleep");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.TRIGGER_ROBOT_SLEEP;
    }

    /** True when the robot docked at one of the gate's pipe's stations is sleeping (7.1.x's {@code
     *  isActive()} renamed — main's {@code isSleeping()} is the modern name). */
    @Override
    public boolean isTriggerActive(IStatementContainer source, IStatementParameter[] parameters) {
        for (DockingStation station : RobotUtils.getStations(source.getTile())) {
            if (station.robotTaking() instanceof EntityRobot robot && robot.isSleeping()) {
                return true;
            }
        }
        return false;
    }
}
