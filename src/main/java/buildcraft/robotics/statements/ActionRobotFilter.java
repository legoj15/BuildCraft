/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import java.util.ArrayList;
import java.util.Collection;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.api.statements.StatementSlot;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.inventory.filter.ArrayStackOrListFilter;
import buildcraft.lib.inventory.filter.PassThroughStackFilter;
import buildcraft.lib.inventory.filter.StatementParameterStackFilter;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

public class ActionRobotFilter extends BCStatement implements IActionInternal {

    public ActionRobotFilter() {
        super("buildcraft:robot.work_filter");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.action.robot.filter");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.ACTION_ROBOT_FILTER;
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
        return new StatementParameterItemStack();
    }

    public static Collection<ItemStack> getGateFilterStacks(DockingStation station) {
        ArrayList<ItemStack> result = new ArrayList<>();

        for (StatementSlot slot : station.getActiveActions()) {
            if (slot.statement instanceof ActionRobotFilter) {
                for (IStatementParameter p : slot.parameters) {
                    if (p != null && p instanceof StatementParameterItemStack) {
                        ItemStack stack = ((StatementParameterItemStack) p).getItemStack();
                        if (!stack.isEmpty()) {
                            result.add(stack);
                        }
                    }
                }
            }
        }

        return result;
    }

    public static IStackFilter getGateFilter(DockingStation station) {
        Collection<ItemStack> stacks = getGateFilterStacks(station);

        if (stacks.size() == 0) {
            return new PassThroughStackFilter();
        } else {
            return new ArrayStackOrListFilter(stacks.toArray(new ItemStack[0]));
        }
    }

    /** Red-baseline degenerate: no gate actions exist yet, so no action may be treated as interacting. Ph6
     *  lands the real action-slot scan behind this. */
    public static boolean canInteractWithItem(DockingStation station, IStackFilter filter, Class<?> actionClass) {
        return false;
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
