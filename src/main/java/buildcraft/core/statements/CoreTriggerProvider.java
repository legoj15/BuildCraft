/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;


import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.ITriggerInternalSided;
import buildcraft.api.statements.ITriggerProvider;
import buildcraft.api.statements.containers.IRedstoneStatementContainer;
import buildcraft.api.tiles.IHasWork;
import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.core.BCCoreStatements;
import buildcraft.platform.transfer.TransferAccess;

public enum CoreTriggerProvider implements ITriggerProvider {
    INSTANCE;

    @Override
    public void addInternalTriggers(Collection<ITriggerInternal> res, IStatementContainer container) {
        res.add(BCCoreStatements.TRIGGER_TRUE);
        if (container instanceof IRedstoneStatementContainer) {
            res.add(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE);
            res.add(BCCoreStatements.TRIGGER_REDSTONE_INACTIVE);
        }

        if (TriggerPower.isTriggeringTile(container.getTile())) {
            res.add(BCCoreStatements.TRIGGER_POWER_HIGH);
            res.add(BCCoreStatements.TRIGGER_POWER_LOW);
        }
    }

    @Override
    public void addInternalSidedTriggers(Collection<ITriggerInternalSided> res, IStatementContainer container,
        @Nonnull Direction side) {}

    @Override
    public void addExternalTriggers(Collection<ITriggerExternal> res, @Nonnull Direction side, Level level, BlockPos pos,
        @Nullable BlockEntity tile) {
        // Skip pipe holders — they have their own internal trigger system (TriggerProviderPipes).
        // In 1.12.2, pipe sections returned getTankProperties()=empty so they were naturally filtered
        // by the liquids.length > 0 check. In 26.1.1 ResourceHandler.size() returns 1 unconditionally,
        // so we must explicitly exclude pipes to avoid false-positive fluid/item triggers.
        if (tile instanceof IPipeHolder) {
            return;
        }

        if (tile != null && TriggerPower.isTriggeringTile(tile, side.getOpposite())) {
            res.add(BCCoreStatements.TRIGGER_POWER_HIGH);
            res.add(BCCoreStatements.TRIGGER_POWER_LOW);
        }

        boolean blockInventoryTriggers = false;
        boolean blockFluidHandlerTriggers = false;

        if (tile instanceof IBlockDefaultTriggers defaults) {
            blockInventoryTriggers = defaults.blockInventoryTriggers(side);
            blockFluidHandlerTriggers = defaults.blockFluidHandlerTriggers(side);
        }

        // The container triggers read a block capability, which a block without a block entity (a vanilla cauldron
        // or composter) can expose too.
        if (!blockInventoryTriggers) {
            var itemHandler = TransferAccess.findItem(level, pos, side.getOpposite());
            boolean hasInventory = itemHandler != null && itemHandler.size() > 0;
            if (hasInventory) {
                res.add(BCCoreStatements.TRIGGER_INVENTORY_EMPTY);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_SPACE);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_CONTAINS);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_FULL);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_BELOW_25);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_BELOW_50);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_BELOW_75);
            }
        }

        if (!blockFluidHandlerTriggers) {
            var fluidHandler = TransferAccess.findFluid(level, pos, side.getOpposite());
            boolean hasFluid = fluidHandler != null && fluidHandler.size() > 0;
            if (hasFluid) {
                res.add(BCCoreStatements.TRIGGER_FLUID_EMPTY);
                res.add(BCCoreStatements.TRIGGER_FLUID_SPACE);
                res.add(BCCoreStatements.TRIGGER_FLUID_CONTAINS);
                res.add(BCCoreStatements.TRIGGER_FLUID_FULL);
                res.add(BCCoreStatements.TRIGGER_FLUID_BELOW_25);
                res.add(BCCoreStatements.TRIGGER_FLUID_BELOW_50);
                res.add(BCCoreStatements.TRIGGER_FLUID_BELOW_75);
            }
        }

        if (tile instanceof IHasWork) {
            res.add(BCCoreStatements.TRIGGER_MACHINE_ACTIVE);
            res.add(BCCoreStatements.TRIGGER_MACHINE_INACTIVE);
        }

        if (TriggerEnginePowerStage.isTriggeringTile(tile)) {
            res.add(BCCoreStatements.TRIGGER_POWER_BLUE);
            res.add(BCCoreStatements.TRIGGER_POWER_GREEN);
            res.add(BCCoreStatements.TRIGGER_POWER_YELLOW);
            res.add(BCCoreStatements.TRIGGER_POWER_RED);
            res.add(BCCoreStatements.TRIGGER_POWER_OVERHEAT);
        }
    }
}
