/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

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

    /** Red-baseline degenerate: no robot is reported sleeping yet. Ph6-green checks
     *  {@code robot.isSleeping()} on the robot docked at the gate's station. */
    public static boolean isTriggerActive(IRobotAccess robot) {
        return false;
    }

    @Override
    public boolean isTriggerActive(IStatementContainer source, IStatementParameter[] parameters) {
        return isTriggerActive(null);
    }
}
