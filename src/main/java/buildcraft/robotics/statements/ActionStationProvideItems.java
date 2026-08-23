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
import buildcraft.api.statements.StatementSlot;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.inventory.filter.ArrayStackOrListFilter;
import buildcraft.lib.inventory.filter.StatementParameterStackFilter;
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

    /** Whether {@code stack} may be extracted: refused only when a filtered provide-items action is
     *  active and the stack is not what it offers — permissive with no (or unfiltered) provide actions. */
    public static boolean canExtractItem(DockingStation station, ItemStack stack) {
        for (StatementSlot s : station.getActiveActions()) {
            if (s.statement instanceof ActionStationProvideItems) {
                StatementParameterStackFilter param = new StatementParameterStackFilter(s.parameters);
                if (param.hasFilter() && !param.matches(new ArrayStackOrListFilter(stack))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
