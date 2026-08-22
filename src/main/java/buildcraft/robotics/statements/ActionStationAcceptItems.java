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
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

/** Accept-items action. 7.1.x registered this under the {@code station.drop_in_pipe} alias as well as
 *  its own tag — the multi-tag constructor preserves both. */
public class ActionStationAcceptItems extends BCStatement implements IActionInternal {

    public ActionStationAcceptItems() {
        super("buildcraft:station.accept_items", "buildcraft:station.drop_in_pipe");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.action.station.accept_items");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.ACTION_STATION_ACCEPT_ITEMS;
    }

    @Override
    public int maxParameters() {
        return 3;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
