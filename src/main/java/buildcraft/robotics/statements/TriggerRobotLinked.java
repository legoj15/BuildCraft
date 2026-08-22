/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

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

    /** Red-baseline degenerate: no robot is reported docked yet. Ph6-green checks whether the station
     *  is taken by a robot (reserved) or merely linked. */
    public static boolean isTriggerActive(boolean reserved, buildcraft.api.robots.DockingStation station) {
        return false;
    }

    @Override
    public boolean isTriggerActive(IStatementContainer source, IStatementParameter[] parameters) {
        return isTriggerActive(reserved, null);
    }
}
