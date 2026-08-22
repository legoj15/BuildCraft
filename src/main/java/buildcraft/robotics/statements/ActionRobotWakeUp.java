/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

public class ActionRobotWakeUp extends BCStatement implements IActionInternal {

    public ActionRobotWakeUp() {
        super("buildcraft:robot.wakeup");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.action.robot.wakeup");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.ACTION_ROBOT_WAKE_UP;
    }

    /** 7.1.x has this empty too: wakeup happens via {@code AIRobotSleep.preempt} polling the linked
     *  station's active actions for this statement (Ph6-green work). */
    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
