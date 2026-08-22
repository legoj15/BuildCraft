/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

public class ActionStationProvideItems extends BCStatement implements IActionInternal {

    public ActionStationProvideItems() {
        super("buildcraft:station.provide_items");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.action.station.provide_items");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.ACTION_STATION_PROVIDE_ITEMS;
    }

    @Override
    public int maxParameters() {
        return 3;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

    /** Red-baseline degenerate: without any filtered provide action, anything is extractable. Ph6-green
     *  checks the active provide-items actions' parameters against {@code stack}. */
    public static boolean canExtractItem(DockingStation station, ItemStack stack) {
        return true;
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
