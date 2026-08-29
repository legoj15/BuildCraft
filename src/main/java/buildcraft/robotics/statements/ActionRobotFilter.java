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
import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IFluidFilter;
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

    /** The fluid twin of {@link #getGateFilter} (7.1.x {@code ActionRobotFilter.getGateFluidFilter} +
     *  {@code ArrayFluidFilter}): the work-filter action's parameter stacks read as the fluids they carry —
     *  a bucket of water filters for water. No filter set passes every fluid; a filter with no
     *  fluid-carrying stack matches nothing (the carrier would never load), exactly as 7.1.x's array
     *  filter over fluidless stacks did. */
    public static IFluidFilter getGateFluidFilter(DockingStation station) {
        Collection<ItemStack> stacks = getGateFilterStacks(station);

        if (stacks.size() == 0) {
            return fluid -> fluid != null && !fluid.isEmpty();
        }
        Set<Fluid> fluids = new HashSet<>();
        for (ItemStack stack : stacks) {
            FluidStack contained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            if (!contained.isEmpty()) {
                fluids.add(contained.getFluid());
            }
        }
        return fluid -> fluid != null && !fluid.isEmpty() && fluids.contains(fluid.getFluid());
    }

    /** Whether any active {@code actionClass} action interacts with {@code filter} (7.1.x verbatim): the
     *  action's parameters are stacked into a filter, and an action with no filter set passes everything. */
    public static boolean canInteractWithItem(DockingStation station, IStackFilter filter, Class<?> actionClass) {
        for (StatementSlot s : station.getActiveActions()) {
            if (actionClass.isAssignableFrom(s.statement.getClass())) {
                StatementParameterStackFilter param = new StatementParameterStackFilter(s.parameters);
                if (!param.hasFilter() || param.matches(filter)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The fluid twin of {@link #canInteractWithItem}: an action with no filter set passes every fluid,
     *  otherwise one of its parameter stacks must contain a fluid matching {@code filter}. */
    public static boolean canInteractWithFluid(DockingStation station, IFluidFilter filter, Class<?> actionClass) {
        for (StatementSlot s : station.getActiveActions()) {
            if (actionClass.isAssignableFrom(s.statement.getClass())) {
                StatementParameterStackFilter param = new StatementParameterStackFilter(s.parameters);
                if (!param.hasFilter()) {
                    return true;
                }
                for (ItemStack stack : param.getStacks()) {
                    FluidStack fluid = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
                    if (!fluid.isEmpty() && filter.matches(fluid)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
