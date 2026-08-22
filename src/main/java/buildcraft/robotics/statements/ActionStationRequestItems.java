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
import buildcraft.robotics.entity.EntityRobot;

public class ActionStationRequestItems extends BCStatement implements IActionInternal {

    public ActionStationRequestItems() {
        super("buildcraft:station.request_items");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.action.station.request_items");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.ACTION_STATION_REQUEST_ITEMS;
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
        return new StatementParameterItemStackExact(EntityRobot.INVENTORY_SIZE);
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
