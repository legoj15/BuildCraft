/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;


import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.TransferAccess;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.LocaleUtil;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerFluidContainer extends AbstractContainerStateTrigger {

    public TriggerFluidContainer(State state) {
        super(state,
            "buildcraft:fluid." + state.name().toLowerCase(Locale.ROOT),
            "buildcraft.fluid." + state.name().toLowerCase(Locale.ROOT)
        );
    }

    @Override
    public SpriteHolder getSprite() {
        return BCCoreSprites.TRIGGER_FLUID.get(state);
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.trigger.fluid." + state.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isTriggerActive(Level level, BlockPos pos, @Nullable BlockEntity tile, Direction side,
        IStatementContainer statementContainer, IStatementParameter[] parameters) {
        BCFluidStorage handler = TransferAccess.findFluid(level, pos, side != null ? side.getOpposite() : null);

        if (handler == null) {
            return false;
        }

        BCFluidStack searchedFluid = BCFluidStack.EMPTY;

        if (parameters != null && parameters.length >= 1 && parameters[0] != null && !parameters[0].getItemStack().isEmpty()) {
            net.minecraft.world.item.ItemStack stack = parameters[0].getItemStack();
            searchedFluid = FluidUtilBC.getFluidContained(stack);
        }

        int tanks = handler.size();
        if (tanks == 0) {
            return false;
        }

        switch (state) {
            case EMPTY: {
                for (int i = 0; i < tanks; i++) {
                    BCFluidStack fluid = handler.getStack(i);
                    if (!fluid.isEmpty() && handler.getAmount(i) > 0) {
                        return false;
                    }
                }
                return true;
            }
            case CONTAINS: {
                for (int i = 0; i < tanks; i++) {
                    BCFluidStack fluid = handler.getStack(i);
                    if (!fluid.isEmpty() && handler.getAmount(i) > 0
                        && (searchedFluid.isEmpty() || BCFluidStack.isSameFluidSameComponents(fluid, searchedFluid))) {
                        return true;
                    }
                }
                return false;
            }
            case SPACE: {
                if (searchedFluid.isEmpty()) {
                    for (int i = 0; i < tanks; i++) {
                        BCFluidStack fluid = handler.getStack(i);
                        long cap = handler.getCapacity(i, fluid);
                        if (fluid.isEmpty() || handler.getAmount(i) < cap) {
                            return true;
                        }
                    }
                    return false;
                }
                // Probe with an unbounded amount, never committed: a cauldron takes fluid only in whole steps, so a
                // 1 mB probe always failed and a filtered SPACE read false (and FULL true) on an empty one.
                try (BCTransaction tx = BCTransaction.openRoot()) {
                    for (int i = 0; i < tanks; i++) {
                        if (handler.insert(i, searchedFluid, Integer.MAX_VALUE, tx) > 0) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            case FULL: {
                if (searchedFluid.isEmpty()) {
                    for (int i = 0; i < tanks; i++) {
                        BCFluidStack fluid = handler.getStack(i);
                        long cap = handler.getCapacity(i, fluid);
                        if (fluid.isEmpty() || handler.getAmount(i) < cap) {
                            return false;
                        }
                    }
                    return true;
                }
                try (BCTransaction tx = BCTransaction.openRoot()) {
                    for (int i = 0; i < tanks; i++) {
                        if (handler.insert(i, searchedFluid, Integer.MAX_VALUE, tx) > 0) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_FLUID_ALL;
    }
}
