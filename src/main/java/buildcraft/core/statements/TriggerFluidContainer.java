/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Locale;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerFluidContainer extends BCStatement implements ITriggerExternal {
    public State state;

    public TriggerFluidContainer(State state) {
        super(
            "buildcraft:fluid." + state.name().toLowerCase(Locale.ROOT),
            "buildcraft.fluid." + state.name().toLowerCase(Locale.ROOT)
        );
        this.state = state;
    }

    @Override
    public SpriteHolder getSprite() {
        return BCCoreSprites.TRIGGER_FLUID.get(state);
    }

    @Override
    public int maxParameters() {
        return state == State.CONTAINS || state == State.SPACE ? 1 : 0;
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.trigger.fluid." + state.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, IStatementContainer statementContainer, IStatementParameter[] parameters) {
        if (!(tile instanceof IFluidHandler handler)) {
            return false;
        }

        FluidStack searchedFluid = FluidStack.EMPTY;

        if (parameters != null && parameters.length >= 1 && parameters[0] != null && !parameters[0].getItemStack().isEmpty()) {
            searchedFluid = FluidUtil.getFluidContained(parameters[0].getItemStack()).orElse(FluidStack.EMPTY);
        }

        int tanks = handler.getTanks();
        if (tanks == 0) {
            return false;
        }

        switch (state) {
            case EMPTY: {
                for (int i = 0; i < tanks; i++) {
                    FluidStack fluid = handler.getFluidInTank(i);
                    if (!fluid.isEmpty() && fluid.getAmount() > 0) {
                        return false;
                    }
                }
                return true;
            }
            case CONTAINS: {
                for (int i = 0; i < tanks; i++) {
                    FluidStack fluid = handler.getFluidInTank(i);
                    if (!fluid.isEmpty() && fluid.getAmount() > 0
                        && (searchedFluid.isEmpty() || FluidStack.isSameFluidSameComponents(fluid, searchedFluid))) {
                        return true;
                    }
                }
                return false;
            }
            case SPACE: {
                if (searchedFluid.isEmpty()) {
                    for (int i = 0; i < tanks; i++) {
                        FluidStack fluid = handler.getFluidInTank(i);
                        int cap = handler.getTankCapacity(i);
                        if (fluid.isEmpty() || fluid.getAmount() < cap) {
                            return true;
                        }
                    }
                    return false;
                }
                FluidStack toFill = searchedFluid.copy();
                toFill.setAmount(1);
                return handler.fill(toFill, IFluidHandler.FluidAction.SIMULATE) > 0;
            }
            case FULL: {
                if (searchedFluid.isEmpty()) {
                    for (int i = 0; i < tanks; i++) {
                        FluidStack fluid = handler.getFluidInTank(i);
                        int cap = handler.getTankCapacity(i);
                        if (fluid.isEmpty() || fluid.getAmount() < cap) {
                            return false;
                        }
                    }
                    return true;
                }
                FluidStack toFill = searchedFluid.copy();
                toFill.setAmount(1);
                return handler.fill(toFill, IFluidHandler.FluidAction.SIMULATE) <= 0;
            }
        }
        return false;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_FLUID_ALL;
    }

    public enum State {
        EMPTY,
        CONTAINS,
        SPACE,
        FULL;

        public static final State[] VALUES = values();
    }
}
